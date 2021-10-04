package com.android.plugin.buildconfig.task;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.lang.model.element.Modifier;

public class BuildConfigDelegateGenerator {
    private List<FieldSpec> getBuildConfigDelegateFields(String packageName, String flavorName, String className) {
        TypeName string = ClassName.get(String.class);
        TypeName booleanType = ClassName.get(Boolean.class);
        TypeName stringBooleanMap = ParameterizedTypeName.get(ClassName.get(Map.class), string, booleanType);
        TypeName stringBooleanHashMap = ParameterizedTypeName.get(ClassName.get(HashMap.class), string, booleanType);

        TypeName stringSetType = ParameterizedTypeName.get(ClassName.get(Set.class), string);
        TypeName stringTreeSetType = ParameterizedTypeName.get(ClassName.get(TreeSet.class), string);

        TypeName classFieldType = ClassName.bestGuess("ClassField");

        TypeName classFieldListClass = ParameterizedTypeName.get(ClassName.get(List.class), classFieldType);

        TypeName stringClassFieldListMap = ParameterizedTypeName
                .get(ClassName.get(Map.class), string, classFieldListClass);
        TypeName stringClassFieldListHashMap = ParameterizedTypeName
                .get(ClassName.get(HashMap.class), string, classFieldListClass);

        TypeName buildConfigDelegateFieldType = ClassName.bestGuess(packageName + "." + className);
        FieldSpec instanceFieldSpec = FieldSpec.builder(buildConfigDelegateFieldType, "buildConfigDelegate")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("new $T()", buildConfigDelegateFieldType).build();

        FieldSpec patternFieldSpec = FieldSpec.builder(Pattern.class, "BUILD_CONFIG_VALUE_PATTERN")
                .addModifiers(Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                .initializer("Pattern.compile(\"`BuildConfig#(?<module>[\\\\w-]+)#(?<value>.+?)`\")").build();

        FieldSpec flavorSetFieldSpec = FieldSpec.builder(stringSetType, "flavorSet")
                .addModifiers(Modifier.PUBLIC)
                .initializer("new $T()", stringTreeSetType).build();

        FieldSpec disabledClassFieldSpec = FieldSpec.builder(stringBooleanMap, "disabledClassField")
                .addModifiers(Modifier.PUBLIC)
                .initializer("new $T()", stringBooleanHashMap).build();

        FieldSpec flavorClassListFieldSpec = FieldSpec.builder(stringClassFieldListMap, "flavorClassFields")
                .addModifiers(Modifier.PUBLIC)
                .initializer("new $T()", stringClassFieldListHashMap).build();

        FieldSpec packageFlavorFieldSpec = FieldSpec.builder(string, "packageFlavor")
                .initializer("$S", flavorName)
                .addModifiers(Modifier.PRIVATE).build();

        FieldSpec currentFlavorFieldSpec = FieldSpec.builder(string, "currentFlavor")
                .initializer("$S", flavorName)
                .addModifiers(Modifier.PRIVATE).build();
        List<FieldSpec> fieldSpecList = new ArrayList<>();
        fieldSpecList.add(patternFieldSpec);
        fieldSpecList.add(instanceFieldSpec);
        fieldSpecList.add(flavorSetFieldSpec);
        fieldSpecList.add(disabledClassFieldSpec);
        fieldSpecList.add(flavorClassListFieldSpec);
        fieldSpecList.add(packageFlavorFieldSpec);
        fieldSpecList.add(currentFlavorFieldSpec);

        return fieldSpecList;
    }

    private TypeSpec getClassFieldTypeSpec() {
        MethodSpec constructor = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(String.class, "flavor")
                .addParameter(String.class, "name")
                .addParameter(String.class, "type")
                .addParameter(String.class, "value")
                .addStatement("this.$N = $N", "flavor", "flavor")
                .addStatement("this.$N = $N", "name", "name")
                .addStatement("this.$N = $N", "type", "type")
                .addStatement("this.$N = $N", "value", "value")
                .build();

        TypeSpec classTypeSpec = TypeSpec.classBuilder("ClassField")
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .addField(String.class, "flavor", Modifier.PUBLIC, Modifier.FINAL)
                .addField(String.class, "name", Modifier.PUBLIC, Modifier.FINAL)
                .addField(String.class, "type", Modifier.PUBLIC, Modifier.FINAL)
                .addField(String.class, "value", Modifier.PUBLIC, Modifier.FINAL)
                .addMethod(constructor)
                .build();
        return classTypeSpec;
    }

    private MethodSpec createInitialModuleBuildConfig() {
        TypeName ioExceptionType = ClassName.bestGuess("java.io.IOException");
        TypeName jsonExceptionType = ClassName.bestGuess("org.json.JSONException");

        TypeName contextType = ClassName.bestGuess("android.content.Context");
        TypeName assetManagerType = ClassName.bestGuess("android.content.res.AssetManager");
        TypeName jSONArrayType = ClassName.bestGuess("org.json.JSONArray");
        TypeName jSONObjectType = ClassName.bestGuess("org.json.JSONObject");
        TypeName inputStreamType = ClassName.bestGuess("java.io.InputStream");
        TypeName classFieldType = ClassName.bestGuess("ClassField");
        TypeName classFieldListClass = ParameterizedTypeName.get(ClassName.get(List.class), classFieldType);
        TypeName classFieldArrayListClass = ParameterizedTypeName.get(ClassName.get(ArrayList.class), classFieldType);
        return MethodSpec.methodBuilder("initialModuleBuildConfig")
                .addException(ioExceptionType)
                .addException(jsonExceptionType)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL, Modifier.STATIC)
                .addParameter(contextType, "context")
                .addStatement("final $T assets = context.getAssets()", assetManagerType)
                .addStatement("final $T[] buildConfigs = assets.list(\"buildconfig\")", String.class)
                .beginControlFlow("if (null != buildConfigs)")
                .beginControlFlow("for ($T fileName : buildConfigs)", String.class)
                .addStatement("final $T inputStream = assets.open(\"buildconfig\" + \"/\" + fileName)", inputStreamType)
                .addStatement("final int available = inputStream.available()")
                .addStatement("byte[] bytes = new byte[available]")
                .addStatement("inputStream.read(bytes)")
                .addStatement("$T jsonObject = new $T(new $T(bytes));", jSONObjectType, jSONObjectType, String.class)
                .addStatement("$T<String> iterator = jsonObject.keys()", Iterator.class)
                .beginControlFlow("while (iterator.hasNext())")
                .addStatement("String key = iterator.next()")
                .addStatement("buildConfigDelegate.flavorSet.add(key)")
                .addStatement("final int index = fileName.lastIndexOf(\".\")")
                .addStatement("final String moduleName = fileName.substring(0, index)")
                .addStatement("$T classFieldList = buildConfigDelegate.flavorClassFields.get(moduleName)",
                        classFieldListClass)
                .beginControlFlow("if (null == classFieldList)")
                .addStatement("classFieldList = new $T()", classFieldArrayListClass)
                .addStatement("buildConfigDelegate.flavorClassFields.put(moduleName, classFieldList)")
                .endControlFlow()
                .addStatement("$T objects = jsonObject.optJSONArray(key)", jSONArrayType)
                .beginControlFlow("for (int i = 0; i < objects.length(); i++)")
                .addCode("$T classFieldObject = objects.getJSONObject(i);\n" +
                        "String name = classFieldObject.optString(\"name\");\n" +
                        "String type = classFieldObject.optString(\"type\");\n" +
                        "String value = classFieldObject.optString(\"value\");\n" +
                        "if (\"String\".equals(type)) {\n" +
                        "   classFieldList.add(new ClassField(key, name, type, value));\n" +
                        "}\n", jSONObjectType)
                .endControlFlow()
                .endControlFlow()
                .endControlFlow()
                .endControlFlow().build();
    }

