package com.android.plugin.buildconfig.visitor;

import com.android.plugin.buildconfig.BuildConfigClassField;
import com.android.plugin.buildconfig.BuildConfigConstants;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * The String replaces method visitor is responsible for changing the constant string in the method.
 * This method visitor handler all kinds of methods:
 * 1. Static constructor method
 * 2. Constructor method
 * 3. Normal method.
 * There are three cases we need to handle
 * 1. The value is not a string
 * 2. The value is a string but does not match our pattern.
 * 3. The value is a string and match our pattern.
 * Handle the string value.
 * Our pattern is `BuildConfig#<module name>#value`
 * However, The string value might contains multiple fields. e.g., "Url1:`BuildConfig#<module name>#value1` Url2: `BuildConfig#<module name>#value2`"
 * So, we have to convert the constant string to a StringBuilder and connect all the strings.
 */
public class StringReplaceMethodVisitor extends MethodVisitor {
    private List<BuildConfigClassField> classFieldList;

    public StringReplaceMethodVisitor(List<BuildConfigClassField> classFieldList,
            MethodVisitor methodVisitor) {
        super(Opcodes.ASM5, methodVisitor);
        this.classFieldList = classFieldList;
    }

    @Override
    public void visitLdcInsn(Object value) {
        if (value == null || !(value instanceof String)) {
            super.visitLdcInsn(value);
            return;
        }
        String stringValue = (String) value;
        final Pattern pattern = BuildConfigConstants.BUILD_CONFIG_VALUE_PATTERN;
        Matcher matcher = pattern.matcher(stringValue);
        if (!matcher.find()) {
            super.visitLdcInsn(stringValue);
            return;
        }

        super.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
        super.visitInsn(Opcodes.DUP);
        super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

        int index = 0;
        matcher = pattern.matcher(stringValue);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            if (index != start) {
                String stringConstant = stringValue.substring(index, start);
                super.visitLdcInsn(stringConstant);
                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                        "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            }
            String module = matcher.group("module");
            CharSequence text = stringValue.subSequence(start, end);
            BuildConfigClassField classField = findClassField(text);
            if (null != classField) {
                super.visitLdcInsn(module);
                super.visitLdcInsn(classField.getName());
                super.visitLdcInsn(classField.getValue());
                System.out.println("\t" + classField.getValue());
                super.visitMethodInsn(Opcodes.INVOKESTATIC, BuildConfigConstants.DELEGATE_CLASS_DESC,
                        BuildConfigConstants.DELEGATE_METHOD_NAME,
                        BuildConfigConstants.DELEGATE_METHOD_DESCRIPTOR, false);
                super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                        "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
            }
            index = end;
        }
        if (index != stringValue.length()) {
            String stringConstant = stringValue.substring(index);
            super.visitLdcInsn(stringConstant);
            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                    "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
        }
        super.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;",
                false);
    }

    private BuildConfigClassField findClassField(CharSequence value) {
        if (null != classFieldList) {
            for (BuildConfigClassField classField : classFieldList) {
                String fieldValue = classField.getValue();
                if (null != fieldValue && 0 < fieldValue.trim().length() && value.equals(fieldValue)) {
                    return classField;
                }
            }
        }
        return null;
    }

}
