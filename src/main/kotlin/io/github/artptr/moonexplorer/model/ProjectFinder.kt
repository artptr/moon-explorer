package io.github.artptr.moonexplorer.model

import java.nio.file.Path

/**
 * Gives the project that contains [file], or null if no project contains it. [root] is the workspace root. It must be absolute and normalized.
 * If more than one project contains the file, the function gives the project with the longest `source` path. This is the nested project.
 * The function compares full directory names. Thus `apps/web` does not contain `apps/web-admin/index.ts`.
 * The function does not read files. Thus you can call it on the EDT.
 */
fun findProjectForFile(root: Path, file: Path, sources: List<ProjectSource>): ProjectSource? {
    val normalizedFile = file.toAbsolutePath().normalize()
    return sources
        .mapNotNull { source -> resolveInRoot(root, source.source)?.let { source to it } }
        .filter { (_, dir) -> normalizedFile.startsWith(dir) }
        .maxByOrNull { (_, dir) -> dir.nameCount }
        ?.first
}

/**
 * Gives the path [relative] in the workspace [root]. [root] must be absolute and normalized.
 * Gives null if [relative] is not a valid path on this computer. Such a path contains no files.
 */
internal fun resolveInRoot(root: Path, relative: String): Path? = runCatching { root.resolve(relative).normalize() }.getOrNull()
