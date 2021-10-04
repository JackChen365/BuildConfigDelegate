package com.android.plugin.buildconfig

import com.android.build.gradle.*
import com.android.build.gradle.api.AndroidSourceSet
import com.android.build.gradle.api.ApplicationVariant
import com.android.build.gradle.api.BaseVariant
import com.android.build.gradle.api.LibraryVariant
import com.android.build.gradle.internal.api.ApplicationVariantImpl
import com.android.builder.model.AndroidProject
import com.android.builder.model.ClassField
import com.android.plugin.buildconfig.task.BuildConfigDelegateGenerator
import com.android.plugin.buildconfig.task.CollectBuildConfigFieldTask
import com.android.plugin.buildconfig.transform.BuildConfigDelegateTransform
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

/**
 * The BuildConfigDelegatePlugin is responsible for creating all the BuildConfig related tasks.
 * Generate collect BuildConfig task for both app and library.
 * Generate BuildConfigDelegate task for app
 *
 * The plugin applied with `build-config-delegate'
 *
 * @see [BuildConfigDelegateGenerator] Generate the BuildConfigDelegate source file.
 * @see [CollectBuildConfigFieldTask] Collect all the BuildConfig fields.
 */
class BuildConfigDelegatePlugin : Plugin<Project> {
    override fun apply(target: Project) {
        if (!isAndroidAppProject(target) && !isAndroidLibraryProject(target)) return
        //Configure the buildConfig folder
        //When we disable the buildconfig. We need to generate the BuildConfigDelegate class in our own source folder
        val assetsBuildConfigFolder = configureAssetsBuildConfigFolder(target)
        //Create BuildConfigTask for each variant flavor.
        target.rootProject.allprojects { subProject ->
            subProject.afterEvaluate { project ->
                if (isAndroidAppProject(project)) {
                    //For android app
                    val appExtension = project.extensions.findByType(AppExtension::class.java)
                    appExtension?.applicationVariants?.forEach { applicationVariant ->
                        //Use application variant to configure the buildConfig file
                        wrapBuildConfigList(project, applicationVariant)
                        createCollectBuildConfigTask(project, applicationVariant, assetsBuildConfigFolder)
                        val buildConfigDeleteOutput = File(
                            project.buildDir.absolutePath + "/"
                                    + AndroidProject.FD_GENERATED
                                    + "/source/buildConfig/"
                                    + applicationVariant.flavorName + "/" + applicationVariant.buildType.name
                        )

                        appExtension.sourceSets.forEach { sourceSet ->
                            sourceSet.java.srcDir(buildConfigDeleteOutput)
                        }

                        createGenerateBuildConfigDelegateTask(
                            project,
                            BuildConfigConstants.DELEGATE_PACKAGE_NAME,
                            applicationVariant,
                            buildConfigDeleteOutput
                        )
                    }
                } else if (isAndroidLibraryProject(project)) {
                    //For android library
                    val libraryExtension = project.extensions.findByType(LibraryExtension::class.java)
                    libraryExtension?.libraryVariants?.all { libraryVariant ->
                        //Use library variant to configure the buildConfig file
                        wrapBuildConfigList(project, libraryVariant)
                        createCollectBuildConfigTask(project, libraryVariant, assetsBuildConfigFolder)
                    }
                }
            }
            val appExtension = subProject.extensions.findByType(AppExtension::class.java)
            appExtension?.registerTransform(BuildConfigDelegateTransform(target))
        }
    }

    /**
     * Add a custom folder to sourceSet.
     * We will put the class ConfigBuildDelete source file in this folder and compile it.
     */
    private fun configureAssetsBuildConfigFolder(target: Project): File {
        val buildConfigFolder = File(target.rootProject.buildDir, "tmp/buildconfig")
        if (!buildConfigFolder.exists()) {
            buildConfigFolder.mkdirs()
        }
        val appExtension = target.extensions.getByType(AppExtension::class.java)
        appExtension.sourceSets.forEach { sourceSet: AndroidSourceSet ->
            //This will include the folder without the folder itself.
            sourceSet.assets.srcDir(buildConfigFolder.parent)
        }
        return buildConfigFolder
    }

