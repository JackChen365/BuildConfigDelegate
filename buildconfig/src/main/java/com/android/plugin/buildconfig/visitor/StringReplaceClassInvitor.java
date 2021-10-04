package com.android.plugin.buildconfig.visitor;

import com.android.plugin.buildconfig.BuildConfigClassField;
import com.android.plugin.buildconfig.BuildConfigConstants;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.FieldNode;

/**
 * The String replace class visitor.
 * We need to handle three cases:
 * 1. Constant string inside the static initial block: clinit
 * 2. Constant string inside the instance construction: init
 * 3. Constant string inside method
 */
public class StringReplaceClassInvitor extends ClassVisitor {
    private StringReplaceStaticInitMethodCreator staticInitMethodCreator;
    private List<FieldNode> fieldNodeList = new ArrayList<>();
    private List<BuildConfigClassField> classFieldList;

    public StringReplaceClassInvitor(List<BuildConfigClassField> classFieldList,
            ClassWriter cw) {
        super(Opcodes.ASM5, cw);
        this.classFieldList = classFieldList;
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        staticInitMethodCreator = new StringReplaceStaticInitMethodCreator(classFieldList, fieldNodeList, this);
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public FieldVisitor visitField(int access, String name, String desc, String signature, Object value) {
        Object fieldValue = value;
        if (BuildConfigConstants.STRING_DESCRIPTOR.equals(desc) && value != null) {
            fieldValue = null;
            fieldNodeList.add(new FieldNode(access, name, desc, signature, value));
        }
        return super.visitField(access, name, desc, signature, fieldValue);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
        StringReplaceMethodVisitor stringReplaceMethodVisitor = new StringReplaceMethodVisitor(classFieldList, mv);
        if (BuildConfigConstants.CLINIT.equals(name)) {
            if (!staticInitMethodCreator.hasStaticInitializationBlock()) {
                staticInitMethodCreator.foundStaticInitializationBlock();
                return stringReplaceMethodVisitor;
            }
            return mv;
        } else if (BuildConfigConstants.INIT.equals(name)) {
            return stringReplaceMethodVisitor;
        } else {
            return stringReplaceMethodVisitor;
        }
    }

    @Override public void visitEnd() {
        staticInitMethodCreator.createStaticInitializationBlockIfNecessary();
        super.visitEnd();
    }
}
