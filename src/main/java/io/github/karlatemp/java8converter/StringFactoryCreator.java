/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/05/24 18:11:32
 *
 * Java8Converter/Java8Converter.main/StringFactoryCreator.java
 */

package io.github.karlatemp.java8converter;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.util.HashMap;

public class StringFactoryCreator implements Opcodes {

    public static void dump(String className, int ver, ClassVisitor visitor) {
        try {
            ClassReader reader = new ClassReader(StringFactoryCreator.class.getResourceAsStream("J8StringFactory.class"));
            HashMap<String, String> mapping = new HashMap<>();
            reader.accept(new ClassVisitor(Opcodes.ASM7, new ClassRemapper(visitor, new SimpleRemapper(mapping))) {
                @Override
                public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                    mapping.put(name, className);
                    super.visit(ver, access, name, signature, superName, interfaces);
                }
            }, 0);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

}
