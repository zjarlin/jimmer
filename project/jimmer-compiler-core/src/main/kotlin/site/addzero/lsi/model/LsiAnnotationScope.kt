package site.addzero.lsi.model

import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiSource
import site.addzero.lsi.core.LsiSymbolId

enum class LsiAnnotationScopeKind {
    PACKAGE,
    FILE
}

/**
 * 承载不属于声明的包级和文件级注解语义。
 */
sealed interface LsiAnnotationScope {
    val id: LsiSymbolId
    val kind: LsiAnnotationScopeKind
    val packageName: String
    val annotations: List<LsiAnnotation>
    val location: LsiLocation?
    val origin: LsiOrigin
}

data class LsiPackageAnnotationScope(
    override val packageName: String,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin,
) : LsiAnnotationScope {

    override val id: LsiSymbolId = LsiSymbolId.packageScope(packageName)

    override val kind: LsiAnnotationScopeKind = LsiAnnotationScopeKind.PACKAGE
}

data class LsiFileAnnotationScope(
    override val packageName: String,
    val logicalPath: String,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin,
) : LsiAnnotationScope {

    init {
        require(logicalPath.isNotBlank()) { "LSI file annotation scope logical path cannot be blank" }
        require(
            logicalPath == LsiSource.of(logicalPath).path &&
                !logicalPath.hasWindowsDrivePrefix()
        ) {
            "LSI file annotation scope logical path must be normalized and relative: '$logicalPath'"
        }
    }

    override val id: LsiSymbolId = LsiSymbolId.fileScope(packageName, logicalPath)

    override val kind: LsiAnnotationScopeKind = LsiAnnotationScopeKind.FILE
}

private fun String.hasWindowsDrivePrefix(): Boolean {
    return length >= 3 && this[0].isLetter() && this[1] == ':' && this[2] == '/'
}
