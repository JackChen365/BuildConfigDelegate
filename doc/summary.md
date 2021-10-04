## Summary

* Publish plugin and auto apply the plugin.

```
1. Copy the jar to libs
project.plugins.withId("java-gradle-plugin") {
    task publishPlugin(type: Copy, group: "plugin development") {
        from("build/libs") {
            include "*.jar"
        }
        into rootDir.absolutePath + "/libs"
        dependsOn "jar"
    }
    def testTask = project.tasks.findByName("test")
    testTask.dependsOn "publishPlugin"
}

2. Apply all the plugins in libs foler
dependencies {
    classpath fileTree(dir: 'libs', include: ['*.jar'])
    classpath deps.android_gradle_plugin
    classpath deps.kotlin.plugin
}

3. App gradle plugin for all the apps

project.plugins.withId("com.android.application") {
    fileTree(dir: "libs", include: "*.jar").each { jarFile ->
        zipTree(jarFile).each { file ->
            if (file.name.endsWith("properties")) {
                Properties props = new Properties()
                if (file.canRead()) {
                    props.load(new FileInputStream(file))
                    if (props != null && props.containsKey('implementation-class')) {
                        //This is a plugin config file. The name without surfix is the plugin id.
                        String pluginId = file.name.replace(".properties", "")
                        //Apply the plugin for this project.
                        println("apply plugin:$pluginId from plugin libs.")
                        project.plugins.apply(pluginId)
                    }
                }
            }
        }
    }
}

```

* When we disable the buildconfig

```
buildFeatures{
    buildconfig false
}
```

The `variant.generateBuildConfigProvider` will be null.

* Add metadata to the BuildField.

```
val valueField = ClassFieldImpl.class.getDeclaredField("value")
if (!valueField.isAccessible) {
    valueField.isAccessible = true
}
valueField.set(item,"\"`" + "${BuildConfigConstants.BUILD_CONFIG}#${project.name}#" + item.value.trim('\"') + "`" + '\"')
//value -> `BuildConfig#app#value`
```

Therefore, after the JVM compiles the class, we know that the constant string contains BuildField.

* Change String constant.

For string: "Var1:"+BuildConfig.VAR1+" Var2:"+BuildConfig.VAR2+"."; We need to change bunch of the instructions in the
method: visitLdcInsn(Object value)

```
public void visitLdcInsn(Object value) {
    if (value == null || !(value instanceof String)) {
        //Return the string.
        super.visitLdcInsn(value);
        return;
    }
    String stringValue = (String) value;
    final Pattern pattern = BuildConfigConstants.BUILD_CONFIG_VALUE_PATTERN;
    Matcher matcher = pattern.matcher(stringValue);
    if (!matcher.find()) {
        //Does not match the pattern: `BuildConfig#<ModuleName>#<Value>`
        super.visitLdcInsn(stringValue);
        return;
    }
    //Those instructions are equal to create a class instance: StringBuilder;
    //Create a StringBuilder() 
    super.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
    super.visitInsn(Opcodes.DUP);
    super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);
    
    //Put three parameters for the funciton: BuildConfig.getString(module,key,value)
    super.visitLdcInsn(module);
    super.visitLdcInsn(classField.getName());
    super.visitLdcInsn(classField.getValue());
    System.out.println("\t" + classField.getValue());
    super.visitMethodInsn(Opcodes.INVOKESTATIC, BuildConfigConstants.DELEGATE_CLASS_DESC,
            BuildConfigConstants.DELEGATE_METHOD_NAME,
            BuildConfigConstants.DELEGATE_METHOD_DESCRIPTOR, false);
    //StringBuilder.append(BuildConfig.getString(module,key,value))
    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
    
    //new StringBuilder().toString();
    super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;",false);
}
```


