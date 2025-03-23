package com.thirfir.exoplayersample

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.thirfir.exoplayersample.databinding.ItemFrameBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TimelineAdapter(
    private val uri: Uri,
    private val context: Context,
    private val retriever: MediaMetadataRetriever,
): RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {

    private val totalFrames = getTotalFrames()
    private val jobs = hashMapOf<Int, Job>()

    class ViewHolder(val binding: ItemFrameBinding): RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemFrameBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        jobs[holder.hashCode()]?.cancel()
        val frameTimeUs = (position * (1_000_000L / FPS))


        jobs[holder.hashCode()] = CoroutineScope(Dispatchers.IO).launch {
            val frameBitmap = retriever.getFrameAtTime(frameTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            withContext(Dispatchers.Main) {
                Glide.with(holder.binding.root.context)
                    .load(frameBitmap)
                    .into(holder.binding.imageViewFrame)
            }
        }
    }

    private fun getTotalFrames(): Int {
        retriever.setDataSource(context, uri)
        val totalFrames = (retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0) * FPS / 1_000

        return totalFrames.toInt()
    }

    override fun getItemCount() = totalFrames

    companion object {
        private const val FPS = 1
    }
}