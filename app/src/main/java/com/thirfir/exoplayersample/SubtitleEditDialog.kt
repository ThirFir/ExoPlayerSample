package com.thirfir.exoplayersample

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.thirfir.exoplayersample.databinding.DialogSubtitleEditBinding

class SubtitleEditDialog(
    private val videoUrl: String,
    private val totalDuration: Long,
    private val segmentDuration: Long,
    private val onDismiss: () -> Unit
) : DialogFragment() {

    private var _binding: DialogSubtitleEditBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogSubtitleEditBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.adapter = SubtitleAdapter(videoUrl, totalDuration, segmentDuration)
        binding.btnComplete.setOnClickListener {
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        onDismiss()
    }
}