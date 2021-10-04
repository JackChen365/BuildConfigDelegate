package com.android.buildconfig.delegate.sample.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.selection.ItemDetailsLookup
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.widget.RecyclerView
import com.android.buildconfig.delegate.sample.databinding.BuildFlavorRadioItemBinding

class BuildFlavorAdapter(items: MutableList<String> = ArrayList()) :
    MutableListAdapter<String, BuildFlavorAdapter.ViewHolder>(items) {
    private lateinit var selectionTracker: SelectionTracker<Long>

    init {
        setHasStableIds(true)
    }

    fun setSelectionTracker(selectionTracker: SelectionTracker<Long>) {
        this.selectionTracker = selectionTracker
    }

    override fun compareItem(checkContent: Boolean, first: String, second: String): Boolean {
        return first == second
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = BuildFlavorRadioItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val text = get(position)
        holder.bind(text, selectionTracker.isSelected(position.toLong()))
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    class ViewHolder(private val binding: BuildFlavorRadioItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(text: String, isSelected: Boolean) {
            binding.text.text = text
            binding.radioButton.isChecked = isSelected
        }

        fun getItemDetails(): ItemDetailsLookup.ItemDetails<Long> =
            object : ItemDetailsLookup.ItemDetails<Long>() {
                override fun getPosition(): Int = adapterPosition
                override fun getSelectionKey(): Long? = itemId
                override fun inSelectionHotspot(e: MotionEvent): Boolean {
                    return true
                }
            }
    }
}