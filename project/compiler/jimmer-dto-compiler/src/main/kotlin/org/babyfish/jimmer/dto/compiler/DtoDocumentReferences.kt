package org.babyfish.jimmer.dto.compiler

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTreeWalker

enum class DtoDocumentReferenceKind {
    SUBJECT_TYPE,
    TARGET_TYPE,
    ANNOTATION_TYPE,
    SUPER_TYPE,
    MODEL_TYPE,
    REUSABLE_DTO_TYPE,
    TYPE_USAGE,
    CONFIG_IMPLEMENTATION,
}

/**
 * DTO 文档中的纯语义类型引用，行列坐标均从一开始。
 */
data class DtoDocumentReference(
    val typeSelector: DtoTypeNameSelector,
    val kind: DtoDocumentReferenceKind,
    val ownerTargetSelector: DtoTypeNameSelector?,
    val line: Int,
    val column: Int,
) : Comparable<DtoDocumentReference> {

    init {
        require(line >= 1) { "DTO document reference line must be positive: $line" }
        require(column >= 1) { "DTO document reference column must be positive: $column" }
    }

    override fun compareTo(other: DtoDocumentReference): Int {
        val lineComparison = line.compareTo(other.line)
        if (lineComparison != 0) {
            return lineComparison
        }
        val columnComparison = column.compareTo(other.column)
        if (columnComparison != 0) {
            return columnComparison
        }
        val kindComparison = kind.compareTo(other.kind)
        if (kindComparison != 0) {
            return kindComparison
        }
        val selectorComparison = typeSelector.compareTo(other.typeSelector)
        if (selectorComparison != 0) {
            return selectorComparison
        }
        return compareValues(ownerTargetSelector, other.ownerTargetSelector)
    }
}

/**
 * 只提取 DTO 中的类型引用，语法和语义错误仍由正式 DTO 编译阶段报告。
 */
object DtoDocumentReferences {

    @JvmStatic
    fun parse(dtoFile: DtoFile): List<DtoDocumentReference> {
        val ast = dtoFile.openReader().use { reader ->
            val lexer = DtoLexer(CharStreams.fromReader(reader))
            lexer.removeErrorListeners()
            val parser = DtoParser(CommonTokenStream(lexer))
            parser.removeErrorListeners()
            parser.dto()
        }
        val resolver = DtoDocumentNameResolver(dtoFile, ast)
        val references = mutableListOf<DtoDocumentReference>()
        if (ast.hasImplicitTargetDeclaration()) {
            resolver.subjectTypeSelector?.let { subjectTypeSelector ->
                references += DtoDocumentReference(
                    typeSelector = subjectTypeSelector,
                    kind = DtoDocumentReferenceKind.SUBJECT_TYPE,
                    ownerTargetSelector = subjectTypeSelector,
                    line = ast.exportStatement()?.start?.line ?: 1,
                    column = (ast.exportStatement()?.start?.charPositionInLine ?: 0) + 1,
                )
            }
        }
        ParseTreeWalker.DEFAULT.walk(
            DtoDocumentReferenceListener(resolver, references),
            ast,
        )
        return references.distinct().sorted()
    }
}

