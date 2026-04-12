import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val androidCompileSdk = 36
val androidMinSdk = 26
val localProperties = Properties().apply {
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
        localPropertiesFile.inputStream().use(::load)
    }
}
val resolvedFirebaseApiKey =
    providers.gradleProperty("secretaria.firebaseApiKey").orNull
        ?: providers.environmentVariable("SECRETARIA_FIREBASE_API_KEY").orNull
        ?: localProperties.getProperty("secretaria.firebaseApiKey")
val desktopFirebaseApiKey = resolvedFirebaseApiKey
val generatedWebResourcesDir = layout.buildDirectory.dir("generated/webMain/resources")
val generateWebFirebaseConfig by tasks.registering(GenerateWebFirebaseConfigTask::class) {
    firebaseApiKey.set(resolvedFirebaseApiKey.orEmpty())
    outputDir.set(generatedWebResourcesDir)
}

abstract class GenerateWebFirebaseConfigTask : DefaultTask() {

    @get:Input
    abstract val firebaseApiKey: Property<String>

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
        outputFile.writeText(
            """
                document.documentElement.setAttribute(
                    "data-secretaria-firebase-api-key",
                    "$escapedApiKey"
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
            implementation(libs.firebase.auth)
            implementation(libs.firebase.firestore)
            implementation(libs.kotlinx.coroutinesPlayServices)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
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
        }
    }
}

tasks.withType<JavaExec>().configureEach {
    if (name == "run" && !desktopFirebaseApiKey.isNullOrBlank()) {
        jvmArgs("-Dsecretaria.firebaseApiKey=$desktopFirebaseApiKey")
    }
}
