package com.thirfir.exoplayersample

import android.content.Context
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.recyclerview.widget.RecyclerView
import com.thirfir.exoplayersample.databinding.ItemSubtitleBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SubtitleAdapter(
    private val videoUrl: String,
    private val totalDuration: Long,
    private val segmentDuration: Long,
): RecyclerView.Adapter<SubtitleAdapter.ViewHolder>() {

    class ViewHolder(val binding: ItemSubtitleBinding): RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemSubtitleBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder.binding) {
            CoroutineScope(Dispatchers.IO).launch {
                val subtitleFile =
                    root.context.getSubtitleFile(position * segmentDuration) ?: return@launch
                val subtitle = subtitleFile.readText()
                withContext(Dispatchers.Main) {
                    textViewTime.text = formatDuration(position * segmentDuration)
                    textViewSubtitle.setText(subtitle)
                }
            }

            btnEdit.setOnClickListener {
                btnEdit.visibility = View.GONE
                btnSave.visibility = View.VISIBLE
                textViewSubtitle.isFocusable = true
                textViewSubtitle.isFocusableInTouchMode = true
                textViewSubtitle.requestFocus()

                val imm = root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(textViewSubtitle, InputMethodManager.SHOW_IMPLICIT)
            }
            btnSave.setOnClickListener {
                btnSave.visibility = View.GONE
                btnEdit.visibility = View.VISIBLE
                textViewSubtitle.isFocusable = false
                textViewSubtitle.isFocusableInTouchMode = false
                root.context.getSubtitleFile(position * segmentDuration)?.saveEditedSubtitle(textViewSubtitle.text.toString())

                val imm = root.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(textViewSubtitle.windowToken, 0)
            }
        }
    }

    private fun File.saveEditedSubtitle(subtitle: String) {
        writeText(subtitle)
    }

    private fun Context.getSubtitleFile(timeMs: Long): File? {
        val outputDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "${videoUrl.hashCode()}")
        if (!outputDir.exists()) {
            outputDir.mkdirs()
        }
        val file = File(outputDir, "${timeMs}.txt")
        if (!file.exists()) {
            return null
        }
        return File(outputDir, "${timeMs}.txt")
    }

    private fun formatDuration(durationMs: Long): String {
        val durationSeconds = durationMs / 1000

        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60

        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
    override fun getItemCount() = totalDuration.div(segmentDuration).toInt()
}