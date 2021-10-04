package com.android.plugin.buildconfig

import com.android.builder.model.ClassField

/**
 * BuildConfig class field.
 * This class similar to the class [ClassField]. However, We can change it and store the other information.
 */
class BuildConfigClassField(var module: String, var name: String, var type: String, var value: String)