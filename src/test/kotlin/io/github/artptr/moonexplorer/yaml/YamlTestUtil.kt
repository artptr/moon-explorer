package io.github.artptr.moonexplorer.yaml

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import java.nio.file.Path

/** Gives the PSI of the file [path] on the disk. */
fun Project.psiFileAt(path: Path): PsiFile {
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path) ?: error("No file $path")
    return PsiManager.getInstance(this).findFile(file) ?: error("No PSI for $path")
}

/** Gives the leaf elements of the file, in the sequence of the text. The IDE attaches the gutter icons to leaf elements. */
fun PsiFile.leaves(): List<PsiElement> = PsiTreeUtil.collectElements(this) { it.firstChild == null }.toList()
