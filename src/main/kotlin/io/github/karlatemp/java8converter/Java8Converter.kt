/*
 * Copyright (c) 2018-2020 Karlatemp. All rights reserved.
 * @author Karlatemp <karlatemp@vip.qq.com> <https://github.com/Karlatemp>
 * @create 2020/05/24 17:30:38
 *
 * Java8Converter/Java8Converter.main/Java8Converter.kt
 */

package io.github.karlatemp.java8converter

import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File

class Java8Converter : Plugin<Project> {
    override fun apply(target: Project) {
        target.tasks.create("java8converter", ConverterTask::class.java)
                .converter.scanner {
                    File(target.buildDir, "libs").listFiles { file ->
                        file.isFile && file.extension == "jar"
                    }?.filter {
                        it.name.startsWith("${target.name}-${target.version}")
                                && !it.name.endsWith("-converted.jar")
                    } ?: listOf()
                }
    }
}