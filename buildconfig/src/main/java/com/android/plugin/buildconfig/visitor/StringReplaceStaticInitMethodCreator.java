package com.android.plugin.buildconfig.visitor;

import com.android.plugin.buildconfig.BuildConfigClassField;
import com.android.plugin.buildconfig.BuildConfigConstants;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;

/**
 * The static constructor method creator.
 * Since there is no way to turn the constant string to StringBuilder directly.
 * We need always set the constant string to null, hold it and then initial the field in the static constructor method.
 * That's why we need this static constructor method creator.
 * This class makes sure we only initial the static fields once and convert the constant value to StringBuilder.
 */
public class StringReplaceStaticInitMethodCreator {
    private final List<FieldNode> stringFieldNodeList;
    private final List<BuildConfigClassField> classFieldList;
    private final ClassVisitor classVisitor;
    private boolean hasCreateStaticInitializationBlock;

    public StringReplaceStaticInitMethodCreator(
            List<BuildConfigClassField> classFieldList,
            List<FieldNode> stringFieldNodeList,
            final ClassVisitor classVisitor) {
        this.classVisitor = classVisitor;
        this.stringFieldNodeList = stringFieldNodeList;
        this.classFieldList = classFieldList;
    }

    public void foundStaticInitializationBlock() {
        hasCreateStaticInitializationBlock = true;
    }

    public boolean hasStaticInitializationBlock() {
        return hasCreateStaticInitializationBlock;
    }

    public void createStaticInitializationBlockIfNecessary() {
        if (hasStaticInitializationBlock())
            return;
        foundStaticInitializationBlock();
        MethodVisitor mv = classVisitor.visitMethod(Opcodes.ACC_STATIC, BuildConfigConstants.CLINIT, "()V", null, null);
        mv.visitCode();
        // Here init static final fields.
        for (FieldNode field : stringFieldNodeList) {
            if ((field.access & Opcodes.ACC_STATIC) != 0 && (field.access & Opcodes.ACC_FINAL) != 0) {
                String stringValue = (String) field.value;
                final Pattern pattern = BuildConfigConstants.BUILD_CONFIG_VALUE_PATTERN;
                Matcher matcher = pattern.matcher(stringValue);

                mv.visitTypeInsn(Opcodes.NEW, "java/lang/StringBuilder");
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V", false);

                int index = 0;
                while (matcher.find()) {
                    int start = matcher.start();
                    int end = matcher.end();
                    if (index != start) {
                        String stringConstant = stringValue.substring(index, start);
                        mv.visitLdcInsn(stringConstant);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                    }
                    CharSequence text = stringValue.subSequence(start, end);
                    BuildConfigClassField classField = findClassField(text);
                    if (null != classField) {
                        mv.visitLdcInsn(classField.getModule());
                        mv.visitLdcInsn(classField.getName());
                        mv.visitLdcInsn(classField.getValue());
                        System.out.println("\t" + classField.getValue());
                        mv.visitMethodInsn(Opcodes.INVOKESTATIC, BuildConfigConstants.DELEGATE_CLASS_DESC,
                                BuildConfigConstants.DELEGATE_METHOD_NAME,
                                BuildConfigConstants.DELEGATE_METHOD_DESCRIPTOR, false);
                        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                                "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                    }
                }
                if (index != stringValue.length()) {
                    String stringConstant = stringValue.substring(index);
                    mv.visitLdcInsn(stringConstant);
                    mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append",
                            "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
                }
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString",
                        "()Ljava/lang/String;",
                        false);
            }
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 0);
        mv.visitEnd();
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
