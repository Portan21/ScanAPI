package com.example.scanapi

import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ResultBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_RESULT_TEXT = "resultText"
        private const val ARG_IMAGE_BITMAP = "imageBitmap"

        fun newInstance(resultText: String, imageBitmap: Bitmap): ResultBottomSheetFragment {
            val fragment = ResultBottomSheetFragment()
            val args = Bundle()
            args.putString(ARG_RESULT_TEXT, resultText)
            args.putParcelable(ARG_IMAGE_BITMAP, imageBitmap)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_result, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val resultTextView: TextView = view.findViewById(R.id.resultTextView)
        val resultImageView: ImageView = view.findViewById(R.id.resultImageView)
        val closeButton: Button = view.findViewById(R.id.closeButton)

        val resultText = arguments?.getString(ARG_RESULT_TEXT) ?: ""
        val imageBitmap = arguments?.getParcelable<Bitmap>(ARG_IMAGE_BITMAP)

        resultTextView.text = resultText
        resultImageView.setImageBitmap(imageBitmap)

        closeButton.setOnClickListener {
            dismiss()
        }
    }
}
