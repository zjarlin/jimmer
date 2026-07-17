package org.babyfish.jimmer.compiler.dto

import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableSchema
import org.babyfish.jimmer.compiler.immutable.JimmerImmutableTypeKind
import org.babyfish.jimmer.dto.compiler.DtoAstException
import org.babyfish.jimmer.dto.compiler.DtoFile
import org.babyfish.jimmer.dto.compiler.DtoModifier
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiWorkspace

internal class JimmerDtoPrecompiler {
    fun compile(
        inputDocuments: Collection<CompilerInputDocument>,
        immutableSchema: JimmerImmutableSchema,
        workspace: LsiWorkspace,
        defaultNullableInputModifier: DtoModifier,
    ): JimmerDtoPrecompileOutcome {
        require(defaultNullableInputModifier.isInputStrategy) {
            "Default nullable input modifier must be an input strategy"
        }
        val registry = LsiDtoTypeRegistry(immutableSchema, workspace)
        val documents = mutableListOf<JimmerDtoPrecompiledDocument>()
        val unresolvedDocuments = mutableListOf<JimmerDtoUnresolvedDocument>()
        val failures = mutableListOf<JimmerDtoCompilerFailure>()
        inputDocuments
            .filter { inputDocument -> inputDocument.kind == CompilerInputDocumentKind.DTO }
            .sorted()
            .forEach { inputDocument ->
                val compiler = try {
                    LsiDtoCompiler(
                        dtoFile = inputDocument.toDtoFile(),
                        registry = registry,
                        defaultNullableInputModifier = defaultNullableInputModifier,
                    )
                } catch (exception: DtoAstException) {
                    failures += JimmerDtoCompilerFailure(
                        inputDocument = inputDocument,
                        baseTypeId = null,
                        message = exception.message ?: "Invalid DTO document '${inputDocument.source.path}'",
                    )
                    return@forEach
                }
                val baseTypeId = LsiSymbolId.type(compiler.sourceTypeName)
                val baseType = registry[baseTypeId]
                if (baseType == null) {
                    unresolvedDocuments += JimmerDtoUnresolvedDocument(
                        inputDocument = inputDocument,
                        baseTypeId = baseTypeId,
                        message = "No immutable type '${compiler.sourceTypeName}' for DTO document " +
                            "'${inputDocument.source.path}'",
                    )
                    return@forEach
                }
                if (baseType.immutableType.kind == JimmerImmutableTypeKind.MAPPED_SUPERCLASS) {
                    failures += JimmerDtoCompilerFailure(
                        inputDocument = inputDocument,
                        baseTypeId = baseTypeId,
                        message = "DTO base type '${compiler.sourceTypeName}' cannot be a mapped superclass",
                    )
                    return@forEach
                }
                val dtoTypes = try {
                    compiler.compile(baseType)
                } catch (exception: DtoAstException) {
                    failures += JimmerDtoCompilerFailure(
                        inputDocument = inputDocument,
                        baseTypeId = baseTypeId,
                        message = exception.message ?: "Invalid DTO document '${inputDocument.source.path}'",
                    )
                    return@forEach
                }
                documents += JimmerDtoPrecompiledDocument(
                    inputDocument = inputDocument,
                    baseTypeId = baseTypeId,
                    sourceTypeName = compiler.sourceTypeName,
                    targetPackageName = compiler.targetPackageName,
                    dtoTypes = dtoTypes.toList(),
                )
            }
        return JimmerDtoPrecompileOutcome(
            schema = JimmerDtoPrecompiledSchema(documents),
            unresolvedDocuments = unresolvedDocuments.sortedBy { document -> document.inputDocument },
            failures = failures.sortedBy { failure -> failure.inputDocument },
        )
    }
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
