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
    id("com.gradle.plugin-publish") version "0.11.0"
    // `maven-publish`
    // id("com.jfrog.bintray") version "1.8.5"
}

group = "io.github.karlatemp"
version = "1.0.1"

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
    classifier = ""
}

tasks.named("jar").get().enabled = false
tasks.named("publishPlugins").get().dependsOn("shadowJar")

/*
val settings0 = Properties().also { set ->
    InputStreamReader(FileInputStream(File(projectDir, "local.properties")), Charsets.UTF_8).use {
        set.load(it)
    }
}


bintray {
    user = settings0.getProperty("bintray.user")
    key = settings0.getProperty("bintray.apikey")
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
tasks.withType(com.jfrog.bintray.gradle.tasks.BintrayUploadTask::class.java) {
    dependsOn("shadowJar")
    dependsOn("generateMetadataFileForPluginMavenPublication")
    dependsOn("generatePomFileForPluginMavenPublication")
}
*/

// apply(from = project.file("gradle/pom.gradle.kts"))

//tasks.removeIf { it.name == "jar" }
//tasks.register("jar").configure { dependsOn("ShadowJar") }

/*
kotlin.run {
    val awa: (Dependency) -> Boolean = { false }
    @Suppress("UNCHECKED_CAST")
    tasks.named("publishToMavenLocal").configure {
        dependsOn("shadowJar")
        val envs = (this as java.util.function.Supplier<Any>).get() as MutableMap<String, Any>
        envs["filter"] = awa
        envs["callback"] = Consumer<String> {
            println("C: $it")
        }
        envs["output"] = Supplier<File> {
            File(project.buildDir,
                    "libs/${project.name}-${project.version}-all.jar"
            )
        }
    }
}
*/

gradlePlugin {
    plugins {
        create("Java8Converter") {
            id = "io.github.karlatemp.java8converter"
            implementationClass = "io.github.karlatemp.java8converter.Java8Converter"
        }
    }
}
pluginBundle {
    // These settings are set for the whole plugin bundle
    website = "https://github.com/Karlatemp/Java8Converter"
    vcsUrl = "https://github.com/Karlatemp/Java8Converter"

    // tags and description can be set for the whole bundle here, but can also
    // be set / overridden in the config for specific plugins
    description = "In order to convert the bytecode of java 9+ into the bytecode of java8"

    // The plugins block can contain multiple plugin entries.
    //
    // The name for each plugin block below (greetingsPlugin, goodbyePlugin)
    // does not affect the plugin configuration, but they need to be unique
    // for each plugin.

    // Plugin config blocks can set the id, displayName, version, description
    // and tags for each plugin.

    // id and displayName are mandatory.
    // If no version is set, the project version will be used.
    // If no tags or description are set, the tags or description from the
    // pluginBundle block will be used, but they must be set in one of the
    // two places.

    (plugins) {

        // first plugin
        "Java8Converter" {
            // id is captured from java-gradle-plugin configuration
            displayName = "Java8Converter"
            tags = listOf("jvm", "bytecode", "converter", "plugin")
            version = project.version.toString()
        }

    }

    // Optional overrides for Maven coordinates.
    // If you have an existing plugin deployed to Bintray and would like to keep
    // your existing group ID and artifact ID for continuity, you can specify
    // them here.
    //
    // As publishing to a custom group requires manual approval by the Gradle
    // team for security reasons, we recommend not overriding the group ID unless
    // you have an existing group ID that you wish to keep. If not overridden,
    // plugins will be published automatically without a manual approval process.
    //
    // You can also override the version of the deployed artifact here, though it
    // defaults to the project version, which would normally be sufficient.

    mavenCoordinates {
        groupId = "io.github.karlatemp"
        artifactId = "java8converter"
        version = project.version.toString()
    }
}