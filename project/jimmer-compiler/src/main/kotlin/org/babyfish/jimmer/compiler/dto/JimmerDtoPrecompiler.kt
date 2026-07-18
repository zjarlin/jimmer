package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentSnapshot
import org.babyfish.jimmer.compiler.JimmerCompilerSourceFilter
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import org.babyfish.jimmer.compiler.immutable.hasImmutableMarker
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiPosition
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiWorkspace

internal class JimmerDtoPrecompiler {
    fun compile(
        inputDocumentSnapshots: Collection<CompilerInputDocumentSnapshot>,
        immutableSchema: JimmerImmutableSchema,
        immutableSemanticRootTypeIds: Set<LsiSymbolId>,
        workspace: LsiWorkspace,
        sourceFilter: JimmerCompilerSourceFilter,
        defaultNullableInputModifier: DtoModifier,
    ): JimmerDtoPrecompileOutcome {
        require(defaultNullableInputModifier.isInputStrategy) {
            "Default nullable input modifier must be an input strategy"
        }
        val registry = LsiDtoTypeRegistry(immutableSchema, workspace)
        val documents = mutableListOf<JimmerDtoPrecompiledDocument>()
        val unresolvedDocuments = mutableListOf<JimmerDtoUnresolvedDocument>()
        val failures = mutableListOf<JimmerDtoCompilerFailure>()
        inputDocumentSnapshots
            .filter { snapshot -> snapshot.document.kind == CompilerInputDocumentKind.DTO }
            .sorted()
            .forEach { snapshot ->
                val inputDocument = snapshot.document
                val compiler = try {
                    LsiDtoCompiler(
                        dtoFile = inputDocument.toDtoFile(),
                        registry = registry,
                        defaultNullableInputModifier = defaultNullableInputModifier,
                    )
                } catch (exception: DtoAstException) {
                    failures += JimmerDtoCompilerFailure(
                        inputSnapshot = snapshot,
                        baseTypeId = null,
                        location = exception.toLocation(snapshot),
                        message = exception.message ?: "Invalid DTO document '${inputDocument.source.path}'",
                    )
                    return@forEach
                }
                if (!sourceFilter.accepts(compiler.sourceTypeName)) {
                    return@forEach
                }
                val baseTypeId = LsiSymbolId.type(compiler.sourceTypeName)
                val workspaceType = workspace[baseTypeId] as? LsiTypeDeclaration
                if (workspaceType != null && !workspaceType.hasImmutableMarker()) {
                    failures += JimmerDtoCompilerFailure(
                        inputSnapshot = snapshot,
                        baseTypeId = baseTypeId,
                        location = snapshot.referenceLocation(baseTypeId),
                        message = "DTO base type '${compiler.sourceTypeName}' is not an immutable type",
                    )
                    return@forEach
                }
                if (workspaceType != null && baseTypeId !in immutableSemanticRootTypeIds) {
                    return@forEach
                }
                val unresolvedTypeIds = buildSet {
                    snapshot.referencedTypeIds.filterTo(this) { typeId -> workspace[typeId] == null }
                    if (workspaceType == null) {
                        add(baseTypeId)
                    }
                }.sorted()
                if (unresolvedTypeIds.isNotEmpty()) {
                    unresolvedDocuments += JimmerDtoUnresolvedDocument(
                        inputSnapshot = snapshot,
                        baseTypeId = baseTypeId,
                        unresolvedTypeIds = unresolvedTypeIds,
                        message = "Cannot resolve DTO document '${inputDocument.source.path}' types: " +
                            unresolvedTypeIds.joinToString { typeId -> typeId.value },
                    )
                    return@forEach
                }
                val baseType = registry[baseTypeId]
                if (baseType == null) {
                    unresolvedDocuments += JimmerDtoUnresolvedDocument(
                        inputSnapshot = snapshot,
                        baseTypeId = baseTypeId,
                        unresolvedTypeIds = listOf(baseTypeId),
                        message = "No immutable type '${compiler.sourceTypeName}' for DTO document " +
                            "'${inputDocument.source.path}'",
                    )
                    return@forEach
                }
                if (baseType.immutableType.kind == JimmerImmutableTypeKind.MAPPED_SUPERCLASS) {
                    failures += JimmerDtoCompilerFailure(
                        inputSnapshot = snapshot,
                        baseTypeId = baseTypeId,
                        location = snapshot.referenceLocation(baseTypeId),
                        message = "DTO base type '${compiler.sourceTypeName}' cannot be a mapped superclass",
                    )
                    return@forEach
                }
                val dtoTypes = try {
                    compiler.compile(baseType)
                } catch (exception: DtoAstException) {
                    failures += JimmerDtoCompilerFailure(
                        inputSnapshot = snapshot,
                        baseTypeId = baseTypeId,
                        location = exception.toLocation(snapshot),
                        message = exception.message ?: "Invalid DTO document '${inputDocument.source.path}'",
                    )
                    return@forEach
                }
                documents += JimmerDtoPrecompiledDocument(
                    inputSnapshot = snapshot,
                    baseTypeId = baseTypeId,
                    sourceTypeName = compiler.sourceTypeName,
                    targetPackageName = compiler.targetPackageName,
                    dtoTypes = dtoTypes.toList(),
                )
            }
        return JimmerDtoPrecompileOutcome(
            schema = JimmerDtoPrecompiledSchema(documents),
            unresolvedDocuments = unresolvedDocuments.sortedBy(JimmerDtoUnresolvedDocument::inputSnapshot),
            failures = failures.sortedBy(JimmerDtoCompilerFailure::inputSnapshot),
        )
    }
}

private fun CompilerInputDocumentSnapshot.referenceLocation(typeId: LsiSymbolId): LsiLocation? {
    return references.firstOrNull { reference -> reference.typeId == typeId }?.location
}

private fun DtoAstException.toLocation(snapshot: CompilerInputDocumentSnapshot): LsiLocation {
    return LsiLocation(
        source = snapshot.document.source,
        start = LsiPosition(lineNumber, colNumber + 1),
    )
}

private fun CompilerInputDocument.toDtoFile(): DtoFile {
    val pathParts = relativePath.split('/')
    return DtoFile(
        source.path,
        content,
        projectName,
        sourceRoot,
        pathParts.dropLast(1),
        pathParts.last(),
    )
}