    /**
     * Create a collect BuildConfig task for each buildFlavor
     * The task will collect all the BuildConfig and convert it to Json.
     * Please refers to the [CollectBuildConfigFieldTask] for more information.
     */
    private fun createCollectBuildConfigTask(
        project: Project,
        variant: BaseVariant,
        assetsBuildConfigFolder: File
    ) {
        val taskName = "collect" + variant.name.capitalize() + "BuildConfig"
        val addBuildConfigTaskProvider =
            project.tasks.register(taskName, CollectBuildConfigFieldTask::class.java) { task ->
                task.group = "buildconfig"
                task.description = "Delegate all the build config fields"
                task.buildConfigFolderProvider.set(assetsBuildConfigFolder)
                task.variantFlavorNameProvider.set(variant.flavorName)
            }
        if (null != variant.generateBuildConfigProvider && variant.generateBuildConfigProvider.isPresent) {
            val generateBuildConfig = variant.generateBuildConfigProvider.get()
            val generateBuildConfigGlobalTask = addBuildConfigTaskProvider.get()
            if (addBuildConfigTaskProvider.isPresent) {
                //Run before the generateBuildConfig
                generateBuildConfig.dependsOn(generateBuildConfigGlobalTask)
            }
        }
    }

    /**
     * Wrap all the BuildConfig fields in [BaseVariant.getGenerateBuildConfigProvider]
     * Since the JVM will do some instruction optimizing work, e.g., constant String.
     * We never know if the constant string contains the BuildField. Therefore, We should add some metadata to the constant string.
     * For example: APPLICATION_ID="com.package.name" -> `BuildConfig#app#com.package.name`
     * Then, after we check the instruction: LDC and if the instruction matches the pattern we will know it is a BuildField.
     *
     * Tips:
     * Some build fields are shared in variants, so we have to check them before modifying them.
     */
    private fun wrapBuildConfigList(project: Project, variant: BaseVariant) {
        if (null != variant.generateBuildConfigProvider && variant.generateBuildConfigProvider.isPresent) {
            val generateBuildConfig = variant.generateBuildConfigProvider.get()
            if (generateBuildConfig.items.isPresent) {
                val buildConfigItems = generateBuildConfig.items.get()
                val clazz = Class.forName(BuildConfigConstants.CLASS_FIELD_IMPL)
                val valueField = clazz.getDeclaredField("value")
                if (!valueField.isAccessible) {
                    valueField.isAccessible = true
                }
                val buildConfigMaskRegex =
                    ("\"" + BuildConfigConstants.BUILD_CONFIG_VALUE_PATTERN_STRING + "\"").toRegex()
                buildConfigItems.forEach { item ->
                    if (item is ClassField && BuildConfigConstants.TYPE_STRING == item.type) {
                        //wrap the build config value: https://help.com -> `BuildConfig#app#https://help.com`
                        if (!buildConfigMaskRegex.matches(item.value)) {
                            valueField.set(
                                item,
                                "\"`" + "${BuildConfigConstants.BUILD_CONFIG}#${project.name}#" + item.value.trim('\"') + "`" + '\"'
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Create the generate BuildConfigDelegateTask for each flavor.
     * This function use [BuildConfigDelegateGenerator] to generate the task.
     * @param applicationId The package of the BuildConfigDelegate but here we use a fixed package name: `com.android` instead of the dynamic package name.
     * @param variant might be [ApplicationVariant] or [LibraryVariant]
     * @param buildConfigDeleteOutput The output folder
     */
    private fun createGenerateBuildConfigDelegateTask(
        project: Project,
        applicationId: String,
        variant: BaseVariant,
        buildConfigDeleteOutput: File
    ) {
        val taskName =
            "generate" + variant.name.capitalize() + BuildConfigConstants.DELEGATE_CLASS_NAME
        val generateBuildConfigDelegateTask = project.tasks.create(taskName) { task ->
            task.group = "buildconfig"
            task.description = "Generate the build config delegate."
        }
        //When the buildFeature.buildConfig = false. The generateBuildConfigProvider is null.
        //That's why we use our own compile folder.
        if (variant is ApplicationVariantImpl) {
            val variantScope = variant.variantData.scope
            if (variantScope.taskContainer.compileTask.isPresent) {
                val compileTask = variantScope.taskContainer.compileTask.get()
                compileTask.dependsOn(generateBuildConfigDelegateTask)
            }
            generateBuildConfigDelegateTask.doLast {
                val buildConfigGlobalGenerator = BuildConfigDelegateGenerator()
                buildConfigGlobalGenerator.generateBuildConfig(
                    applicationId,
                    variant.name,
                    BuildConfigConstants.DELEGATE_CLASS_NAME,
                    buildConfigDeleteOutput
                )
            }
        }
    }

    /**
     * Determine whether the project is an app.
     */
    private fun isAndroidAppProject(project: Project): Boolean {
        return null != project.plugins.findPlugin(AppPlugin::class.java)
    }

    /**
     * Determine whether the project is a library.
     */
    private fun isAndroidLibraryProject(project: Project): Boolean {
        return null != project.plugins.findPlugin(LibraryPlugin::class.java)
    }
}