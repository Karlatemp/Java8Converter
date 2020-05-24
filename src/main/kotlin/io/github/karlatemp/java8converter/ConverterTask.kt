/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/05/24 17:30:38
 *
 * Java8Converter/Java8Converter.main/ConverterTask.kt
 */

package io.github.karlatemp.java8converter

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.objectweb.asm.*
import org.objectweb.asm.tree.*
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.*
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.collections.HashMap
import kotlin.collections.HashSet

private typealias FileScanner = () -> Collection<File>
private typealias FileOutput = (File) -> File
private typealias EntryFilter = ZipEntry.() -> Boolean
private typealias FactoryNamer = File.() -> String

open class ConverterTask : DefaultTask() {
    val converter = Converter()

    init {
        group = "converter"
    }

    fun setup(invoke: Converter.() -> Unit) {
        invoke(converter)
    }

    @TaskAction
    fun action() {
        converter.invoke0()
    }
}

open class Converter {
    private var scanner: FileScanner? = null
    private var output: FileOutput = output@{
        val p = it.path
        val lastFileSplitter = p.lastIndexOf(File.pathSeparatorChar)
        val extIndex = p.lastIndexOf('.')
        if (extIndex == -1 || extIndex < lastFileSplitter) {
            return@output File("$p-converted.jar")
        } else {
            val path0 = p.substring(0, extIndex)
            val ext = p.substring(extIndex)
            return@output File("$path0-converted$ext")
        }
    }
    private var filter: EntryFilter = { true }
    private var namer: FactoryNamer = { "io/github/karlatemp/java8converter/${UUID.randomUUID()}" }

    fun filter(filter: EntryFilter): Converter {
        this.filter = filter
        return this
    }

    fun namer(namer: FactoryNamer): Converter {
        this.namer = namer
        return this
    }

    fun output(output: FileOutput): Converter {
        this.output = output
        return this
    }

    fun scanner(scanner: FileScanner): Converter {
        this.scanner = scanner
        return this
    }

    open fun invoke0() {
        val scanner = scanner ?: error("No file scanner set")
        val files = scanner()
        if (files.isEmpty()) {
            println("WARNING: No jar found.")
        } else {
            files.forEach { if (it.isFile) it.runConverter() }
        }
    }

    private fun File.runConverter() {
        ZipFile(this).use { it.runConverter(output(this)) }
    }

    private fun ZipFile.runConverter(output: File) {
        ZipOutputStream(RAFOutputStream(RandomAccessFile(output, "rw")))
                .use {
                    runConverter(it, output.namer())
                }
    }

    private fun ZipFile.runConverter(output: ZipOutputStream, stringFactoryPackage: String) {
        val nodes = HashMap<String, ClassNode>()

        class ExportedChecker : MethodVisitor(Opcodes.ASM7) {
            var nonExported = false
            override fun visitAnnotation(descriptor: String, visible: Boolean):
                    AnnotationVisitor? {
                if (descriptor == "Lcn/mcres/karlatemp/mxlib/annotations/UnExported;") {
                    nonExported = true
                    return null
                }
                return null
            }

            fun run(a: Collection<AnnotationNode>?, b: Collection<AnnotationNode>?) {
                run(a)
                run(b)
            }

            fun run(a: Collection<AnnotationNode>?) {
                if (a != null) {
                    for (node in a) {
                        if ("Lcn/mcres/karlatemp/mxlib/annotations/UnExported;" == node.desc) {
                            nonExported = true
                            break
                        }
                    }
                }
            }
        }

        class CMember(var name: String, var desc: String, var owner: String) {
            var isInterface = false
            var openName: String? = null
            var openSetName: String? = null
            var openGetName: String? = null
            var setExist = false
            var getExist = false
            var isStatic = false
            var wrappedConsDesc: String? = null
            var notExported = false
            lateinit var method: MethodNode
            lateinit var field: FieldNode
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (other == null || javaClass != other.javaClass) return false
                val cMember = other as CMember
                return name == cMember.name &&
                        desc == cMember.desc &&
                        owner == cMember.owner
            }

            override fun hashCode(): Int {
                return Objects.hash(name, desc, owner)
            }

        }

