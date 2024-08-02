package com.example.scanapi

import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ResultBottomSheetFragment(private val detections: List<Detection>) : BottomSheetDialogFragment() {

    private lateinit var detectionAdapter: DetectionAdapter
    private lateinit var onDetectionSelectedListener: OnDetectionSelectedListener

    interface OnDetectionSelectedListener {
        fun onDetectionSelected(detection: Detection)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnDetectionSelectedListener) {
            onDetectionSelectedListener = context
        } else {
            throw RuntimeException("$context must implement OnDetectionSelectedListener")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_detection_list, container, false)

        val detectionRecyclerView = view.findViewById<RecyclerView>(R.id.detectionRecyclerView)
        detectionRecyclerView.layoutManager = LinearLayoutManager(context)
        detectionAdapter = DetectionAdapter(detections, object : DetectionAdapter.OnItemClickListener {
            override fun onItemClick(detection: Detection) {
                onDetectionSelectedListener.onDetectionSelected(detection)
                dismiss()
            }
        })
        detectionRecyclerView.adapter = detectionAdapter

        return view
    }

    companion object {
        @JvmStatic
        fun show(fragmentManager: FragmentManager, detections: List<Detection>) {
            ResultBottomSheetFragment(detections).show(fragmentManager, "DetectionListBottomSheetFragment")
        }
    }
}
