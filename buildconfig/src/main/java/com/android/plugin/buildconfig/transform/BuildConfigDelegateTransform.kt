package com.android.plugin.buildconfig.transform

import com.android.build.api.transform.*
import com.android.build.api.transform.QualifiedContent.DefaultContentType
import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.android.builder.model.ClassField
import com.google.common.collect.ImmutableSet
import com.android.plugin.buildconfig.BuildConfigClassField
import com.android.plugin.buildconfig.BuildConfigConstants
import com.android.plugin.buildconfig.visitor.StringReplaceClassInvitor
import org.apache.commons.io.FileUtils
import org.gradle.api.Project
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.util.function.Consumer
import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarOutputStream
import java.util.zip.ZipEntry

/**
 * A Transform that processes intermediary build artifacts to proxy BuildConfig field.
 *
 * The scope of the Transform: QualifiedContent.Scope.PROJECT, QualifiedContent.Scope.SUB_PROJECTS
 * We don't need to the external libraries.
 *
 * @see StringReplaceClassInvitor
 */
open class BuildConfigDelegateTransform(private val project: Project) : Transform() {

    override fun getName(): String {
        return BuildConfigConstants.DELEGATE_CLASS_NAME
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return ImmutableSet.of(DefaultContentType.CLASSES)
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return ImmutableSet.of(QualifiedContent.Scope.PROJECT, QualifiedContent.Scope.SUB_PROJECTS)
    }

    override fun isIncremental(): Boolean {
        return false
    }

    override fun transform(transformInvocation: TransformInvocation) {
        super.transform(transformInvocation)
        if (transformInvocation.isIncremental) {
            throw UnsupportedOperationException("Unsupported incremental build!")
        }
        //1. Collect all the BuildConfig fields
        val classFieldList = collectBuildConfigClassFields(transformInvocation)
        //2. Transform all the source file.
        transformClassFiles(transformInvocation, classFieldList)
        //3. Transform all the internal libraries.(Without external libraries)
        transformJarFiles(transformInvocation, classFieldList)
    }

    /**
     * Collect all the BuildConfig fields in all the modules.
     */
    private fun collectBuildConfigClassFields(transformInvocation: TransformInvocation): List<BuildConfigClassField> {
        val variantName = transformInvocation.context.variantName
        val classFieldSet = mutableSetOf<BuildConfigClassField>()
        project.rootProject.allprojects { subProject ->
            if (subProject.plugins.hasPlugin(AppPlugin::class.java)) {
                val appExtension = subProject.extensions.findByType(AppExtension::class.java)
                appExtension?.applicationVariants?.forEach { applicationVariant ->
                    if (variantName == applicationVariant.name && null != applicationVariant.generateBuildConfigProvider) {
                        val generateBuildConfig = applicationVariant.generateBuildConfigProvider.get()
                        if (generateBuildConfig.items.isPresent) {
                            val classFieldList =
                                generateBuildConfig.items.get().filterIsInstance<ClassField>().map { classField ->
                                    BuildConfigClassField(
                                        subProject.name,
                                        classField.name,
                                        classField.type,
                                        classField.value.trim('\"')
                                    )
                                }
                            classFieldSet.addAll(classFieldList)
                        }
                    }
                }
            } else if (subProject.plugins.hasPlugin(LibraryPlugin::class.java)) {
                val libraryExtension = subProject.extensions.findByType(LibraryExtension::class.java)
                libraryExtension?.libraryVariants?.all { libraryVariant ->
                    if (variantName == libraryVariant.name && null != libraryVariant.generateBuildConfigProvider) {
                        val generateBuildConfig = libraryVariant.generateBuildConfigProvider.get()
                        if (generateBuildConfig.items.isPresent) {
                            val classFieldList =
                                generateBuildConfig.items.get().filterIsInstance<ClassField>().map { classField ->
                                    BuildConfigClassField(
                                        subProject.name,
                                        classField.name,
                                        classField.type,
                                        classField.value.trim('\"')
                                    )
                                }
                            classFieldSet.addAll(classFieldList)
                        }
                    }
                }
            }
        }
        return classFieldSet.toList()
    }

