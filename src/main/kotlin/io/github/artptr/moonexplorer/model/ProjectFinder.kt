package io.github.artptr.moonexplorer.model

import java.nio.file.Path

/**
 * Gives the project that contains [file], or null if no project contains it. [root] is the workspace root.
 * If more than one project contains the file, the function gives the project with the longest `source` path. This is the nested project.
 * The function compares full directory names. Thus `apps/web` does not contain `apps/web-admin/index.ts`.
 * The function does not read files. Thus you can call it on the EDT.
 */
fun findProjectForFile(root: Path, file: Path, sources: List<ProjectSource>): ProjectSource? {
    val normalizedFile = file.toAbsolutePath().normalize()
    val normalizedRoot = root.toAbsolutePath().normalize()
    return sources
        .mapNotNull { source -> resolveSourceDir(normalizedRoot, source)?.let { source to it } }
        .filter { (_, dir) -> normalizedFile.startsWith(dir) }
        .maxByOrNull { (_, dir) -> dir.nameCount }
        ?.first
}

/**
 * Gives the directory of [source] in the workspace [root]. [root] must be absolute and normalized.
 * Gives null if the source is not a valid path on this computer. Such a source contains no files.
 */
internal fun resolveSourceDir(root: Path, source: ProjectSource): Path? =
    runCatching { root.resolve(source.source).normalize() }.getOrNull()