    private MethodSpec findClassField() {
        TypeName classFieldType = ClassName.bestGuess("ClassField");
        TypeName classFieldListClass = ParameterizedTypeName.get(ClassName.get(List.class), classFieldType);
        return MethodSpec.methodBuilder("findClassField")
                .addParameter(String.class, "key")
                .addParameter(String.class, "module")
                .addParameter(String.class, "flavor")
                .returns(classFieldType)
                .addModifiers(Modifier.FINAL, Modifier.STATIC, Modifier.PRIVATE)
                .addStatement("$T classFieldList = buildConfigDelegate.flavorClassFields.get(module)",
                        classFieldListClass)
                .beginControlFlow("if (null != classFieldList)")
                .beginControlFlow("for ($T classField : classFieldList)", classFieldType)
                .beginControlFlow(
                        "if (flavor.equals(classField.flavor) && key.equals(classField.name))")
                .addStatement("return classField")
                .endControlFlow()
                .endControlFlow()
                .endControlFlow()
                .addStatement("return null")
                .build();
    }

    private MethodSpec getFlavorClassFields() {
        TypeName string = ClassName.get(String.class);
        TypeName classFieldType = ClassName.bestGuess("ClassField");
        TypeName classFieldListClass = ParameterizedTypeName.get(ClassName.get(List.class), classFieldType);
        TypeName stringClassFieldListMap = ParameterizedTypeName
                .get(ClassName.get(Map.class), string, classFieldListClass);
        return MethodSpec.methodBuilder("getFlavorClassFields")
                .returns(stringClassFieldListMap)
                .addModifiers(Modifier.FINAL, Modifier.STATIC, Modifier.PUBLIC)
                .addStatement("return buildConfigDelegate.flavorClassFields").build();
    }

