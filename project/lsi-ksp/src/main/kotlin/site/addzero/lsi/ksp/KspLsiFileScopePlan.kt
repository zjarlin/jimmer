package site.addzero.lsi.ksp

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.validate
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

/**
 * 当前 KSP 轮内冻结文件注解作用域所需的原生输入。
 */
data class KspLsiFileScopeInput(
    val file: KSFile,
    val normalizedSourcePath: String,
    val logicalPath: String,
    val annotations: List<KSAnnotation>,
) {
    val id: LsiSymbolId = LsiSymbolId.fileScope(file.packageName.asString(), logicalPath)
}

/**
 * 使用全量可见文件统一计算作用域身份，再按当前轮文件选择相同输入。
 */
data class KspLsiFileScopePlan(
    val validScopes: List<KspLsiFileScopeInput>,
    val invalidScopes: List<KspLsiFileScopeInput>,
) {
    fun validScopesFor(sourcePaths: Set<String>): List<KspLsiFileScopeInput> {
        return validScopes.filter { scope -> scope.normalizedSourcePath in sourcePaths }
    }
}

fun Collection<KSFile>.toKspLsiFileScopePlan(): KspLsiFileScopePlan {
    val candidates = asSequence()
        .toStableKspFileList()
        .mapNotNull { file ->
            val annotations = file.annotations.toList()
            if (annotations.isEmpty()) {
                null
            } else {
                KspLsiFileScopeCandidate(
                    file = file,
                    normalizedSourcePath = file.normalizedLsiSourcePath(),
                    annotations = annotations,
                )
            }
        }
        .toList()
    val inputs = candidates
        .groupBy { candidate -> candidate.file.packageName.asString() to candidate.file.fileName }
        .toSortedMap(compareBy<Pair<String, String>>({ key -> key.first }, { key -> key.second }))
        .values
        .flatMap(List<KspLsiFileScopeCandidate>::withLogicalPaths)
        .sortedBy(KspLsiFileScopeInput::normalizedSourcePath)
    val validScopes = mutableListOf<KspLsiFileScopeInput>()
    val invalidScopes = mutableListOf<KspLsiFileScopeInput>()
    inputs.forEach { input ->
        if (input.annotations.all { annotation -> annotation.validate() }) {
            validScopes += input
        } else {
            invalidScopes += input
        }
    }
    return KspLsiFileScopePlan(
        validScopes = validScopes,
        invalidScopes = invalidScopes,
    )
}

fun Sequence<KSFile>.toStableKspFileList(): List<KSFile> {
    return distinctBy(KSFile::normalizedLsiSourcePath)
        .sortedBy(KSFile::normalizedLsiSourcePath)
        .toList()
}

private data class KspLsiFileScopeCandidate(
    val file: KSFile,
    val normalizedSourcePath: String,
    val annotations: List<KSAnnotation>,
)

private fun List<KspLsiFileScopeCandidate>.withLogicalPaths(): List<KspLsiFileScopeInput> {
    if (size == 1) {
        val candidate = single()
        return listOf(candidate.toInput(candidate.file.fileName))
    }
    val pathSegments = associateWith { candidate -> candidate.normalizedSourcePath.split('/') }
    require(pathSegments.values.distinct().size == size) {
        "KSP file annotation scopes require distinct normalized source paths: " +
            joinToString { candidate -> candidate.normalizedSourcePath }
    }
    return map { candidate ->
        val segments = pathSegments.getValue(candidate)
        val logicalPath = (1..segments.size).firstNotNullOfOrNull { segmentCount ->
            val suffix = segments.takeLast(segmentCount)
            suffix.joinToString("/").takeIf {
                pathSegments.values.count { otherSegments -> otherSegments.endsWith(suffix) } == 1
            }
        } ?: error("Cannot derive a unique KSP file scope path for '${candidate.normalizedSourcePath}'")
        candidate.toInput(logicalPath)
    }
}

private fun KspLsiFileScopeCandidate.toInput(logicalPath: String): KspLsiFileScopeInput {
    return KspLsiFileScopeInput(
        file = file,
        normalizedSourcePath = normalizedSourcePath,
        logicalPath = logicalPath,
        annotations = annotations,
    )
}

fun KSFile.normalizedLsiSourcePath(): String {
    val path = filePath.takeIf(String::isNotBlank) ?: fileName
    return LsiSource.of(path).path
}

private fun List<String>.endsWith(suffix: List<String>): Boolean {
    if (size < suffix.size) {
        return false
    }
    return subList(size - suffix.size, size) == suffix
}
