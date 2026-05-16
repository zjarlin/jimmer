package site.addzero.lsi.jimmer.dto

import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.OsFile
import site.addzero.lsi.file.LsiFile
import java.io.File

class DtoContext(anchorFilePath: String?, dtoDirs: Collection<String>) {

    constructor(anyFile: LsiFile?, dtoDirs: Collection<String>) : this(anyFile?.filePath, dtoDirs)

    val dtoFiles: List<DtoFile>

    init {
        var file = anchorFilePath?.takeIf { it.isNotBlank() }?.let(::File)
        val dtoDirFileMap = mutableMapOf<String, File>()
        var projectDir: String? = null
        while (file != null) {
            val prjDir = collectDtoDirFileMap(file, dtoDirs, dtoDirFileMap)
            if (projectDir == null) {
                projectDir = prjDir
            }
            file = file.parentFile
        }
        val dtoFiles = mutableListOf<DtoFile>()
        for ((key, value) in dtoDirFileMap) {
            val subFiles = value.listFiles() ?: continue
            val resolvedProjectDir = projectDir ?: continue
            for (subFile in subFiles) {
                collectDtoFiles(resolvedProjectDir, key, subFile, mutableListOf(), dtoFiles)
            }
        }
        this.dtoFiles = dtoFiles
    }

    private fun collectDtoDirFileMap(
        baseFile: File,
        dtoDirs: Collection<String>,
        dtoDirFileMap: MutableMap<String, File>
    ): String? {
        var projectDir: String? = null
        for (dtoDir in dtoDirs) {
            var subFile: File? = baseFile
            for (part in dtoDir.split("/")) {
                subFile = File(subFile, part)
                if (!subFile.isDirectory) {
                    subFile = null
                    break
                }
            }
            if (subFile != null) {
                dtoDirFileMap[dtoDir] = subFile
                projectDir = baseFile.name
            }
        }
        return projectDir
    }

    private fun collectDtoFiles(
        projectDir: String,
        dtoDir: String,
        file: File,
        paths: MutableList<String>,
        dtoFiles: MutableList<DtoFile>
    ) {
        if (file.isFile && file.name.endsWith(".dto")) {
            dtoFiles += DtoFile(OsFile.of(file), projectDir, dtoDir, paths, file.name)
            return
        }
        val subFiles = file.listFiles() ?: return
        paths += file.name
        for (subFile in subFiles) {
            collectDtoFiles(projectDir, dtoDir, subFile, paths, dtoFiles)
        }
        paths.removeAt(paths.lastIndex)
    }
}
