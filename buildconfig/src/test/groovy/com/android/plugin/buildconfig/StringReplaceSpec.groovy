package com.android.plugin.buildconfig

import com.android.plugin.buildconfig.visitor.StringReplaceClassInvitor
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import spock.lang.Shared
import spock.lang.Specification

class StringReplaceSpec extends Specification {
    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder(new File("build/tmp"))
    @Shared
    private def inputAssetsProvider = new TestAssetsProvider("Input")
    @Shared
    private def outputAssetsProvider = new TestAssetsProvider("Output")

    def "test replace string field testcase"(File file) {
        given:
        def classReader = new ClassReader(file.bytes)
        def classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS)
        def classVisitor = new StringReplaceClassInvitor("com/test", classWriter)
        classReader.accept(classVisitor, ClassReader.EXPAND_FRAMES)
        def outputFile = outputAssetsProvider.getAssetFile(file.name)
        outputFile.bytes = classWriter.toByteArray()
        expect:
        outputFile.exists()

        where:
        file                                                | _
        inputAssetsProvider.getAssetFile("TestCase1.class") | _
        inputAssetsProvider.getAssetFile("TestCase2.class") | _
        inputAssetsProvider.getAssetFile("TestCase3.class") | _
        inputAssetsProvider.getAssetFile("TestCase4.class") | _
        inputAssetsProvider.getAssetFile("TestCase5.class") | _
    }
}