private class DtoDocumentReferenceListener(
    private val resolver: DtoDocumentNameResolver,
    private val references: MutableList<DtoDocumentReference>,
) : DtoBaseListener() {

    private var currentOwnerTargetSelector: DtoTypeNameSelector? = null

    override fun enterDtoType(context: DtoParser.DtoTypeContext) {
        currentOwnerTargetSelector = resolveOwnerTarget(context.targetType)
        context.targetType?.let { targetType ->
            collect(targetType, DtoDocumentReferenceKind.TARGET_TYPE)
        }
        context.superInterfaces.forEach { typeRef -> collectTypeRef(typeRef, DtoDocumentReferenceKind.SUPER_TYPE) }
    }

    override fun exitDtoType(context: DtoParser.DtoTypeContext) {
        currentOwnerTargetSelector = null
    }

    override fun enterDtoFragment(context: DtoParser.DtoFragmentContext) {
        currentOwnerTargetSelector = resolveOwnerTarget(context.targetType)
        context.targetType?.let { targetType ->
            collect(targetType, DtoDocumentReferenceKind.TARGET_TYPE)
        }
    }

    override fun exitDtoFragment(context: DtoParser.DtoFragmentContext) {
        currentOwnerTargetSelector = null
    }

    override fun enterDefaultBranch(context: DtoParser.DefaultBranchContext) {
        context.superInterfaces.forEach { typeRef -> collectTypeRef(typeRef, DtoDocumentReferenceKind.SUPER_TYPE) }
    }

    override fun enterTypeBranch(context: DtoParser.TypeBranchContext) {
        context.targetType?.let { targetType ->
            collect(targetType, DtoDocumentReferenceKind.MODEL_TYPE)
        }
        context.superInterfaces.forEach { typeRef -> collectTypeRef(typeRef, DtoDocumentReferenceKind.SUPER_TYPE) }
    }

    override fun enterFoldProp(context: DtoParser.FoldPropContext) {
        context.bodySuperInterfaces.forEach { typeRef ->
            collectTypeRef(typeRef, DtoDocumentReferenceKind.SUPER_TYPE)
        }
    }

    override fun enterPositiveProp(context: DtoParser.PositivePropContext) {
        context.referencedType?.let { referencedType ->
            collectDtoType(referencedType, DtoDocumentReferenceKind.REUSABLE_DTO_TYPE)
        }
        context.bodySuperInterfaces.forEach { typeRef ->
            collectTypeRef(typeRef, DtoDocumentReferenceKind.SUPER_TYPE)
        }
    }

    override fun enterUserProp(context: DtoParser.UserPropContext) {
        context.typeRef()?.let { typeRef ->
            collectTypeRef(typeRef, DtoDocumentReferenceKind.TYPE_USAGE)
        }
    }

    override fun enterMacro(context: DtoParser.MacroContext) {
        context.args.forEach { argument ->
            resolver.resolveMacroModelType(argument.parts)?.let { selector ->
                collect(selector, argument.parts.first(), DtoDocumentReferenceKind.MODEL_TYPE)
            }
        }
    }

    override fun enterFilter(context: DtoParser.FilterContext) {
        context.qualifiedName()?.let { typeName ->
            collect(typeName, DtoDocumentReferenceKind.CONFIG_IMPLEMENTATION)
        }
    }

    override fun enterRecursion(context: DtoParser.RecursionContext) {
        context.qualifiedName()?.let { typeName ->
            collect(typeName, DtoDocumentReferenceKind.CONFIG_IMPLEMENTATION)
        }
    }

    override fun enterAnnotation(context: DtoParser.AnnotationContext) {
        context.typeName?.let { typeName ->
            collect(typeName, DtoDocumentReferenceKind.ANNOTATION_TYPE)
        }
    }

    override fun enterNestedAnnotation(context: DtoParser.NestedAnnotationContext) {
        context.typeName?.let { typeName ->
            collect(typeName, DtoDocumentReferenceKind.ANNOTATION_TYPE)
        }
    }

    override fun enterAnnotationSingleValue(context: DtoParser.AnnotationSingleValueContext) {
        val qualifiedPart = context.qualifiedPart ?: return
        if (context.classSuffix() != null) {
            collect(qualifiedPart, DtoDocumentReferenceKind.TYPE_USAGE)
            return
        }
        if (qualifiedPart.parts.size <= 1) {
            return
        }
        collect(
            parts = qualifiedPart.parts.dropLast(1),
            kind = DtoDocumentReferenceKind.TYPE_USAGE,
        )
    }

    private fun collectTypeRef(
        context: DtoParser.TypeRefContext,
        kind: DtoDocumentReferenceKind,
    ) {
        context.qualifiedName()?.let { typeName -> collect(typeName, kind) }
        context.genericArguments.forEach { argument ->
            argument.typeRef()?.let { typeRef ->
                collectTypeRef(typeRef, DtoDocumentReferenceKind.TYPE_USAGE)
            }
        }
    }

    private fun collect(
        context: DtoParser.QualifiedNameContext,
        kind: DtoDocumentReferenceKind,
    ) {
        collect(context.parts, kind)
    }

    private fun collect(
        parts: List<Token>,
        kind: DtoDocumentReferenceKind,
    ) {
        val selector = resolver.resolve(parts) ?: return
        collect(selector, parts.first(), kind)
    }

    private fun collect(
        selector: DtoTypeNameSelector,
        token: Token,
        kind: DtoDocumentReferenceKind,
    ) {
        references += DtoDocumentReference(
            typeSelector = selector,
            kind = kind,
            ownerTargetSelector = currentOwnerTargetSelector,
            line = token.line,
            column = token.charPositionInLine + 1,
        )
    }

    private fun collectDtoType(
        context: DtoParser.QualifiedNameContext,
        kind: DtoDocumentReferenceKind,
    ) {
        val selector = resolver.resolveDtoType(context.parts) ?: return
        collect(selector, context.parts.first(), kind)
    }

    private fun resolveOwnerTarget(
        targetType: DtoParser.QualifiedNameContext?,
    ): DtoTypeNameSelector? {
        return if (targetType != null) {
            resolver.resolve(targetType.parts)
        } else {
            resolver.subjectTypeSelector
        }
    }
}

private fun DtoParser.DtoContext.hasImplicitTargetDeclaration(): Boolean {
    return dtoTypes.any { dtoType -> dtoType.targetType == null } ||
        fragments.any { fragment -> fragment.targetType == null }
}

