package com.thirfir.exoplayersample

import android.content.res.Resources
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowInsetsController.*
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import com.google.api.gax.core.FixedCredentialsProvider
import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.speech.v1.RecognitionAudio
import com.google.cloud.speech.v1.RecognitionConfig
import com.google.cloud.speech.v1.RecognizeResponse
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.SpeechSettings
import com.google.protobuf.ByteString
import com.thirfir.exoplayersample.databinding.ActivityVideoBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files


@OptIn(UnstableApi::class)
class VideoActivity : AppCompatActivity() {
    private val binding: ActivityVideoBinding by lazy(LazyThreadSafetyMode.NONE) {
        ActivityVideoBinding.inflate(layoutInflater)
    }
    private val player by lazy(LazyThreadSafetyMode.NONE) {
        ExoPlayer.Builder(this).build()
    }

    private val retriever by lazy(LazyThreadSafetyMode.NONE) {
        MediaMetadataRetriever().apply { setDataSource(this@VideoActivity, intent.data!!) }
    }

    private val speechSettings by lazy(LazyThreadSafetyMode.NONE) {
        createSpeechSettings()
    }

    private val subtitleQueue = mutableListOf<String>()
    private var currentSubtitleTimeMs = 0L
    private var subtitleJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        enableEdgeToEdge()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setBackgroundColor(0xFF000000.toInt())
            window.insetsController?.setSystemBarsAppearance(APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND, APPEARANCE_LIGHT_STATUS_BARS)
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initializePlayer()
        initializeFrames()
    }

    override fun onStart() {
        super.onStart()
        player.play()
    }

    private fun initializeFrames() {
        binding.recyclerViewTimeline.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        convertVideoToImages(intent.data?.getRealPathFromUri())
        binding.recyclerViewTimeline.addItemDecoration(object: ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                super.getItemOffsets(outRect, view, parent, state)
                val halfWidth = Resources.getSystem().displayMetrics.widthPixels / 2
                if (parent.getChildAdapterPosition(view) == 0) {
                    outRect.left = halfWidth
                } else if (parent.getChildAdapterPosition(view) == state.itemCount - 1) {
                    outRect.right = halfWidth
                }
            }
        })
    }

    private fun Uri.getRealPathFromUri(): String? {
        var filePath: String? = null
        val cursor = contentResolver.query(this, arrayOf(MediaStore.Video.Media.DATA), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val columnIndex = it.getColumnIndex(MediaStore.Video.Media.DATA)
                if (columnIndex != -1) {
                    filePath = it.getString(columnIndex)
                }
            }
        }
        return filePath
    }

    private fun convertVideoToImages(videoUrl: String?) {
        val outputDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "${videoUrl.hashCode()}")
        if (outputDir.exists()) {
            binding.recyclerViewTimeline.adapter = TimelineAdapter(outputDir.listFiles()?.map { it.absolutePath }?.sorted() ?: emptyList())
        } else {
            outputDir.mkdirs()
            val outputPattern = File(outputDir, "%%04d.jpg").absolutePath

            val ffmpegCommand = String.format("-i %s -vf fps=1/1 ${outputPattern}", videoUrl)
            FFmpegKit.executeAsync(ffmpegCommand) { session: FFmpegSession ->
                if (ReturnCode.isSuccess(session.returnCode)) {
                    val imagePaths =
                        outputDir.listFiles()?.map { it.absolutePath }?.sorted() ?: emptyList()
                    CoroutineScope(Dispatchers.Main).launch {
                        binding.recyclerViewTimeline.adapter = TimelineAdapter(imagePaths)
                    }
                } else if (ReturnCode.isCancel(session.returnCode)) {
                    // Canceled
                } else {
                    println("Conversion failed. ${session.failStackTrace} ${session.returnCode}")
                }
            }
        }
    }
    private fun initializePlayer() {
        binding.playerView.player = player

        val videoUri = intent.data
        val mediaItem = MediaItem.Builder()
            .setUri(videoUri)
            .build()

        player.setMediaItem(mediaItem)
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    lifecycleScope.launch {
                        subtitleQueue.clear()
                        delay(3000)
                        binding.playerView.subtitleView?.setCues(null)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying)
                    subtitleJob?.cancel()
                else {
                    showNextSubtitle()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                currentSubtitleTimeMs = newPosition.contentPositionMs
            }
        })

        player.prepare()
        player.play()
    }

    private fun createSpeechSettings(): SpeechSettings =
        SpeechSettings.newBuilder()
            .setCredentialsProvider(
                FixedCredentialsProvider.create(
                    GoogleCredentials.fromStream(
                        resources.openRawResource(R.raw.credential)
                    )
                )
            ).build()

    private val speechClient by lazy(LazyThreadSafetyMode.NONE) {
        SpeechClient.create(speechSettings)
    }
    private val recognitionConfig by lazy(LazyThreadSafetyMode.NONE) {
        RecognitionConfig.newBuilder()
            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
            .setLanguageCode(SUBTITLE_LANGUAGE_CODE)
            .setAudioChannelCount(2)
            .build()
    }

    private fun showNextSubtitle() {
        subtitleJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                val wavPath = externalCacheDir?.absolutePath + "/subtitle.wav"
                delay(maxOf(0, (currentSubtitleTimeMs - withContext(Dispatchers.Main) { player.currentPosition })))
                extractAudioSegment(
                    this@VideoActivity,
                    intent.data!!,
                    wavPath,
                    currentSubtitleTimeMs,
                    SUBTITLE_SEGMENT_DURATION_MS
                )

                val audioBytes = ByteString.copyFrom(Files.readAllBytes(File(wavPath).toPath()))
                val audio = RecognitionAudio.newBuilder()
                    .setContent(audioBytes)
                    .build()
                val response = speechClient.recognize(recognitionConfig, audio)

                withContext(Dispatchers.Main) {
                    showSubtitleOnUI(response)
                }
                currentSubtitleTimeMs += SUBTITLE_SEGMENT_DURATION_MS
            }
        }
    }

    private fun showSubtitleOnUI(response: RecognizeResponse) {
        for (result in response.resultsList) {
            val transcript = result.alternativesList[0].transcript
            subtitleQueue.addAll(transcript.chunked(30))
            while (subtitleQueue.size >= 3)
                subtitleQueue.removeAt(0)

            val cue = Cue.Builder()
                .setText(subtitleQueue.joinToString("\n"))
                .setLineAnchor(Cue.ANCHOR_TYPE_START)
                .setLine(.8f, Cue.LINE_TYPE_FRACTION)
                .build()

            binding.playerView.subtitleView?.setCues(listOf(cue))
            Log.d("STT ${currentSubtitleTimeMs / 1000}", transcript)
        }
    }

    override fun onStop() {
        super.onStop()
        player.stop()
    }

    override fun onDestroy() {
        super.onDestroy()
        player.release()
        retriever.release()
    }

    companion object {
        private const val SUBTITLE_LANGUAGE_CODE = "ko-KR"
        private const val SUBTITLE_SEGMENT_DURATION_MS = 3500L
        private val FPS = 1
    }
}