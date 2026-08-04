package site.addzero.lsi.ksp

import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.validate
import site.addzero.lsi.model.LsiFrontendOptions
import site.addzero.lsi.core.LsiSymbolId

/**
 * 当前 KSP 轮内的原生符号索引，不得跨轮保存。
 */
data class KspLsiRoundSymbols(
    val allSourceFiles: List<KSFile>,
    val currentSourceFiles: List<KSFile>,
    val allValidFileScopes: List<KspLsiFileScopeInput>,
    val currentValidFileScopes: List<KspLsiFileScopeInput>,
    val allValidRootTypes: List<KSClassDeclaration>,
    val currentValidRootTypes: List<KSClassDeclaration>,
    val invalidRootTypes: List<KSClassDeclaration>,
    val invalidFileAnnotationScopes: List<KspLsiFileScopeInput>,
    val annotatedById: Map<LsiSymbolId, KSAnnotated>,
    val filesById: Map<LsiSymbolId, KSFile>,
)

fun Resolver.toKspLsiRoundSymbols(
    frontendOptions: LsiFrontendOptions,
    pendingFileScopeSourcePaths: Set<String>,
): KspLsiRoundSymbols {
    val allSourceFiles = getAllFiles().toStableKspFileList()
    val currentSourceFiles = getNewFiles().toStableKspFileList()
    val fileScopePlan = allSourceFiles.toKspLsiFileScopePlan()
    val currentFileScopeSourcePaths = currentSourceFiles
        .mapTo(hashSetOf(), KSFile::normalizedLsiSourcePath)
        .apply { addAll(pendingFileScopeSourcePaths) }
    val allRoots = allSourceFiles.asSequence().toKspRootTypes()
    val currentRoots = currentSourceFiles.asSequence().toKspRootTypes()
    val allValidRoots = mutableListOf<Pair<KSClassDeclaration, KSFile>>()
    val invalidRoots = mutableListOf<KSClassDeclaration>()
    for ((declaration, file) in allRoots) {
        if (declaration.validate()) {
            allValidRoots += declaration to file
        } else if (declaration.origin == Origin.KOTLIN) {
            invalidRoots += declaration
        }
    }
    val currentValidRoots = currentRoots.filter { (declaration, _) -> declaration.validate() }
    return KspLsiRoundSymbolIndexer(this, frontendOptions).index(
        allValidRoots = allValidRoots,
        currentValidRoots = currentValidRoots,
        invalidRoots = invalidRoots,
        allSourceFiles = allSourceFiles,
        currentSourceFiles = currentSourceFiles,
        allValidFileScopes = fileScopePlan.validScopes,
        currentValidFileScopes = fileScopePlan.validScopesFor(currentFileScopeSourcePaths),
        invalidFileAnnotationScopes = fileScopePlan.invalidScopes.filter { scope ->
            scope.file.origin == Origin.KOTLIN
        },
    )
}

private fun Sequence<KSFile>.toKspRootTypes(): List<Pair<KSClassDeclaration, KSFile>> {
    return flatMap { file ->
        file.declarations
            .filterIsInstance<KSClassDeclaration>()
            .filter { declaration -> declaration.classKind != ClassKind.ENUM_ENTRY }
            .map { declaration -> declaration to file }
    }.distinctBy { (declaration, _) -> declaration.qualifiedName?.asString() }
        .sortedBy { (declaration, _) -> declaration.qualifiedName?.asString().orEmpty() }
        .toList()
}

private class KspLsiRoundSymbolIndexer(
    resolver: Resolver,
    private val frontendOptions: LsiFrontendOptions,
) {
    private val typeContext = KspLsiTypeContext(resolver)

    private val annotatedById = linkedMapOf<LsiSymbolId, KSAnnotated>()

    private val filesById = linkedMapOf<LsiSymbolId, KSFile>()

    fun index(
        allValidRoots: List<Pair<KSClassDeclaration, KSFile>>,
        currentValidRoots: List<Pair<KSClassDeclaration, KSFile>>,
        invalidRoots: List<KSClassDeclaration>,
        allSourceFiles: List<KSFile>,
        currentSourceFiles: List<KSFile>,
        allValidFileScopes: List<KspLsiFileScopeInput>,
        currentValidFileScopes: List<KspLsiFileScopeInput>,
        invalidFileAnnotationScopes: List<KspLsiFileScopeInput>,
    ): KspLsiRoundSymbols {
        allValidFileScopes.forEach(::indexFileScope)
        allValidRoots.forEach { (root, file) -> indexType(root, file) }
        return KspLsiRoundSymbols(
            allSourceFiles = allSourceFiles,
            currentSourceFiles = currentSourceFiles,
            allValidFileScopes = allValidFileScopes,
            currentValidFileScopes = currentValidFileScopes,
            allValidRootTypes = allValidRoots.map { (root, _) -> root },
            currentValidRootTypes = currentValidRoots.map { (root, _) -> root },
            invalidRootTypes = invalidRoots.toList(),
            invalidFileAnnotationScopes = invalidFileAnnotationScopes,
            annotatedById = annotatedById.toMap(),
            filesById = filesById.toMap(),
        )
    }

    private fun indexFileScope(scope: KspLsiFileScopeInput) {
        index(scope.id, scope.file, scope.file)
    }

    private fun indexType(type: KSClassDeclaration, file: KSFile) {
        val qualifiedName = requireNotNull(type.qualifiedName?.asString()) {
            "KSP LSI type declaration must have a qualified name"
        }
        val typeId = LsiSymbolId.type(qualifiedName)
        index(typeId, type, file)
        type.typeParameters.forEach { parameter ->
            index(LsiSymbolId.typeParameter(typeId, parameter.name.asString()), parameter, file)
        }
        for (declaration in type.declarations) {
            if (declaration !is KSClassDeclaration) {
                continue
            }
            if (declaration.classKind == ClassKind.ENUM_ENTRY) {
                index(
                    LsiSymbolId.enumEntry(typeId, declaration.simpleName.asString()),
                    declaration,
                    file,
                )
            } else {
                indexType(declaration, file)
            }
        }
        type.getDeclaredProperties().forEach { property ->
            index(LsiSymbolId.property(typeId, property.simpleName.asString()), property, file)
        }
        type.getDeclaredFunctions()
            .filterNot(KSFunctionDeclaration::isConstructor)
            .forEach { function -> indexFunction(function, file) }
        type.getConstructors().forEach { constructor -> indexFunction(constructor, file) }
    }

    private fun indexFunction(function: KSFunctionDeclaration, file: KSFile) {
        val callableId = typeContext.toLsiDeclarationId(function, frontendOptions)
        index(callableId, function, file)
        function.typeParameters.forEach { parameter ->
            index(LsiSymbolId.typeParameter(callableId, parameter.name.asString()), parameter, file)
        }
        function.parameters.forEachIndexed { parameterIndex, parameter ->
            val parameterName = parameter.name?.asString()?.takeIf(String::isNotBlank) ?: "p$parameterIndex"
            index(
                LsiSymbolId.parameter(callableId, parameterIndex, parameterName),
                parameter,
                file,
            )
        }
    }

    private fun index(id: LsiSymbolId, symbol: KSAnnotated, file: KSFile) {
        annotatedById[id] = symbol
        filesById[id] = file
    }
}
