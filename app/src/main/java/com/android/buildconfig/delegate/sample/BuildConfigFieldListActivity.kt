package com.android.buildconfig.delegate.sample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.BuildConfigDelegate
import com.android.buildconfig.delegate.sample.adapter.BuildConfigListAdapter
import com.android.buildconfig.delegate.sample.databinding.ActivityBuildConfigFieldListBinding
import com.cz.android.sample.api.Exclude

@Exclude
class BuildConfigFieldListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityBuildConfigFieldListBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val flavor = intent.getStringExtra("flavor")
        if (null != flavor) {
            val flavorClassFields = BuildConfigDelegate.getFlavorClassFields()
            val flavorClassFieldList = flavorClassFields.values.flatten().filter { classField ->
                classField.flavor == flavor
            }.toMutableList()
            binding.recyclerView.layoutManager = LinearLayoutManager(this)
            binding.recyclerView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
            binding.recyclerView.adapter = BuildConfigListAdapter(flavorClassFieldList)
        }
    }
}