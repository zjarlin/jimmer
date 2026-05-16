package site.addzero.lsi.codegen

import site.addzero.lsi.poet.LsiFileSpec

/**
 * compiler 侧最小生成产物契约。
 *
 * 迁移说明：生成产物契约下沉到 `lsi-core`，让 APT/KSP/shared metadata generator 统一依赖同一份 artifact 定义，
 * 而不是继续经由 `jimmer-ksp-ext` 间接获得它
 */
sealed interface GeneratedArtifact

data class GeneratedSourceArtifact(
    val qualifiedName: String,
    val content: String
) : GeneratedArtifact

data class GeneratedSourceFileArtifact(
    val fileSpec: LsiFileSpec
) : GeneratedArtifact

data class GeneratedResourceArtifact(
    val path: String,
    val content: String
) : GeneratedArtifact
