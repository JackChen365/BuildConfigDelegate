package com.android.buildconfig.delegate.sample.adapter

import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.widget.RecyclerView

class BuildFlavorItemDetailsLookup(private val recyclerView: RecyclerView) : ItemDetailsLookup<Long>() {
    override fun getItemDetails(event: MotionEvent): ItemDetails<Long>? {
        val view = findChildViewUnder(event.x, event.y)
        if (view != null) {
            val childViewHolder = recyclerView.getChildViewHolder(view) as? BuildFlavorAdapter.ViewHolder
            return childViewHolder?.getItemDetails()
        }
        return null
    }

    private fun findChildViewUnder(x: Float, y: Float): View? {
        val count: Int = recyclerView.childCount
        for (i in count - 1 downTo 0) {
            val child: View = recyclerView.getChildAt(i)
            val translationX = child.translationX
            val translationY = child.translationY
            if ((x >= child.left.toFloat() + translationX && x <= child.right.toFloat() + translationX && y >= child.top
                    .toFloat() + translationY && y <= child.bottom.toFloat() + translationY) || (y > child.bottom.toFloat() + translationY)
            ) {
                return child
            }
        }
        return null
    }
}