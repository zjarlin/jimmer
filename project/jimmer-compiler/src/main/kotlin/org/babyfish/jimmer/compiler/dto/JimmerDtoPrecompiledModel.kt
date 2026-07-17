package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.dto.compiler.DtoType
import site.addzero.lsi.core.LsiSymbolId

internal data class JimmerDtoPrecompiledSchema(
    val documents: List<JimmerDtoPrecompiledDocument>,
) {
    init {
        require(documents == documents.sortedBy { document -> document.inputDocument }) {
            "DTO precompiled documents must use stable input order"
        }
        require(documents.map { document -> document.inputDocument.source.path }.distinct().size == documents.size) {
            "DTO precompiled schema cannot contain duplicate input documents"
        }
    }
}

internal data class JimmerDtoPrecompiledDocument(
    val inputDocument: CompilerInputDocument,
    val baseTypeId: LsiSymbolId,
    val sourceTypeName: String,
    val targetPackageName: String?,
    val dtoTypes: List<DtoType<LsiDtoBaseType, LsiDtoBaseProp>>,
) {
    init {
        baseTypeId.requireTypeQualifiedName()
        require(sourceTypeName.isNotBlank()) { "DTO source type name cannot be blank" }
        require(dtoTypes.all { dtoType -> dtoType.baseType.id == baseTypeId }) {
            "DTO types must reference the document base type: ${inputDocument.source.path}"
        }
    }
}

internal data class JimmerDtoUnresolvedDocument(
    val inputDocument: CompilerInputDocument,
    val baseTypeId: LsiSymbolId,
    val message: String,
) {
    init {
        baseTypeId.requireTypeQualifiedName()
        require(message.isNotBlank()) { "Unresolved DTO document message cannot be blank" }
    }
}

internal data class JimmerDtoCompilerFailure(
    val inputDocument: CompilerInputDocument,
    val baseTypeId: LsiSymbolId?,
    val message: String,
) {
    init {
        baseTypeId?.requireTypeQualifiedName()
        require(message.isNotBlank()) { "DTO compiler failure message cannot be blank" }
    }
}

internal data class JimmerDtoPrecompileOutcome(
    val schema: JimmerDtoPrecompiledSchema,
    val unresolvedDocuments: List<JimmerDtoUnresolvedDocument>,
    val failures: List<JimmerDtoCompilerFailure>,
)
