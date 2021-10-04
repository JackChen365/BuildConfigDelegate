package com.android.buildconfig.delegate.sample

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import androidx.recyclerview.selection.SelectionTracker
import androidx.recyclerview.selection.StableIdKeyProvider
import androidx.recyclerview.selection.StorageStrategy
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.android.BuildConfigDelegate
import com.android.buildconfig.delegate.sample.adapter.BuildFlavorAdapter
import com.android.buildconfig.delegate.sample.adapter.BuildFlavorItemDetailsLookup
import com.android.buildconfig.delegate.sample.databinding.ActivityBuildConfigTestBinding
import com.cz.android.sample.api.Register

@Register(title = "BuildConfigTest")
class BuildConfigTestActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityBuildConfigTestBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.textBuildConfig.text = "ServerUrl:${BuildConfig.SERVER_URL}\n" +
                "flavorName:${BuildConfig.FLAVOR_NAME}"

        val flavorList = BuildConfigDelegate.getFlavorSet().toMutableList()
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
        val buildFlavorAdapter = BuildFlavorAdapter(flavorList)
        binding.recyclerView.adapter = buildFlavorAdapter

        val selectionTracker = SelectionTracker.Builder(
            "BuildFlavorSelection",
            binding.recyclerView,
            StableIdKeyProvider(binding.recyclerView),
            BuildFlavorItemDetailsLookup(binding.recyclerView),
            StorageStrategy.createLongStorage()
        ).withSelectionPredicate(createSelectSingleAnything()).build()
        selectionTracker.addObserver(object : SelectionTracker.SelectionObserver<Long>() {
            override fun onItemStateChanged(key: Long, selected: Boolean) {
                super.onItemStateChanged(key, selected)
                if (selected) {
                    val flavor = buildFlavorAdapter[key.toInt()]
                    BuildConfigDelegate.setCurrentFlavor(flavor)
                    binding.textBuildConfig.text = "ServerUrl:${BuildConfig.SERVER_URL}\n" +
                            "FlavorName:${BuildConfig.FLAVOR_NAME}"
                }
            }
        })
        val currentFlavor = BuildConfigDelegate.getCurrentFlavor()
        val index = flavorList.indexOf(currentFlavor)
        selectionTracker.select(index.toLong())
        buildFlavorAdapter.setSelectionTracker(selectionTracker)


    }

    /**
     * Similar to the function:{@code SelectionPredicates.createSelectSingleAnything}
     * But we have changed the function: canSetStateForKey to return nextState in order to prevent we cancel the selections.
     */
    private fun <K> createSelectSingleAnything(): SelectionTracker.SelectionPredicate<K> {
        return object : SelectionTracker.SelectionPredicate<K>() {
            override fun canSetStateForKey(key: K, nextState: Boolean): Boolean {
                return nextState
            }

            override fun canSetStateAtPosition(position: Int, nextState: Boolean): Boolean {
                return true
            }

            override fun canSelectMultiple(): Boolean {
                return false
            }
        }
    }
}