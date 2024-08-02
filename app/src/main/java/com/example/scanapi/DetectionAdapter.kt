package com.example.scanapi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DetectionAdapter(
    private val detections: List<Detection>,
    private val listener: OnItemClickListener
) : RecyclerView.Adapter<DetectionAdapter.DetectionViewHolder>() {

    interface OnItemClickListener {
        fun onItemClick(detection: Detection)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DetectionViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_detection, parent, false)
        return DetectionViewHolder(view)
    }

    override fun onBindViewHolder(holder: DetectionViewHolder, position: Int) {
        val detection = detections[position]
        holder.detectionName.text = detection.className
        holder.itemView.setOnClickListener {
            listener.onItemClick(detection)
        }
    }

    override fun getItemCount() = detections.size

    class DetectionViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val detectionName: TextView = itemView.findViewById(R.id.detectionName)
    }
}
