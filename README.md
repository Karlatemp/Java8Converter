# Java8Converter

将 java 9+ 的字节码转成 java 8 的

# Use

```kotlin
buildscript {
    repositories {
        // maven("https://raw.githubusercontent.com/Karlatemp/karlatemp-repo/master/")
        maven("https://gitee.com/Karlatemp/Karlatemp-repo/raw/master/")
    }
    dependencies.classpath("io.github.karlatemp:Java8Converter:1.0.2")
}

io.github.karlatemp.java8converter.Java8Converter().apply(project)

tasks.named("java8converter", io.github.karlatemp.java8converter.ConverterTask::class.java).configure {
    dependsOn("shadowJar")
    setup {
        filter {
            name.startsWith("io/github/karlatemp")
        }
    }
}

```