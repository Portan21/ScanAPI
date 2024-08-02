import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

fun RecyclerView.setHeightBasedOnChildren() {
    val adapter = adapter ?: return
    val manager = layoutManager ?: return
    var totalHeight = 0

    for (i in 0 until adapter.itemCount) {
        val holder = adapter.createViewHolder(this, adapter.getItemViewType(i))
        adapter.onBindViewHolder(holder, i)
        holder.itemView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        totalHeight += holder.itemView.measuredHeight

        // Add item decorations (e.g., dividers)
        val layoutParams = holder.itemView.layoutParams as RecyclerView.LayoutParams
        totalHeight += layoutParams.topMargin + layoutParams.bottomMargin
    }

    val layoutParams = layoutParams
    layoutParams.height = totalHeight
    this.layoutParams = layoutParams
}