    private MethodSpec geCurrentFlavor() {
        return MethodSpec.methodBuilder("getCurrentFlavor")
                .returns(String.class)
                .addModifiers(Modifier.FINAL, Modifier.STATIC, Modifier.PUBLIC)
                .addStatement("return buildConfigDelegate.currentFlavor").build();
    }

    private MethodSpec getPackageFlavor() {
        return MethodSpec.methodBuilder("getPackageFlavor")
                .returns(String.class)
                .addModifiers(Modifier.FINAL, Modifier.STATIC, Modifier.PUBLIC)
                .addStatement("return buildConfigDelegate.packageFlavor").build();
    }

    private MethodSpec getFlavorSet() {
        TypeName stringSetType = ParameterizedTypeName.get(Set.class, String.class);
        return MethodSpec.methodBuilder("getFlavorSet")
                .returns(stringSetType)
                .addModifiers(Modifier.FINAL, Modifier.STATIC, Modifier.PUBLIC)
                .addStatement("return buildConfigDelegate.flavorSet").build();
    }

    private MethodSpec setCurrentFlavor() {
        return MethodSpec.methodBuilder("setCurrentFlavor")
                .addParameter(String.class, "flavor")
                .addModifiers(Modifier.FINAL, Modifier.STATIC, Modifier.PUBLIC)
                .addStatement("buildConfigDelegate.currentFlavor = flavor").build();
    }

    private MethodSpec getString() {
        TypeName classFieldType = ClassName.bestGuess("ClassField");
        return MethodSpec.methodBuilder("getString")
                .addParameter(String.class, "module")
                .addParameter(String.class, "key")
                .addParameter(String.class, "defaultValue")
                .returns(String.class)
                .addModifiers(Modifier.FINAL, Modifier.STATIC, Modifier.PUBLIC)
                .addStatement("final $T classField = findClassField(key, module, buildConfigDelegate.currentFlavor)",
                        classFieldType)
                .beginControlFlow("if (null != classField)")
                .addStatement("final $T matcher = BUILD_CONFIG_VALUE_PATTERN.matcher(classField.value)", Matcher.class)
                .beginControlFlow("if (matcher.find())")
                .addStatement("String value = matcher.group(2)")
                .addStatement("$T.i(\"BuildConfigDelegate\", \"Key:\" + key + \" value:\" + value)",
                        ClassName.bestGuess("android.util.Log"))
                .addStatement("return value")
                .endControlFlow()
                .endControlFlow()
                .addStatement("$T.i(\"BuildConfigDelegate\", \"Key:\" + key + \" defaultValue:\" + defaultValue)",
                        ClassName.bestGuess("android.util.Log"))
                .addStatement("return defaultValue").build();

    }

    public File generateBuildConfig(String packageName, String flavorName, String className, File outputDir)
            throws IOException {
        TypeSpec classFieldTypeSpec = getClassFieldTypeSpec();
        TypeSpec buildConfigDelegateTypeSpec = TypeSpec.classBuilder(className)
                .addJavadoc(CodeBlock.of("Automatically generated file. DO NOT MODIFY"))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addFields(getBuildConfigDelegateFields(packageName, flavorName, className))
                .addType(classFieldTypeSpec)
                .addMethod(createInitialModuleBuildConfig())
                .addMethod(getFlavorClassFields())
                .addMethod(geCurrentFlavor())
                .addMethod(getPackageFlavor())
                .addMethod(getFlavorSet())
                .addMethod(setCurrentFlavor())
                .addMethod(getString())
                .addMethod(findClassField())
                .build();
        JavaFile javaFile = JavaFile.builder(packageName, buildConfigDelegateTypeSpec).build();
        File sourceFile = new File(outputDir, className + ".java");
        javaFile.writeTo(outputDir);
        return sourceFile;
    }
}
