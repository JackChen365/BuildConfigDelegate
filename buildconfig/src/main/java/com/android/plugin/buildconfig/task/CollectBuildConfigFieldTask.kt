package com.android.plugin.buildconfig.task

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.build.gradle.api.BaseVariant
import com.android.builder.model.ClassField
import com.android.plugin.buildconfig.BuildConfigConstants
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class CollectBuildConfigFieldTask : DefaultTask() {
    @get:Input
    abstract val variantFlavorNameProvider: Property<String>

    @get:Input
    abstract val buildConfigFolderProvider: Property<File>

    @TaskAction
    fun generateBuildConfigGlobal() {
        val buildConfigFieldFlavors = mutableMapOf<String, List<ClassField>>()
        if (project.plugins.hasPlugin(AppPlugin::class.java)) {
            val appExtension = project.extensions.findByType(AppExtension::class.java) ?: return
            val applicationVariants = appExtension.applicationVariants
            applicationVariants.forEach { applicationVariant ->
                val buildConfigFieldItems = getBuildConfigFieldItems(applicationVariant)
                if (null != buildConfigFieldItems) {
                    buildConfigFieldFlavors[applicationVariant.name] = buildConfigFieldItems
                }
            }
        } else if (project.plugins.hasPlugin(LibraryPlugin::class.java)) {
            val libraryExtension = project.extensions.findByType(LibraryExtension::class.java) ?: return
            libraryExtension.libraryVariants.all { libraryVariant ->
                val buildConfigFieldItems = getBuildConfigFieldItems(libraryVariant)
                if (null != buildConfigFieldItems) {
                    buildConfigFieldFlavors[libraryVariant.name] = buildConfigFieldItems
                }
            }
        }
        if (buildConfigFolderProvider.isPresent && variantFlavorNameProvider.isPresent) {
            val buildConfigFolder = buildConfigFolderProvider.get()
            println("Project:${project.name} variantName:${variantFlavorNameProvider.get()} BuildConfigDir:" + buildConfigFolder.absolutePath)
            if (buildConfigFieldFlavors.isNotEmpty()) {
                val clazz = Class.forName(BuildConfigConstants.CLASS_FIELD_IMPL)
                val gson =
                    GsonBuilder().registerTypeAdapter(clazz, object : TypeAdapter<ClassField>() {
                        override fun write(out: JsonWriter, value: ClassField) {
                            out.beginObject()
                            out.name("name")
                            out.value(value.name)
                            out.name("type")
                            out.value(value.type)
                            out.name("value")
                            out.value(value.value.trim('\"'))
                            out.endObject()
                        }

                        override fun read(`in`: JsonReader?): ClassField? {
                            return null
                        }
                    }).create()
                val jsonString = gson.toJson(buildConfigFieldFlavors)
                val file = File(buildConfigFolder, project.name + ".json")
                file.writeText(jsonString)
            }
        }
    }

    private fun getBuildConfigFieldItems(variant: BaseVariant): List<ClassField>? {
        if (variant.generateBuildConfigProvider.isPresent) {
            val generateBuildConfig = variant.generateBuildConfigProvider.get()
            if (generateBuildConfig.items.isPresent) {
                val buildConfigItems = generateBuildConfig.items.get()
                return buildConfigItems.filterIsInstance<ClassField>()
            }
        }
        return null
    }
}