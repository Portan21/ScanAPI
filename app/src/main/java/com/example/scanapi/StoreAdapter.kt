package com.example.scanapi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class StoreAdapter(private val storeList: List<MainActivity.Store>?) :
    RecyclerView.Adapter<StoreAdapter.StoreViewHolder>() {

    class StoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val storeNameTextView: TextView = itemView.findViewById(R.id.storeNameTextView)
        val priceTextView: TextView = itemView.findViewById(R.id.priceTextView)
        val stockTextView: TextView = itemView.findViewById(R.id.stockTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StoreViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_store, parent, false)
        return StoreViewHolder(view)
    }

    override fun onBindViewHolder(holder: StoreViewHolder, position: Int) {
        val store = storeList?.get(position)
        holder.storeNameTextView.text = store?.branches
        holder.priceTextView.text = "Price: ${store?.price}"
        holder.stockTextView.text = "Stock: ${store?.stock}"
    }

    override fun getItemCount(): Int {
        return storeList?.size ?: 0
    }
}