private class DtoDocumentNameResolver(
    private val dtoFile: DtoFile,
    ast: DtoParser.DtoContext,
) {
    private val wildcardPackageNames = ast.importStatements
        .asSequence()
        .filter { statement -> statement.wildcard != null }
        .mapNotNull { statement -> statement.parts.qualifiedNameOrNull() }
        .distinct()
        .toList()

    private val importedTypes = buildMap {
        for (statement in ast.importStatements) {
            val path = statement.parts.qualifiedNameOrNull() ?: continue
            if (statement.wildcard != null) {
                continue
            }
            if (statement.alias != null) {
                val alias = statement.alias.identifierOrNull() ?: continue
                put(alias, path)
                continue
            }
            if (statement.importedTypes.isNotEmpty()) {
                for (importedType in statement.importedTypes) {
                    val name = importedType.name?.identifierOrNull() ?: continue
                    val alias = importedType.alias?.identifierOrNull()
                        ?: if (importedType.alias == null) name else continue
                    put(alias, "$path.$name")
                }
                continue
            }
            put(statement.parts.last().identifierOrNull() ?: continue, path)
        }
    }

    private val subjectType: String? = resolveSubjectType(ast)

    val subjectTypeSelector: DtoTypeNameSelector? = subjectType?.let { qualifiedName ->
        DtoTypeNameSelector.exact(
            qualifiedName = qualifiedName,
            sourceName = resolveSubjectSourceName(ast) ?: qualifiedName,
        )
    }

    private val subjectPackage = subjectType?.substringBeforeLast('.', "")

    private val dtoPackageName = resolveDtoPackageName(ast)

    private fun resolveSubjectType(ast: DtoParser.DtoContext): String? {
        val export = ast.exportStatement()
        if (export != null) {
            val exportedName = export.typeParts.qualifiedNameOrNull() ?: return null
            if (export.typeParts.size > 1) {
                return exportedName
            }
            return qualify(dtoFile.packageName, exportedName)
        }
        val modelName = dtoFile.name.removeSuffix(".dto")
            .takeIf(DTO_IDENTIFIER_PATTERN::matches)
            ?: return null
        return qualify(dtoFile.packageName, modelName)
    }

    private fun resolveSubjectSourceName(ast: DtoParser.DtoContext): String? {
        return ast.exportStatement()?.typeParts?.qualifiedNameOrNull()
            ?: dtoFile.name.removeSuffix(".dto").takeIf(DTO_IDENTIFIER_PATTERN::matches)
    }

    fun resolve(parts: List<Token>): DtoTypeNameSelector? {
        val name = parts.qualifiedNameOrNull() ?: return null
        if (name == "this" || name in TypeRef.TNS_WITH_DEFAULT_VALUE) {
            return null
        }
        val defaultPackageName = subjectPackage
        if (defaultPackageName == null && !name.canResolveWithoutDefaultPackage()) {
            return null
        }
        return DtoTypeNameSelector.plan(
            name,
            defaultPackageName.orEmpty(),
            importedTypes,
            wildcardPackageNames,
        )
    }

    fun resolveDtoType(parts: List<Token>): DtoTypeNameSelector? {
        val name = parts.qualifiedNameOrNull() ?: return null
        return DtoTypeNameSelector.plan(
            name,
            dtoPackageName ?: return null,
            importedTypes,
            wildcardPackageNames,
        )
    }

    fun resolveMacroModelType(parts: List<Token>): DtoTypeNameSelector? {
        val name = parts.qualifiedNameOrNull() ?: return null
        if (name == "this") {
            return null
        }
        val firstPart = parts.first().identifierOrNull() ?: return null
        if (parts.size == 1 && firstPart !in importedTypes) {
            return null
        }
        return resolve(parts)
    }

    private fun String.canResolveWithoutDefaultPackage(): Boolean {
        val separatorIndex = indexOf('.')
        val firstPart = if (separatorIndex == -1) this else substring(0, separatorIndex)
        return firstPart in importedTypes || Character.isLowerCase(first())
    }

    private fun resolveDtoPackageName(ast: DtoParser.DtoContext): String? {
        val packageStatement = ast.packageStatement()
        if (packageStatement != null) {
            return packageStatement.packageParts.qualifiedNameOrNull()
        }
        val explicitPackage = ast.exportStatement()?.packageParts?.qualifiedNameOrNull()
        if (explicitPackage != null) {
            return explicitPackage
        }
        val basePackageName = subjectPackage ?: return null
        return if (basePackageName.isEmpty()) "dto" else "$basePackageName.dto"
    }

    private fun qualify(packageName: String, name: String): String {
        return if (packageName.isEmpty()) name else "$packageName.$name"
    }
}

private val DTO_IDENTIFIER_PATTERN = Regex("[\$A-Za-z_][\$A-Za-z_0-9]*")

private fun Token.identifierOrNull(): String? {
    return text?.takeIf(DTO_IDENTIFIER_PATTERN::matches)
}

private fun List<Token>.qualifiedNameOrNull(): String? {
    if (isEmpty()) {
        return null
    }
    val identifiers = ArrayList<String>(size)
    for (token in this) {
        identifiers += token.identifierOrNull() ?: return null
    }
    return identifiers.joinToString(".")
}
