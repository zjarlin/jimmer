package org.babyfish.jimmer.dto.compiler

import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTreeWalker

enum class DtoDocumentReferenceKind {
    SUBJECT_TYPE,
    ANNOTATION_TYPE,
    SUPER_TYPE,
    MODEL_TYPE,
    TYPE_USAGE,
    CONFIG_IMPLEMENTATION,
}

/**
 * DTO 文档中的纯语义类型引用，行列坐标均从一开始。
 */
data class DtoDocumentReference(
    val qualifiedName: String,
    val kind: DtoDocumentReferenceKind,
    val line: Int,
    val column: Int,
) : Comparable<DtoDocumentReference> {

    init {
        require(qualifiedName.isNotBlank()) { "DTO document reference name cannot be blank" }
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
        return qualifiedName.compareTo(other.qualifiedName)
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
        resolver.subjectType?.let { subjectType ->
            references += DtoDocumentReference(
                qualifiedName = subjectType,
                kind = DtoDocumentReferenceKind.SUBJECT_TYPE,
                line = ast.exportStatement()?.start?.line ?: 1,
                column = (ast.exportStatement()?.start?.charPositionInLine ?: 0) + 1,
            )
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

    override fun enterDtoType(context: DtoParser.DtoTypeContext) {
        context.superInterfaces.forEach { typeRef -> collectTypeRef(typeRef, DtoDocumentReferenceKind.SUPER_TYPE) }
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
            resolver.resolveMacroModelType(argument.parts)?.let { qualifiedName ->
                collect(
                    qualifiedName = qualifiedName,
                    token = argument.parts.first(),
                    kind = DtoDocumentReferenceKind.MODEL_TYPE,
                )
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
        val qualifiedName = resolver.resolve(parts) ?: return
        collect(qualifiedName, parts.first(), kind)
    }

    private fun collect(
        qualifiedName: String,
        token: Token,
        kind: DtoDocumentReferenceKind,
    ) {
        references += DtoDocumentReference(
            qualifiedName = qualifiedName,
            kind = kind,
            line = token.line,
            column = token.charPositionInLine + 1,
        )
    }
}

private class DtoDocumentNameResolver(
    private val dtoFile: DtoFile,
    ast: DtoParser.DtoContext,
) {
    private val importedTypes = buildMap {
        for (statement in ast.importStatements) {
            val path = statement.parts.qualifiedNameOrNull() ?: continue
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

    val subjectType: String? = resolveSubjectType(ast)

    private val subjectPackage = subjectType?.substringBeforeLast('.', "")

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

    fun resolve(parts: List<Token>): String? {
        val name = parts.qualifiedNameOrNull() ?: return null
        if (name == "this" || name in TypeRef.TNS_WITH_DEFAULT_VALUE) {
            return null
        }
        val firstPart = parts.first().identifierOrNull() ?: return null
        val importedType = importedTypes[firstPart]
        if (importedType != null) {
            return importedType + name.removePrefix(firstPart)
        }
        if (firstPart.first().isLowerCase()) {
            return name
        }
        return qualify(subjectPackage ?: return null, name)
    }

    fun resolveMacroModelType(parts: List<Token>): String? {
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
