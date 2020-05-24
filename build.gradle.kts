import java.io.FileInputStream
import java.io.InputStreamReader
import java.util.*

buildscript {
    repositories {
        mavenLocal()
        mavenCentral()
    }
}

plugins {
    java
    kotlin("jvm") version "1.3.72"
    id("com.github.johnrengelman.shadow") version "5.2.0"
    `java-gradle-plugin`
    `maven-publish`
    id("com.jfrog.bintray") version "1.8.5"
}

group = "io.github.karlatemp"
version = "1.0.0-Alpha"

repositories {
    mavenLocal()
    mavenCentral()
}

configurations.named(JavaPlugin.API_CONFIGURATION_NAME) {
    val gradleApi = project.dependencies.gradleApi()
    dependencies.remove(gradleApi)
    dependencies { compileOnly(gradleApi) }
}

dependencies {
    // https://mvnrepository.com/artifact/org.ow2.asm/asm-tree
    //compile group: 'org.ow2.asm', name: 'asm-tree', version: '8.0.1'
    implementation("org.jetbrains:annotations:19.0.0")
    implementation("org.ow2.asm:asm-tree:8.0.1")
    implementation("org.ow2.asm:asm-commons:8.0.1")
    testImplementation("junit", "junit", "4.12")
}

configure<JavaPluginConvention> {
    sourceCompatibility = JavaVersion.VERSION_1_8
}


tasks.withType(com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar::class.java) {

    val pck = "io/github/karlatemp/java8converter"
    fun shadow(a: String) {
        val b = a.replace('.', '/')
        this.relocate("$b/", "$pck/shadowed/$b/")
    }
    shadow("org.intellij.lang.annotations")
    shadow("org.jetbrains.annotations")
    shadow("org.objectweb.asm")
}

@Suppress("ClassName", "NOTHING_TO_INLINE")
object settings {
    val settings0 = Properties()

    init {
        InputStreamReader(FileInputStream(File(projectDir, "local.properties")), Charsets.UTF_8).use {
            settings0.load(it)
        }
    }

    inline operator fun get(key: String): String = settings0.getProperty(key)
}


bintray {
    user = settings["bintray.user"]
    key = settings["bintray.apikey"]
    with(pkg) {
        name = "Java8Converter"
        repo = "Java8Converter"
        setLicenses("AGPL")
        val a = "https://github.com/Karlatemp/Java8Converter"
        vcsUrl = "$a.git"
        githubRepo = a
        issueTrackerUrl = "$a/issues"
        with(version) {
            name = project.version.toString()
        }
    }
}