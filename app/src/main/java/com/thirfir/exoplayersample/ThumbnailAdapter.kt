package com.thirfir.exoplayersample

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.thirfir.exoplayersample.databinding.ItemThumbnailBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URL

class ThumbnailAdapter(
    private val videos: List<Video>,
    private val onItemClick: (Video) -> Unit
): RecyclerView.Adapter<ThumbnailAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemThumbnailBinding): RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemThumbnailBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = videos[position]
        with(holder.binding) {
            CoroutineScope(Dispatchers.Main).launch {
                imageView.setImageBitmap(getBitmapFrom(URL(video.thumbnail)))
            }
            imageView.contentDescription = video.title
            textViewTitle.text = video.title
            textViewDescription.text = video.description
            root.setOnClickListener {
                onItemClick(video)
            }
        }
    }

    private suspend fun getBitmapFrom(url: URL): Bitmap? {
        return withContext(Dispatchers.IO) {
            BitmapFactory.decodeStream(url.openConnection().getInputStream())
        }
    }

    override fun getItemCount() = videos.size
}