    private fun transformJarFiles(
        transformInvocation: TransformInvocation,
        classFieldList: List<BuildConfigClassField>
    ) {
        val outputProvider = transformInvocation.outputProvider
        //Copy all the jar and classes to the where they need to...
        for (input in transformInvocation.inputs) {
            input.jarInputs.forEach { jarInput: JarInput ->
                val dest = outputProvider.getContentLocation(
                    jarInput.name,
                    jarInput.contentTypes,
                    jarInput.scopes,
                    Format.JAR
                )
                if (dest.exists()) {
                    dest.delete()
                }
                try {
                    processAndTransformJar(jarInput.file, classFieldList, dest)
                } catch (e: IOException) {
                    throw UncheckedIOException(e)
                }
            }
        }
    }

    private fun transformClassFiles(
        transformInvocation: TransformInvocation,
        classFieldList: List<BuildConfigClassField>
    ) {
        val outputProvider = transformInvocation.outputProvider
        for (input in transformInvocation.inputs) {
            input.directoryInputs.forEach(Consumer { dir: DirectoryInput ->
                if (dir.file.isDirectory) {
                    dir.file.walk().forEach { classFile ->
                        val fileName = classFile.name
                        if (fileName.endsWith(".class") &&
                            !fileName.startsWith("R$") &&
                            fileName != BuildConfigConstants.DELEGATE_CLASS_NAME + ".class" &&
                            "R.class" != fileName &&
                            "BuildConfig.class" != fileName
                        ) {
                            try {
                                val bytes = visitClass(classFile.readBytes(), classFieldList)
                                classFile.writeBytes(bytes)
                            } catch (e: Exception) {
                                System.err.println("Process file:${classFile.name} failed.")
                            }
                        }
                    }
                }
                try {
                    val destFolder = outputProvider.getContentLocation(
                        dir.name,
                        dir.contentTypes,
                        dir.scopes,
                        Format.DIRECTORY
                    )
                    FileUtils.copyDirectory(dir.file, destFolder)
                } catch (e: IOException) {
                    throw UncheckedIOException(e)
                }
            })
        }
    }


    @Throws(IOException::class)
    private fun processAndTransformJar(
        sourceFile: File,
        classFieldList: List<BuildConfigClassField>, destFile: File
    ) {
        if (null == sourceFile || !sourceFile.exists()) return
        val jarFile = JarFile(sourceFile)
        val newDestFile = File(destFile.parent, destFile.name.hashCode().toString() + destFile.name)
        val jarOutputStream = JarOutputStream(newDestFile.outputStream())

        val enumeration = jarFile.entries()
        while (enumeration.hasMoreElements()) {
            val jarEntry = enumeration.nextElement() as JarEntry
            val jarEntryName = jarEntry.name
            val inputStream = jarFile.getInputStream(jarEntry)
            jarOutputStream.putNextEntry(ZipEntry(jarEntry.name))
            var bytes = inputStream.readBytes()
            if (jarEntryName.endsWith(".class") &&
                !jarEntryName.contains("R$") &&
                !jarEntryName.endsWith("R.class") &&
                !jarEntryName.endsWith("BuildConfig.class")
            ) {
                bytes = visitClass(bytes, classFieldList)
            }
            jarOutputStream.write(bytes)
            jarOutputStream.closeEntry()
        }
        jarOutputStream.close()
        jarFile.close()
        //Delete the old file and rename the new file.
        destFile.delete()
        newDestFile.renameTo(destFile)
    }

    private fun visitClass(
        byteArray: ByteArray,
        classFieldList: List<BuildConfigClassField>
    ): ByteArray {
        val classReader = ClassReader(byteArray)
        val classWriter = ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
        val classVisitor = StringReplaceClassInvitor(classFieldList, classWriter)
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
        return classWriter.toByteArray()
    }
}