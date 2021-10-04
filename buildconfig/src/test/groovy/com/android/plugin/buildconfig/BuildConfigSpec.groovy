package com.android.plugin.buildconfig

import org.apache.commons.io.FileUtils
import org.gradle.testkit.runner.GradleRunner
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created on 2021/8/7.
 *
 * @author Jack Chen
 * @email bingo110@126.com
 */
class BuildConfigSpec extends Specification {
    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder(new File("build/tmp"))
    @Shared
    private def inputAssetsProvider = new TestAssetsProvider("TestApp")

    def "test build config"() {
        given:
        FileUtils.cleanDirectory(testProjectDir.root.parentFile)
        FileUtils.copyDirectory(inputAssetsProvider.functionalAssetsDir, testProjectDir.root)
        def tmpLocalProperties = new File(testProjectDir.root, "local.properties")
        tmpLocalProperties.append("sdk.dir=" + getAndroidSdkDir())

        def buildScript = new File(testProjectDir.root, "build.gradle")
        buildScript.text = buildScript.text.replaceAll("classpath", "//classpath")

        def appBuildScript = new File(testProjectDir.root, "app/build.gradle")
        appBuildScript.text = appBuildScript.text.replace("//id 'plugin-placeholder'", "id 'build-config-delegate'")
        expect:
        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments(':app:assembleAndroidDevDebug', "--stacktrace")
                .withDebug(true)
                .forwardOutput()
                .withPluginClasspath()
                .build()
        null != result
    }

    private def getAndroidSdkDir() {
        def localPropertiesFile = new File('../local.properties')
        if (localPropertiesFile.exists()) {
            Properties local = new Properties()
            local.load(new FileReader(localPropertiesFile))
            if (local.containsKey('sdk.dir')) {
                def property = local.getProperty("sdk.dir")
                if (null != property) {
                    File sdkDir = new File(property)
                    if (sdkDir.exists()) {
                        return property
                    }
                }
            }
        }
        return new NullPointerException("Can not found the initial android SDK configuration.")
    }
}
