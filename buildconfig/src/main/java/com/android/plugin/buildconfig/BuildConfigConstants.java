package com.android.plugin.buildconfig;

import java.util.regex.Pattern;

public interface BuildConfigConstants {
    String CLASS_FIELD_IMPL="com.android.builder.internal.ClassFieldImpl";
    String TYPE_STRING = "String";
    String BUILD_CONFIG = "BuildConfig";
    String BUILD_CONFIG_VALUE_PATTERN_STRING = "`" + BUILD_CONFIG + "#(?<module>[\\w-]+)#(?<value>.+?)`";
    Pattern BUILD_CONFIG_VALUE_PATTERN = Pattern.compile(BUILD_CONFIG_VALUE_PATTERN_STRING);
    String CLINIT = "<clinit>";
    String INIT = "<init>";
    String STRING_DESCRIPTOR = "Ljava/lang/String;";
    String DELEGATE_PACKAGE_NAME = "com.android";
    String DELEGATE_CLASS_NAME = "BuildConfigDelegate";
    String DELEGATE_CLASS_DESC = DELEGATE_PACKAGE_NAME.replace('.', '/') + "/" + DELEGATE_CLASS_NAME;
    String DELEGATE_METHOD_NAME = "getString";
    String DELEGATE_METHOD_DESCRIPTOR = "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;";
}
