package com.example.scanapi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ResultBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_RESULT_TEXT = "resultText"

        fun newInstance(resultText: String): ResultBottomSheetFragment {
            val fragment = ResultBottomSheetFragment()
            val args = Bundle()
            args.putString(ARG_RESULT_TEXT, resultText)
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
        val closeButton: Button = view.findViewById(R.id.closeButton)

        val resultText = arguments?.getString(ARG_RESULT_TEXT) ?: ""
        resultTextView.text = resultText

        closeButton.setOnClickListener {
            dismiss()
        }
    }
}