        class CAccess {
            var fields: Deque<CMember> = ConcurrentLinkedDeque()
            var methods: Deque<CMember> = ConcurrentLinkedDeque()
            fun find(name: String?, desc: String?, owner: String?, isField: Boolean): CMember? {
                val deque = if (isField) fields else methods
                for (x in deque) {
                    if (x.desc == desc && x.name == name && x.owner == owner) return x
                }
                return null
            }
        }

        val privates = HashMap<String, CAccess>()
        val fullNodes = HashMap<String, CAccess>()
        val access = HashMap<String, ClassNode>()
        val allFieldAccess = HashSet<CMember>()
        val allMethodAccess = HashSet<CMember>()

        entries().iterator().forEach { entry ->
            if (entry.isDirectory || !entry.name.endsWith(".class") || !filter(entry)) {
                output.putNextEntry(entry)
                if (!entry.isDirectory) {
                    getInputStream(entry).use { it.copyTo(output) }
                }
            } else {
                val node = getInputStream(entry).use { it.toNode() }
                        .also {
                            nodes[it.name] = it
                        }
                val ca = CAccess()
                privates[node.name] = ca
                val fa = CAccess()
                fullNodes[node.name] = fa
                node.methods?.forEach methodLoop@{ method ->
                    val checker = ExportedChecker()
                    checker.run(method.invisibleAnnotations, method.visibleAnnotations)
                    if (method.name == "<init>") {
                        method.access = method.access and Opcodes.ACC_PRIVATE.inv()
                        if (checker.nonExported) method.access = method.access or Opcodes.ACC_SYNTHETIC
                        return@methodLoop
                    }
                    val method0 = CMember(method.name, method.desc, node.name)
                    method0.isStatic = method.access and Opcodes.ACC_STATIC != 0
                    method0.notExported = checker.nonExported
                    method0.method = method
                    fa.methods.add(method0)
                    if (method.access and Opcodes.ACC_PRIVATE != 0) {
                        if (node.access and Opcodes.ACC_INTERFACE != 0) {
                            if (method.access and Opcodes.ACC_ABSTRACT != 0) {
                                method0.isInterface = true
                            }
                        }
                        ca.methods.add(method0)
                    }
                }
                node.fields?.forEach fieldLoop@{ field ->
                    val checker = ExportedChecker()
                    checker.run(field.invisibleAnnotations, field.visibleAnnotations)
                    val field0 = CMember(field.name, field.desc, node.name)
                    field0.field = field
                    field0.notExported = checker.nonExported
                    fa.fields.add(field0)
                    if (field.access and Opcodes.ACC_PRIVATE != 0) {
                        field0.isStatic = field.access and Opcodes.ACC_STATIC != 0
                        ca.fields.add(field0)
                    }
                }
            }
        }

        for (node in nodes.values) {
            access[node.name] = node
            if (node.methods != null) for (method in node.methods) {
                if (method.instructions != null) {
                    val instructions = method.instructions
                    for (i in 0 until instructions.size()) {
                        val instruction = instructions.get(i)
                        if (instruction is FieldInsnNode) {
                            if (instruction.owner == node.name) continue
                            val ownClass = privates[instruction.owner]
                            if (ownClass != null) {
                                val find = ownClass.find(instruction.name, instruction.desc, instruction.owner, true)
                                if (find != null) {
                                    allFieldAccess.add(find)
                                    when (instruction.getOpcode()) {
                                        Opcodes.PUTSTATIC, Opcodes.PUTFIELD -> {
                                            find.setExist = true
                                        }
                                        Opcodes.GETSTATIC, Opcodes.GETFIELD -> {
                                            find.getExist = true
                                        }
                                    }
                                }
                            }
                        } else if (instruction is MethodInsnNode) {
                            if (node.name == instruction.owner) continue
                            val ownClass = privates[instruction.owner]
                            if (ownClass != null) {
                                val find = ownClass.find(instruction.name, instruction.desc, instruction.owner, false)
                                if (find != null) {
                                    allMethodAccess.add(find)
                                }
                            }
                        }
                    }
                }
            }
        }

