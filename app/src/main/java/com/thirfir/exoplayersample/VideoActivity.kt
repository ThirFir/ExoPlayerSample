package com.thirfir.exoplayersample

import android.content.res.Resources
import android.graphics.Rect
import android.media.MediaMetadataRetriever
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import android.view.WindowInsetsController.APPEARANCE_TRANSPARENT_CAPTION_BAR_BACKGROUND
import androidx.activity.enableEdgeToEdge
import androidx.annotation.MainThread
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.google.cloud.speech.v1.SpeechClient
import com.google.cloud.speech.v1.SpeechSettings
import com.google.protobuf.ByteString
import com.thirfir.exoplayersample.databinding.ActivityVideoBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
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

    private val videoUrl by lazy(LazyThreadSafetyMode.NONE) {
        intent.data?.getRealPath(this)
    }

    private val retriever by lazy(LazyThreadSafetyMode.NONE) {
        MediaMetadataRetriever().apply { setDataSource(this@VideoActivity, intent.data!!) }
    }

    private val speechSettings by lazy(LazyThreadSafetyMode.NONE) {
        createSpeechSettings()
    }

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

    private var subtitleJob: Job? = null
    private val displayedSubtitleQueue = ArrayDeque<Pair<Int, String>>() // (time, subtitle)
    private var currentSubtitleTimeMs = 0L
    private var timelineScrollJob: Job? = null

    private var timelineTotalWidth = 0f
    val halfWidth by lazy {
        Resources.getSystem().displayMetrics.widthPixels / 2
    }

    private val subtitleLoadJobs = mutableListOf<Job>()

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
        binding.fab.setOnClickListener {
            val wasPlaying = player.isPlaying
            player.pause()
            SubtitleEditDialog(
                videoUrl!!,
                player.duration,
                SUBTITLE_SEGMENT_DURATION_MS,
                onDismiss = {
                    if (wasPlaying) {
                        player.play()
                    }
                }
            ).show(supportFragmentManager, "subtitle_edit_dialog")
        }
    }


    private fun initializeFrames() {
        binding.recyclerViewTimeline.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        convertVideoToImages()

        binding.recyclerViewTimeline.addItemDecoration(object: ItemDecoration() {
            override fun getItemOffsets(
                outRect: Rect,
                view: View,
                parent: RecyclerView,
                state: RecyclerView.State
            ) {
                super.getItemOffsets(outRect, view, parent, state)
                if (parent.getChildAdapterPosition(view) == 0) {
                    outRect.left = halfWidth
                } else if (parent.getChildAdapterPosition(view) == state.itemCount - 1) {
                    outRect.right = halfWidth
                }
            }
        })
        binding.recyclerViewTimeline.addOnScrollListener(object: RecyclerView.OnScrollListener() {
            private var xPos = halfWidth
            private var wasPlaying = false
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    if (timelineTotalWidth > 0) {
                        val t = xPos - halfWidth
                        val b = timelineTotalWidth
                        val per = t / b
                        player.seekTo((player.duration * per).toLong())
                        if (wasPlaying) {
                            player.play()
                        }
                    }
                } else if (newState == RecyclerView.SCROLL_STATE_DRAGGING) {
                    wasPlaying = player.isPlaying
                    player.pause()
                }
            }
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                xPos += dx
            }
        })
    }

    private fun convertVideoToImages() {
        val outputDir = File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "${videoUrl.hashCode()}")
        if (outputDir.exists()) {
            binding.recyclerViewTimeline.adapter = TimelineAdapter(outputDir.listFiles()?.map { it.absolutePath }?.sorted()?.also {
                timelineTotalWidth = 68f.toPx(this) * it.size
            } ?: emptyList())
        } else {
            outputDir.mkdirs()
            val outputPattern = File(outputDir, "%%04d.jpg").absolutePath

            val ffmpegCommand = String.format("-i %s -vf fps=1/1 $outputPattern", videoUrl)
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
                    log("Conversion failed. ${session.failStackTrace} ${session.returnCode}")
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
                // 한 번만 호출됨
                if (playbackState == Player.STATE_READY && subtitleLoadJobs.isEmpty()) {
                    lifecycleScope.launch {
                        val loadingDialog = LoadingDialog(this@VideoActivity)
                        loadingDialog.show()
                        loadAllSubtitles()
                        loadingDialog.dismiss()
                    }
                }
                if (playbackState == Player.STATE_ENDED) {
                    lifecycleScope.launch {
                        displayedSubtitleQueue.clear()
                        delay(3000)
                        binding.playerView.subtitleView?.setCues(null)
                    }
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) {
                    subtitleJob?.cancel()
                    timelineScrollJob?.cancel()
                }
                else {
                    createAndStartTimelineScrollJob()
                    if (subtitleLoadJobs.isNotEmpty())
                        createAndStartSubtitleJob()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                currentSubtitleTimeMs = newPosition.contentPositionMs.roundToNearestInterval(SUBTITLE_SEGMENT_DURATION_MS)
            }
        })

        player.prepare()
        player.play()
    }

    private fun createAndStartTimelineScrollJob() {
        timelineScrollJob = lifecycleScope.launch {
            while(isActive) {
                val per = player.currentPosition.toDouble() / player.duration
                val scrollOffsetX = -(timelineTotalWidth * per).toInt()
                (binding.recyclerViewTimeline.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(
                    0,
                    scrollOffsetX
                )
                delay(50)
            }
        }
    }

    private fun createAndStartSubtitleJob() {
        subtitleJob = CoroutineScope(Dispatchers.IO).launch {
            while(isActive) {
                delay((currentSubtitleTimeMs - withContext(Dispatchers.Main) { player.currentPosition })
                    .coerceAtLeast(0))
                val subtitleFile = getSubtitleFile(currentSubtitleTimeMs.roundToNearestInterval(SUBTITLE_SEGMENT_DURATION_MS))
                val transcript = try {
                    subtitleFile.readText()
                } catch (e: Exception) {
                    log("End of subtitle")
                    currentSubtitleTimeMs += SUBTITLE_SEGMENT_DURATION_MS
                    currentSubtitleTimeMs = currentSubtitleTimeMs.roundToNearestInterval(SUBTITLE_SEGMENT_DURATION_MS)
                    continue
                }

                withContext(Dispatchers.Main) {
                    showSubtitleOnUI(currentSubtitleTimeMs.toInt(), transcript)
                }
                currentSubtitleTimeMs += SUBTITLE_SEGMENT_DURATION_MS
                currentSubtitleTimeMs = currentSubtitleTimeMs.roundToNearestInterval(SUBTITLE_SEGMENT_DURATION_MS)
            }
        }
    }

    private fun getSubtitleFile(timeMs: Long): File {
        log("dddddddd $timeMs")
        val outputDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "${videoUrl.hashCode()}")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        return File(outputDir, "${timeMs}.txt")
    }

    private suspend fun loadAllSubtitles() {
        player.pause()
        for(time in 0..player.duration step SUBTITLE_SEGMENT_DURATION_MS) {
            subtitleLoadJobs.add(CoroutineScope(Dispatchers.IO).launch {
                val wavPath = externalCacheDir?.absolutePath + "/subtitle${time}.wav"
                extractAudioSegment(
                    this@VideoActivity,
                    intent.data!!,
                    wavPath,
                    time,
                    SUBTITLE_SEGMENT_DURATION_MS
                )
                val audioBytes = ByteString.copyFrom(Files.readAllBytes(File(wavPath).toPath()))
                val audio = RecognitionAudio.newBuilder()
                    .setContent(audioBytes)
                    .build()
                val response = speechClient.recognize(recognitionConfig, audio)

                for (result in response.resultsList) {
                    log("Load transcript $time   " + result.alternativesList[0].transcript)
                    saveSubtitle(time, result.alternativesList[0].transcript)
                }
            })
        }
        subtitleLoadJobs.joinAll()
        player.play()
    }

    private fun saveSubtitle(time: Long, subtitle: String) {
        val subtitleFile = getSubtitleFile(time)
        subtitleFile.writeText(subtitle)
    }

    @MainThread
    private fun showSubtitleOnUI(time: Int, transcript: String) {
        transcript.chunked(30).forEach { displayedSubtitleQueue.add(time to it) }
        while (displayedSubtitleQueue.size >= 3)
            displayedSubtitleQueue.removeAt(0)

        val cue = Cue.Builder()
            .setText(displayedSubtitleQueue.map { it.second }.joinToString("\n"))
            .setLineAnchor(Cue.ANCHOR_TYPE_START)
            .setLine(.8f, Cue.LINE_TYPE_FRACTION)
            .build()

        binding.playerView.subtitleView?.setCues(listOf(cue))
        log(transcript)
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

    override fun onStart() {
        super.onStart()
        player.play()
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

    private fun log(message: String) {
        Log.d(TAG, message)
    }

    companion object {
        private const val SUBTITLE_LANGUAGE_CODE = "ko-KR"
        private const val SUBTITLE_SEGMENT_DURATION_MS = 4000L
        private const val TAG = "VideoActivity"
    }
}