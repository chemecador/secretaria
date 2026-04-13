import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.security.SecureRandom
import java.util.Properties

fun parseProjectIdFromGoogleServices(json: String): String? =
    Regex("\"project_id\"\\s*:\\s*\"([^\"]+)\"")
        .find(json)
        ?.groupValues
        ?.getOrNull(1)

val androidCompileSdk = 36
val androidMinSdk = 26
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val googleServicesProjectId =
    rootProject.file("androidApp/google-services.json")
        .takeIf { it.exists() }
        ?.readText()
        ?.let(::parseProjectIdFromGoogleServices)
val resolvedFirebaseApiKey =
    providers.gradleProperty("secretaria.firebaseApiKey").orNull
        ?: providers.environmentVariable("SECRETARIA_FIREBASE_API_KEY").orNull
        ?: localProperties.getProperty("secretaria.firebaseApiKey")
val resolvedFirebaseProjectId =
    providers.gradleProperty("secretaria.firebaseProjectId").orNull
        ?: providers.environmentVariable("SECRETARIA_FIREBASE_PROJECT_ID").orNull
        ?: localProperties.getProperty("secretaria.firebaseProjectId")
        ?: googleServicesProjectId
val resolvedGoogleDesktopClientId =
    providers.gradleProperty("secretaria.googleDesktopClientId").orNull
        ?: providers.environmentVariable("SECRETARIA_GOOGLE_DESKTOP_CLIENT_ID").orNull
        ?: localProperties.getProperty("secretaria.googleDesktopClientId")
val resolvedGoogleDesktopClientSecret =
    providers.gradleProperty("secretaria.googleDesktopClientSecret").orNull
        ?: providers.environmentVariable("SECRETARIA_GOOGLE_DESKTOP_CLIENT_SECRET").orNull
        ?: localProperties.getProperty("secretaria.googleDesktopClientSecret")
val desktopFirebaseApiKey = resolvedFirebaseApiKey
val generatedWebResourcesDir = layout.buildDirectory.dir("generated/webMain/resources")
val generatedDesktopConfigDir = layout.buildDirectory.dir("generated/jvmMain/kotlin")
val generateDesktopBuildConfig by tasks.registering {
    val apiKey = resolvedFirebaseApiKey.orEmpty()
    val projectId = resolvedFirebaseProjectId.orEmpty()
    val googleDesktopClientId = resolvedGoogleDesktopClientId.orEmpty()
    val googleDesktopClientSecret = resolvedGoogleDesktopClientSecret.orEmpty()
    val outputDir = generatedDesktopConfigDir

    inputs.property("apiKeyHash", apiKey.hashCode())
    inputs.property("projectIdHash", projectId.hashCode())
    inputs.property("googleDesktopClientIdHash", googleDesktopClientId.hashCode())
    inputs.property("googleDesktopClientSecretHash", googleDesktopClientSecret.hashCode())
    outputs.dir(outputDir)

    doLast {
        val dir = outputDir.get().asFile
            .resolve("com/chemecador/secretaria/config")
        dir.mkdirs()

        val rng = SecureRandom()
        val xorKey = ByteArray(32).also { rng.nextBytes(it) }

        fun obfuscate(value: String): String {
            val bytes = value.toByteArray(Charsets.UTF_8)
            return ByteArray(bytes.size) { i ->
                (bytes[i].toInt() xor xorKey[i % xorKey.size].toInt()).toByte()
            }.joinToString(",")
        }

        dir.resolve("DesktopBuildConfig.kt").writeText(
            buildString {
                appendLine("package com.chemecador.secretaria.config")
                appendLine()
                appendLine("internal object DesktopBuildConfig {")
                appendLine("    private val k = byteArrayOf(${xorKey.joinToString(",")})")
                appendLine("    private val a = byteArrayOf(${obfuscate(apiKey)})")
                appendLine("    private val p = byteArrayOf(${obfuscate(projectId)})")
                appendLine("    private val g = byteArrayOf(${obfuscate(googleDesktopClientId)})")
                appendLine("    private val s = byteArrayOf(${obfuscate(googleDesktopClientSecret)})")
                appendLine()
                appendLine("    val firebaseApiKey: String get() = d(a)")
                appendLine("    val firebaseProjectId: String get() = d(p)")
                appendLine("    val googleDesktopClientId: String get() = d(g)")
                appendLine("    val googleDesktopClientSecret: String get() = d(s)")
                appendLine()
                appendLine("    private fun d(b: ByteArray): String =")
                appendLine("        ByteArray(b.size) { (b[it].toInt() xor k[it % k.size].toInt()).toByte() }")
                appendLine("            .toString(Charsets.UTF_8)")
                appendLine("}")
            },
        )
    }
}
val generateWebFirebaseConfig by tasks.registering(GenerateWebFirebaseConfigTask::class) {
    firebaseApiKey.set(resolvedFirebaseApiKey.orEmpty())
    firebaseProjectId.set(resolvedFirebaseProjectId.orEmpty())
    outputDir.set(generatedWebResourcesDir)
}

abstract class GenerateWebFirebaseConfigTask : DefaultTask() {

    @get:Input
    abstract val firebaseApiKey: Property<String>

    @get:Input
    abstract val firebaseProjectId: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outputDir = outputDir.get().asFile
        val outputFile = outputDir.resolve("firebase-config.js")
        outputFile.parentFile.mkdirs()
        val escapedApiKey = firebaseApiKey.get()
            .replace("</", "<\\/")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        val escapedProjectId = firebaseProjectId.get()
            .replace("</", "<\\/")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
        outputFile.writeText(
            """
                document.documentElement.setAttribute(
                    "data-secretaria-firebase-api-key",
                    "$escapedApiKey"
                );
                document.documentElement.setAttribute(
                    "data-secretaria-firebase-project-id",
                    "$escapedProjectId"
                );
            """.trimIndent(),
        )
    }
}

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

kotlin {
    android {
        namespace = "com.chemecador.secretaria.shared"
        compileSdk = androidCompileSdk
        minSdk = androidMinSdk
        androidResources {
            enable = true
        }
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }
    
    jvm()
    
    js {
        browser()
        binaries.executable()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }
    
    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.kotlinx.coroutinesCore)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutinesTest)
        }
        androidMain.dependencies {
            implementation(libs.androidx.credentials)
            implementation(libs.androidx.credentials.play.services.auth)
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.googleid)
            implementation(libs.kotlinx.coroutinesPlayServices)
        }
        iosMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
        getByName("jvmMain") {
            kotlin.srcDir(generatedDesktopConfigDir)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.kotlinx.serialization.json)
        }
        jsMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}

tasks.matching { it.name == "jsProcessResources" || it.name == "wasmJsProcessResources" }.configureEach {
    dependsOn(generateWebFirebaseConfig)
    if (this is Copy) {
        from(generateWebFirebaseConfig)
    }
}

tasks.named("compileKotlinJvm") {
    dependsOn(generateDesktopBuildConfig)
}

dependencies {
    "androidRuntimeClasspath"(libs.compose.uiTooling)
}

compose.desktop {
    application {
        mainClass = "com.chemecador.secretaria.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.chemecador.secretaria"
            packageVersion = "1.0.0"
            modules("java.desktop", "java.net.http", "jdk.httpserver")
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    if (name == "run" && !desktopFirebaseApiKey.isNullOrBlank()) {
        jvmArgs("-Dsecretaria.firebaseApiKey=$desktopFirebaseApiKey")
    }
}
