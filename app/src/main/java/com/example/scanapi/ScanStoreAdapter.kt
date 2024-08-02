package com.example.scanapi

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ScanStoreAdapter(private val storeList: List<ScanActivity.Store>?) :
    RecyclerView.Adapter<ScanStoreAdapter.StoreViewHolder>() {

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
        var stockQuantity = ""
        holder.storeNameTextView.text = store?.branches
        holder.priceTextView.text = "Price: ₱${store?.price}"
        if (store?.stock!! > 50){
            stockQuantity = "High Stock"
        }
        else if(store.stock == 0){
            stockQuantity = "Out Of Stock"
        }
        else{
            stockQuantity = "Low Stock"
        }
        holder.stockTextView.text = "$stockQuantity"
    }

    override fun getItemCount(): Int {
        return storeList?.size ?: 0
    }
}
