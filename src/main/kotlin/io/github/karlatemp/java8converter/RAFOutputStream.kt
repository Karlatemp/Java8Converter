/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/05/24 17:30:38
 *
 * Java8Converter/Java8Converter.main/RAFOutputStream.kt
 */

package io.github.karlatemp.java8converter

import java.io.OutputStream
import java.io.RandomAccessFile

class RAFOutputStream(val raf: RandomAccessFile) : OutputStream() {
    override fun write(b: Int) {
        raf.write(b)
    }

    override fun write(b: ByteArray) {
        raf.write(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        raf.write(b, off, len)
    }

    override fun close() {
        raf.setLength(raf.filePointer)
        raf.close()
    }
}