package com.chemecador.secretaria.config

import java.io.File
import java.util.Properties

internal fun readNearbyFileText(
    relativePath: String,
    workingDirectoryProvider: () -> File = { File("").absoluteFile },
    extraSearchRootsProvider: () -> Sequence<File> = ::defaultSearchRoots,
): String? =
    nearbyFiles(
        relativePath = relativePath,
        workingDirectoryProvider = workingDirectoryProvider,
        extraSearchRootsProvider = extraSearchRootsProvider,
    ).firstNotNullOfOrNull { candidate ->
        if (candidate.exists()) {
            candidate.readText()
        } else {
            null
        }
    }

internal fun readLocalProperty(
    propertyName: String,
    workingDirectoryProvider: () -> File = { File("").absoluteFile },
    extraSearchRootsProvider: () -> Sequence<File> = ::defaultSearchRoots,
): String? =
    nearbyFiles(
        relativePath = "local.properties",
        workingDirectoryProvider = workingDirectoryProvider,
        extraSearchRootsProvider = extraSearchRootsProvider,
    ).firstNotNullOfOrNull { localPropertiesFile ->
            if (!localPropertiesFile.exists()) {
                null
            } else {
                Properties().apply {
                    localPropertiesFile.inputStream().use(::load)
                }.getProperty(propertyName)
                    ?.takeUnless { it.isBlank() }
            }
        }

private fun nearbyFiles(
    relativePath: String,
    workingDirectoryProvider: () -> File,
    extraSearchRootsProvider: () -> Sequence<File>,
): Sequence<File> =
    sequenceOf(workingDirectoryProvider())
        .plus(extraSearchRootsProvider())
        .mapNotNull(File::asSearchRootOrNull)
        .flatMap(::ancestorDirectories)
        .map { directory -> File(directory, relativePath) }
        .distinctBy { file -> file.absolutePath }

private fun defaultSearchRoots(): Sequence<File> =
    sequence {
        classCodeSourceRoot()?.let { yield(it) }
        yieldAll(classPathRoots())
    }

private fun classCodeSourceRoot(): File? =
    runCatching {
        File(LocalPropertiesMarker::class.java.protectionDomain.codeSource.location.toURI())
    }.getOrNull()

private fun classPathRoots(): Sequence<File> =
    System.getProperty("java.class.path")
        ?.split(File.pathSeparatorChar)
        ?.asSequence()
        ?.map(::File)
        ?: emptySequence()

private fun File.asSearchRootOrNull(): File? =
    when {
        isDirectory -> absoluteFile
        isFile -> parentFile?.absoluteFile
        else -> null
    }

private fun ancestorDirectories(start: File): Sequence<File> =
    generateSequence(start) { directory -> directory.parentFile }

private object LocalPropertiesMarker
