package com.android.plugin.buildconfig

import com.android.plugin.buildconfig.task.BuildConfigDelegateGenerator
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class BuildConfigDelegateGeneratorSpec extends Specification {
    @Rule
    TemporaryFolder testProjectDir = new TemporaryFolder(new File("build/tmp"))

    def "test generate build config global"() {
        given:
        String packageName = "com.android.plugin.buildconfig"
        String className = "BuildConfigGlobal"
        BuildConfigDelegateGenerator buildConfigGlobalGenerator = new BuildConfigDelegateGenerator()
        def sourceFile = buildConfigGlobalGenerator.generateBuildConfig(packageName, "androidDev", className, testProjectDir.root)
        expect:
        null != sourceFile
    }


}