        kotlin.run {
            var index = 0
            for (field in allFieldAccess) {
                val node = nodes[field.owner] ?: continue
                if (field.notExported) {
                    field.field.access = field.field.access or Opcodes.ACC_SYNTHETIC
                    field.openName = "?" + UUID.randomUUID() + "?" + index++
                    field.field.name = field.openName
                }
                if (field.getExist) {
                    val acc = node.visitMethod(
                            Opcodes.ACC_SYNTHETIC or if (field.isStatic) Opcodes.ACC_STATIC else 0,
                            ("access\$get$" + index++).also {
                                field.openGetName = it
                            }, Type.getMethodDescriptor(
                            Type.getType(field.desc)
                    ), null, null
                    )
                    var x = 0
                    if (!field.isStatic) {
                        x = 1
                        acc.visitVarInsn(Opcodes.ALOAD, 0)
                    }
                    acc.visitFieldInsn(
                            if (field.isStatic) Opcodes.GETSTATIC else Opcodes.GETFIELD,
                            node.name, field.name, field.desc
                    )
                    acc.visitMaxs(WrappedClassImplements.putTypeInsn(Type.getType(field.desc), x, true, acc).also { x = it }, x)
                }
                if (field.setExist) {
                    val acc = node.visitMethod(
                            Opcodes.ACC_SYNTHETIC or if (field.isStatic) Opcodes.ACC_STATIC else 0,
                            ("access\$set$" + index++).also {
                                field.openSetName = it
                            }, Type.getMethodDescriptor(
                            Type.VOID_TYPE, Type.getType(field.desc)
                    ), null, null
                    )
                    var x = 0
                    if (!field.isStatic) {
                        x = 1
                        acc.visitVarInsn(Opcodes.ALOAD, 0)
                    }
                    WrappedClassImplements.putTypeInsn(
                            Type.getType(field.desc), x, false, acc
                    )
                    acc.visitFieldInsn(
                            if (field.isStatic) Opcodes.PUTSTATIC else Opcodes.PUTFIELD,
                            node.name, field.name, field.desc
                    )
                    acc.visitInsn(Opcodes.RETURN)
                    x += Type.getType(field.desc).size
                    acc.visitMaxs(x, x)
                }
            }
            for (method in allMethodAccess) {
                val node = nodes[method.owner] ?: continue
                if (method.notExported) {
                    var acc = method.method.access
                    acc = acc and Opcodes.ACC_PRIVATE.inv()
                    acc = acc or Opcodes.ACC_SYNTHETIC
                    method.method.access = acc
                    method.openName = "?" + index++ + "w" + UUID.randomUUID() + "$"
                    method.method.name = method.openName
                    continue
                }
                val mtt = node.visitMethod(Opcodes.ACC_SYNTHETIC or if (method.isStatic) Opcodes.ACC_STATIC else 0,
                        ("access\$invoke$" + index++).also {
                            method.openName = it
                        }, method.desc, null, null)
                var slot = 0
                if (!method.isStatic) {
                    mtt.visitVarInsn(Opcodes.ALOAD, slot++)
                }
                for (arg in Type.getArgumentTypes(method.desc)) {
                    slot = WrappedClassImplements.putTypeInsn(
                            arg, slot, false, mtt
                    )
                }
                mtt.visitMethodInsn(
                        if (method.isStatic) Opcodes.INVOKESTATIC else if (method.isInterface) Opcodes.INVOKEINTERFACE else Opcodes.INVOKEVIRTUAL,
                        method.owner, method.name, method.desc, method.isInterface)
                val rt = Type.getReturnType(method.desc)
                WrappedClassImplements.putTypeInsn(
                        rt, 0, true, mtt
                )
                mtt.visitMaxs(slot + rt.size, slot)
            }
            for (full in fullNodes.values) {
                for (field in full.fields) {
                    if (field.notExported) {
                        if (field.openName == null) {
                            field.field.access = field.field.access or Opcodes.ACC_SYNTHETIC
                            field.openName = "?" + UUID.randomUUID() + "?" + index++
                            field.field.name = field.openName
                        }
                    }
                }
                for (method in full.methods) {
                    if (method.notExported) {
                        if (method.openName == null) {
                            var acc = method.method.access
                            acc = acc and Opcodes.ACC_PRIVATE.inv()
                            acc = acc or Opcodes.ACC_SYNTHETIC
                            method.method.access = acc
                            method.openName = "?" + index++ + "x" + UUID.randomUUID() + "$"
                            method.method.name = method.openName
                        }
                    }
                }
            }
        }
        kotlin.run {
            for (node in nodes.values) {
                for (met in node.methods) {
                    val instructions = met.instructions
                    if (instructions != null) {
                        for (i in 0 until instructions.size()) {
                            val inst = instructions.get(i)
                            if (inst is MethodInsnNode) {
                                val self = inst.owner == node.name
                                val ownClass = fullNodes[inst.owner]
                                if (ownClass != null) {
                                    val find = ownClass.find(inst.name, inst.desc, inst.owner, false)
                                    if (find != null) {
                                        if (self && !find.notExported) continue
                                        if (find.openName != null) {
                                            println(node.name.toString() + "." + met.name + "#" + inst.name + "->" + find.openName)
                                            inst.name = find.openName
                                        }
                                    }
                                }
                            } else if (inst is FieldInsnNode) {
                                val self = inst.owner == node.name
                                val ownClass = fullNodes[inst.owner]
                                if (ownClass != null) {
                                    val find = ownClass.find(inst.name, inst.desc, inst.owner, true)
                                    if (find != null) {
                                        if (self && !find.notExported) continue
                                        if (self && find.openName != null) {
                                            inst.name = find.openName
                                            continue
                                        }
                                        val isGet = inst.getOpcode() == Opcodes.GETSTATIC || inst.getOpcode() == Opcodes.GETFIELD
                                        val name = (if (isGet) find.openGetName else find.openSetName)
                                                ?: continue
                                        instructions.set(inst, MethodInsnNode(
                                                if (find.isStatic) Opcodes.INVOKESTATIC else Opcodes.INVOKEVIRTUAL,
                                                inst.owner,
                                                name,
                                                if (isGet) "()" + inst.desc else "(" + inst.desc.toString() + ")V", false
                                        ))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        kotlin.run {
            val factoryName: String
            kotlin.run {
                // val package0 = "cn/mcres/karlatemp/mxlib/Java9ToJava8/" + UUID.randomUUID().toString().replace('-', '_')
                factoryName = "$stringFactoryPackage/StringFactory".replace('.', '/') // Will add mapping here.
            }
            for (node in nodes.values) {
                node.version = Opcodes.V1_8
                node.nestHostClass = null
                node.nestMembers = null
                node.module = null
                for (met in node.methods) {
                    val `is` = met.instructions
                    if (`is` != null) {
                        for (inst in `is`) {
                            if (inst is InvokeDynamicInsnNode) {
                                val inv = inst
                                if (inv.name == "makeConcatWithConstants" && inv.bsm.owner == "java/lang/invoke/StringConcatFactory") {
                                    val bsm = inv.bsm
                                    inv.bsm = Handle(
                                            bsm.tag,
                                            factoryName,
                                            bsm.name,
                                            bsm.desc,
                                            bsm.isInterface
                                    )
                                }
                            }
                            if (inst is MethodInsnNode) {
                                if (inst.owner == "java/lang/Thread") if (inst.desc == "()V") {
                                    if (inst.name == "onSpinWait") {
                                        inst.owner = factoryName
                                    }
                                }
                            }
                        }
                    }
                }
            }
            val factory = ClassNode()
            StringFactoryCreator.dump(factoryName, Opcodes.V1_8, factory)
            nodes.put(factory.name, factory)
        }
        kotlin.run {
            nodes.values.forEach { node ->
                output.putNextEntry(ZipEntry("${node.name}.class"))
                output.write(
                        ClassWriter(0).also { node.accept(it) }.toByteArray()
                )
            }
        }
    }
}

private fun InputStream.toNode(): ClassNode =
        ClassReader(this).let { reader ->
            ClassNode().also {
                reader.accept(it, 0)
            }
        }



