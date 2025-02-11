import org.gradle.configurationcache.extensions.capitalized
import java.awt.GraphicsEnvironment
import java.io.ByteArrayOutputStream

plugins {
    application
    scala
    alias(libs.plugins.gitSemVer)
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.qa)
    alias(libs.plugins.multiJvmTesting)
    alias(libs.plugins.taskTree)
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

val usesJvm: Int =
    File(File(projectDir, "docker/sim"), "Dockerfile")
        .readLines()
        .first { it.isNotBlank() }
        .let {
            Regex("FROM\\s+eclipse-temurin:(\\d+)\\s*$")
                .find(it)
                ?.groups
                ?.get(1)
                ?.value
                ?: throw IllegalStateException("Cannot read information on the JVM to use.")
        }.toInt()

multiJvm {
    jvmVersionForCompilation.set(usesJvm)
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(libs.bundles.alchemist.scafi)
    implementation(libs.swi.prolog)
    implementation("org.scala-lang:scala-library:2.13.16")
    if (!GraphicsEnvironment.isHeadless()) {
        implementation("it.unibo.alchemist:alchemist-swingui:${libs.versions.alchemist.get()}")
    }
}

// Heap size estimation for batches
val maxHeap: Long? by project
val heap: Long =
    maxHeap ?: if (System.getProperty("os.name").lowercase().contains("linux")) {
        ByteArrayOutputStream()
            .use { output ->
                exec {
                    executable = "bash"
                    args = listOf("-c", "cat /proc/meminfo | grep MemAvailable | grep -o '[0-9]*'")
                    standardOutput = output
                }
                output.toString().trim().toLong() / 1024
            }.also { println("Detected ${it}MB RAM available.") } * 9 / 10
    } else {
        // Guess 16GB RAM of which 2 used by the OS
        14 * 1024L
    }
val taskSizeFromProject: Int? by project
val taskSize = taskSizeFromProject ?: 512
val threadCount = maxOf(1, minOf(Runtime.getRuntime().availableProcessors(), heap.toInt() / taskSize))

val alchemistGroup = "Run Alchemist"
/*
 * This task is used to run all experiments in sequence
 */
val runAllGraphic by tasks.register<DefaultTask>("runAllGraphic") {
    group = alchemistGroup
    description = "Launches all simulations with the graphic subsystem enabled"
}
val runAllBatch by tasks.register<DefaultTask>("runAllBatch") {
    group = alchemistGroup
    description = "Launches all experiments"
}
/*
 * Scan the folder with the simulation files, and create a task for each one of them.
 */
File(rootProject.rootDir.path + "/src/main/yaml")
    .listFiles()
    ?.filter { it.extension == "yml" }
    ?.sortedBy { it.nameWithoutExtension }
    ?.forEach {
        fun basetask(
            name: String,
            additionalConfiguration: JavaExec.() -> Unit = {},
        ) = tasks.register<JavaExec>(name) {
            group = alchemistGroup
            description = "Launches graphic simulation ${it.nameWithoutExtension}"
            mainClass.set("it.unibo.alchemist.Alchemist")
            // -- Set the environment variables for SWI-Prolog
            System.getenv("LD_LIBRARY_PATH") ?:
                environment("LD_LIBRARY_PATH", "/usr/lib/swipl/lib/x86_64-linux")
            System.getenv("SWI_HOME_DIR") ?:
                environment("SWI_HOME_DIR", "/usr/lib/swipl")
            System.getenv("SWI_EXEC_FILE") ?:
                environment("SWI_EXEC_FILE", "/usr/lib/swipl/bin/x86_64-linux/swipl")
            System.getenv("SWIPL_BOOT_FILE") ?:
                environment("SWIPL_BOOT_FILE", "/usr/lib/swipl/boot.prc")
            // -- End of SWI-Prolog environment variables
            classpath = sourceSets["main"].runtimeClasspath + files("/usr/lib/swipl/lib/jpl.jar")
            args("run", it.absolutePath)
            javaLauncher.set(
                javaToolchains.launcherFor {
                    languageVersion.set(JavaLanguageVersion.of(usesJvm))
                },
            )
            if (System.getenv("CI") == "true") {
                args("--override", "terminate: { type: AfterTime, parameters: [2] } ")
            } else {
                this.additionalConfiguration()
            }
        }
        val capitalizedName = it.nameWithoutExtension.capitalized()
        val graphic by basetask("run${capitalizedName}Graphic") {
            jvmArgs("-Dsun.java2d.opengl=false")
            args(
                "--override",
                "monitors: { type: SwingGUI, parameters: { graphics: effects/${it.nameWithoutExtension}.json } }",
                "--override",
                "launcher: { parameters: { batch: [], autoStart: false } }",
            )
        }
        runAllGraphic.dependsOn(graphic)
        val batch by basetask("run${capitalizedName}Batch") {
            description = "Launches batch experiments for $capitalizedName"
            maxHeapSize = "${minOf(heap.toInt(), Runtime.getRuntime().availableProcessors() * taskSize)}m"
            File("data").mkdirs()
//            args(
//                "--override",
//                """
//                launcher: {
//                    parameters: {
//                        batch: [ seed, spacing, error ],
//                        showProgress: true,
//                        autoStart: true,
//                        parallelism: $threadCount,
//                    }
//                }
//                """.trimIndent(),
//            )
        }
        runAllBatch.dependsOn(batch)
    }
