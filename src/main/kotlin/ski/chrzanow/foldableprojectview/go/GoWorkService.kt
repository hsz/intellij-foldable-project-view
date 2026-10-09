package ski.chrzanow.foldableprojectview.go

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import com.intellij.openapi.util.SystemInfo

@Service(Service.Level.PROJECT)
class GoWorkService(private val project: Project) {

    private var cachedStamp: Long = -1L
    private var cachedDirs: List<VirtualFile> = emptyList()

    fun isWorkspaceEnabled(): Boolean = getGoWorkFile() != null && workspaceModuleDirs().isNotEmpty()

    fun workspaceModuleDirs(): List<VirtualFile> {
        val goWork = getGoWorkFile() ?: return emptyList()
        val stamp = goWork.modificationStamp
        if (stamp != cachedStamp) {
            cachedDirs = parseUseDirectories(goWork)
            cachedStamp = stamp
        }
        return cachedDirs
    }

    fun isWorkspaceModuleDir(dir: VirtualFile?): Boolean {
        if (dir == null) return false
        val ignoreCase = SystemInfo.isWindows
        val dirPath = dir.path
        return workspaceModuleDirs().any { it.path.equals(dirPath, ignoreCase) }
    }

    fun findWorkspaceRootFor(file: VirtualFile): VirtualFile? {
        val dirs = workspaceModuleDirs()
        if (dirs.isEmpty()) return null
        return dirs
            .filter { VfsUtilCore.isAncestor(it, file, false) }
            .maxByOrNull { it.path.length }
    }

    private fun getGoWorkFile(): VirtualFile? =
        project.guessProjectDir()?.findChild("go.work")

    private fun parseUseDirectories(goWork: VirtualFile): List<VirtualFile> {
        val text = runCatching { VfsUtilCore.loadText(goWork) }.getOrNull() ?: return emptyList()
        val base = goWork.parent ?: return emptyList()
        val basePath = base.toNioPath()

        val inBlock = object { var value = false }
        val result = mutableListOf<VirtualFile>()

        fun addPath(raw: String) {
            val p = raw.trim().removeSurrounding("\"", "\"")
            if (p.isEmpty()) return
            val resolved: Path = try {
                val candidate = if (Path.of(p).isAbsolute) Path.of(p) else basePath.resolve(p)
                candidate.normalize()
            } catch (e: Exception) {
                return
            }
            val vf = LocalFileSystem.getInstance().findFileByPath(resolved.toString())
            if (vf != null) {
                result.add(vf)
            }
        }

        text.lineSequence().forEach { rawLine ->
            var line = rawLine.substringBefore("//").trim()
            if (line.isEmpty()) return@forEach

            // Single-line use with parentheses on the same line: use (a b)
            if (!inBlock.value && line.startsWith("use ") && line.contains('(') && line.contains(')')) {
                val inner = line.substringAfter('(').substringBeforeLast(')').trim()
                inner.split(Regex("""\s+""")).filter { it.isNotBlank() }.forEach { addPath(it) }
                return@forEach
            }

            if (!inBlock.value && (line == "use(" || line.startsWith("use (") || line == "use (")) {
                inBlock.value = true
                val after = line.substringAfter('(', missingDelimiterValue = "").trim()
                if (after.isNotEmpty() && !after.startsWith("//")) {
                    // support: use ( ./a   ./b ) on same line
                    val tail = after.substringBefore(")").trim()
                    if (tail.isNotEmpty()) tail.split(Regex("""\s+""")).forEach { addPath(it) }
                    if (after.contains(")")) inBlock.value = false
                }
                return@forEach
            }

            if (!inBlock.value && line.startsWith("use ")) {
                val rest = line.removePrefix("use ").trim()
                val token = if (rest.startsWith('"')) rest.drop(1).substringBefore('"') else rest.substringBefore(" ")
                addPath(token)
                return@forEach
            }

            if (inBlock.value) {
                if (line.startsWith(")")) {
                    inBlock.value = false
                    return@forEach
                }
                val token = if (line.startsWith('"')) line.drop(1).substringBefore('"') else line.substringBefore(" ")
                addPath(token)
            }
        }

        // Deduplicate and keep stable order
        return result.distinctBy { it.path }
    }
}
