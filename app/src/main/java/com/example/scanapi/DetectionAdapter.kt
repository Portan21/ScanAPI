package com.example.scanapi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DetectionAdapter(private val detections: List<Detection>, private val onClick: (Detection) -> Unit) :
    RecyclerView.Adapter<DetectionAdapter.DetectionViewHolder>() {

    class DetectionViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val detectionName: TextView = view.findViewById(R.id.detectionName)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetectionViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_detection, parent, false)
        return DetectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: DetectionViewHolder, position: Int) {
        val detection = detections[position]
        holder.detectionName.text = detection.className
        holder.itemView.setOnClickListener { onClick(detection) }
    }

    override fun getItemCount(): Int = detections.size
}

