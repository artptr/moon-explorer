package io.github.artptr.moonexplorer.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import java.util.Collections

/**
 * Records each file that the refresher analyzes again, and gives the list of the files. If [callReal] is true, the refresher also does the real restart.
 * The light project is the same for all tests. Thus the function puts the real restart back when the IDE disposes [disposable].
 */
fun RunMarkersRefresher.recordRestarts(disposable: Disposable, callReal: Boolean): MutableList<PsiFile> {
    val real = restart
    val files = Collections.synchronizedList(mutableListOf<PsiFile>())
    restart = { file ->
        files += file
        if (callReal) real(file)
    }
    Disposer.register(disposable) { restart = real }
    return files
}
