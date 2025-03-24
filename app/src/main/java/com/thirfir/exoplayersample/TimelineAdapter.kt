package com.thirfir.exoplayersample

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.thirfir.exoplayersample.databinding.ItemFrameBinding
import java.io.File

class TimelineAdapter(
    private val framePaths: List<String>
) : RecyclerView.Adapter<TimelineAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemFrameBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            ItemFrameBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        Glide.with(holder.binding.root.context)
            .load(File(framePaths[position]))
            .into(holder.binding.imageViewFrame)
    }

    override fun getItemCount() = framePaths.size
}