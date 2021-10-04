package com.android.buildconfig.delegate.sample.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.android.BuildConfigDelegate
import com.android.buildconfig.delegate.sample.databinding.BuildConfigItemBinding
import java.util.regex.Pattern

class BuildConfigListAdapter(items: MutableList<BuildConfigDelegate.ClassField> = ArrayList()) :
    MutableListAdapter<BuildConfigDelegate.ClassField, BuildConfigListAdapter.ViewHolder>(items) {
    companion object {
        private var BUILD_CONFIG_VALUE_PATTERN = Pattern.compile("`BuildConfig#(?<module>[\\w-]+)#(?<value>.+?)`")
    }

    override fun compareItem(
        checkContent: Boolean,
        first: BuildConfigDelegate.ClassField,
        second: BuildConfigDelegate.ClassField
    ): Boolean {
        return if (checkContent) first.name == second.name && first.flavor == second.flavor else first === second
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = BuildConfigItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val classField = get(position)
        holder.bind(classField, position)
    }

    class ViewHolder(private val binding: BuildConfigItemBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(classField: BuildConfigDelegate.ClassField, position: Int) {
            val matcher = BUILD_CONFIG_VALUE_PATTERN.matcher(classField.value)
            binding.textBuildConfigFiled.text = null
            binding.textBuildConfigFiled.append("Flavor:" + classField.flavor + "\n")
            if (matcher.find()) {
                val module = matcher.group(1)
                val value = matcher.group(2)
                binding.textBuildConfigFiled.append("Module:$module\n")
                binding.textBuildConfigFiled.append("Name:${classField.name}\n")
                binding.textBuildConfigFiled.append("Value:$value")
            }
        }
    }
}