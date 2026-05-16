package site.addzero.lsi.jimmer.tuple.metadata.extractor

import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.annotation
import site.addzero.lsi.codegen.LsiClassName
import site.addzero.lsi.diagnostic.LsiDiagnosticAnchor
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.jimmer.TYPED_TUPLE
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleConstructionMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleConstructorConstructionMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTuplePropertyMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleSetterConstructionMetadata
import site.addzero.lsi.jimmer.tuple.metadata.model.TypedTupleTypeRefMetadata
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.poet.isLsiObjectLikeQualifiedName
import site.addzero.lsi.resolver.LsiResolver
import site.addzero.lsi.type.LsiType

/**
 * TypedTuple metadata 提取器。
 *
 * 当前目标：
 * - 统一 KSP data class 与 APT Java bean/Lombok tuple 的合法性校验
 * - 把“如何创建 tuple 实例”抽成 construction metadata
 * - generator 只消费纯 metadata，不再感知 KSP/APT 细节
 */
class TypedTupleMetadataExtractor {

    fun collectRoundTypes(
        resolver: LsiResolver,
        delayedTypeNames: Collection<String>? = null,
    ): TypedTupleMetadataExtraction =
        collectRoundTypes(
            resolver = resolver,
            delayedTypeNames = delayedTypeNames,
            include = { true },
        )

    fun collectRoundTypes(
        resolver: LsiResolver,
        delayedTypeNames: Collection<String>? = null,
        include: (LsiClass) -> Boolean = { true },
    ): TypedTupleMetadataExtraction {
        val declarationsById = linkedMapOf<String, LsiClass>()
        for (declaration in resolver.findClassesAnnotatedWith(TYPED_TUPLE)) {
            if (!include(declaration)) {
                continue
            }
            declarationsById.putIfAbsent(declaration.collectionKey(), declaration)
        }
        for (delayedTypeName in delayedTypeNames.orEmpty()) {
            val declaration = resolver.findClassByQualifiedName(delayedTypeName)
                ?: continue
            if (!include(declaration)) {
                continue
            }
            // 覆盖来源：project/jimmer-apt/.../tuple/TypedTupleProcessor.process 的 delayed 名称回放
            // 覆盖来源：project/compiler/tuple/jimmer-ksp-tuple/.../TypedTupleProcessor.onRound 的 delayed lookup
            // 迁移说明：当前轮注解扫描 + delayed type 回补统一收口到 extractor，APT/KSP processor 不再各自拼装声明列表
            declarationsById.putIfAbsent(declaration.collectionKey(), declaration)
        }
        return extract(declarationsById.values.asSequence())
    }

    fun collectAllTypes(
        resolver: LsiResolver,
        include: (LsiClass) -> Boolean = { true },
    ): TypedTupleMetadataExtraction =
        extract(
            resolver.allClasses().filter { declaration ->
                declaration.annotation(TYPED_TUPLE) != null && include(declaration)
            }
        )

    fun extract(
        declarations: Iterable<LsiClass>,
    ): TypedTupleMetadataExtraction =
        extract(declarations.asSequence())

    fun extract(
        declarations: Sequence<LsiClass>,
    ): TypedTupleMetadataExtraction {
        val typeMetadatas = mutableListOf<TypedTupleMetadata>()
        val anchorsById = linkedMapOf<String, LsiDiagnosticAnchor>()
        for (declaration in declarations) {
            if (declaration.annotation(TYPED_TUPLE) == null) {
                continue
            }
            typeMetadatas += extractType(
                declaration = declaration,
                anchorsById = anchorsById,
            )
        }
        return TypedTupleMetadataExtraction(
            types = typeMetadatas,
            sourceIndex = TypedTupleMetadataSourceIndex(anchorsById),
        )
    }

    private fun extractType(
        declaration: LsiClass,
        anchorsById: MutableMap<String, LsiDiagnosticAnchor>,
    ): TypedTupleMetadata {
        val sourceQualifiedName = declaration.qualifiedName
            ?: throw MetaException(
                declaration,
                "The type decorated by @$TYPED_TUPLE must be top-level class",
            )
        validateType(declaration)
        val sourceSimpleName = declaration.simpleName
            ?: sourceQualifiedName.substringAfterLast('.')
        val typeId = sourceQualifiedName
        anchorsById.putIfAbsent(typeId, classAnchor(declaration))
        val packageName = sourceQualifiedName.substringBeforeLast('.', "")
        val generatedSimpleName = "${sourceSimpleName}Mapper"
        val generatedQualifiedName =
            if (packageName.isNotEmpty()) {
                "$packageName.$generatedSimpleName"
            } else {
                generatedSimpleName
            }
        val properties = extractProperties(
            ownerId = typeId,
            declaration = declaration,
            anchorsById = anchorsById,
        )
        return TypedTupleMetadata(
            id = typeId,
            sourceSimpleName = sourceSimpleName,
            sourceQualifiedName = sourceQualifiedName,
            sourceClassName = LsiClassName.bestGuess(sourceQualifiedName),
            packageName = packageName,
            generatedSimpleName = generatedSimpleName,
            generatedQualifiedName = generatedQualifiedName,
            generatedClassName = LsiClassName.bestGuess(generatedQualifiedName),
            construction = determineConstruction(declaration, properties),
            properties = properties,
        )
    }

    private fun validateType(
        declaration: LsiClass,
    ) {
        if (!declaration.isClass) {
            throw MetaException(
                declaration,
                "The type decorated by @$TYPED_TUPLE must be class",
            )
        }
        if (!declaration.isTopLevel) {
            throw MetaException(
                declaration,
                "The type decorated by @$TYPED_TUPLE must be top-level class",
            )
        }
        if (declaration.superClasses.any { !it.isRootObjectLike() }) {
            throw MetaException(
                declaration,
                "The type decorated by @$TYPED_TUPLE cannot inherit other class",
            )
        }
        if (declaration.typeParameterCount > 0) {
            throw MetaException(
                declaration,
                "The type decorated by @$TYPED_TUPLE cannot be generic type",
            )
        }
    }

    private fun determineConstruction(
        declaration: LsiClass,
        properties: List<TypedTuplePropertyMetadata>,
    ): TypedTupleConstructionMetadata {
        val allPropertyIndices = properties.indices.toList()
        when (findLombokConstructionKind(declaration, properties)) {
            LombokConstructionKind.CONSTRUCTOR ->
                return TypedTupleConstructorConstructionMetadata(allPropertyIndices)
            LombokConstructionKind.SETTER ->
                return TypedTupleSetterConstructionMetadata(properties.map { setterName(it.name) })
            LombokConstructionKind.NONE -> {}
        }
        findConstructorPropertyIndices(declaration, properties)?.let { argumentPropertyIndices ->
            return TypedTupleConstructorConstructionMetadata(argumentPropertyIndices)
        }
        if (hasDefaultConstructor(declaration)) {
            return TypedTupleSetterConstructionMetadata(properties.map { setterName(it.name) })
        }
        throw MetaException(
            declaration,
            "it is decorated by @$TYPED_TUPLE, but there is neither default constructor nor constructor with full arguments",
        )
    }

    private fun findLombokConstructionKind(
        declaration: LsiClass,
        properties: List<TypedTuplePropertyMetadata>,
    ): LombokConstructionKind {
        if (declaration.annotation(LOMBOK_BUILDER) != null) {
            throw MetaException(
                declaration,
                "it is decorated by @$TYPED_TUPLE, so it cannot be decorated by @$LOMBOK_BUILDER",
            )
        }
        if (declaration.annotation(LOMBOK_ALL_ARGS_CONSTRUCTOR) != null) {
            return LombokConstructionKind.CONSTRUCTOR
        }
        if (declaration.annotation(LOMBOK_NO_ARGS_CONSTRUCTOR) != null) {
            return LombokConstructionKind.SETTER
        }
        if (declaration.annotation(LOMBOK_DATA) == null) {
            return LombokConstructionKind.NONE
        }
        var finality: Boolean? = null
        for (property in properties) {
            val field = declaration.fields.firstOrNull { it.name == property.name }
                ?: continue
            val currentFinality = !field.isVar
            if (finality != null && finality != currentFinality) {
                throw MetaException(
                    declaration,
                    "it is decorated by both @$TYPED_TUPLE and @$LOMBOK_DATA, so it cannot mix final fields and non-final fields",
                )
            }
            finality = currentFinality
        }
        return if (finality != false) {
            LombokConstructionKind.CONSTRUCTOR
        } else {
            LombokConstructionKind.SETTER
        }
    }

    private fun findConstructorPropertyIndices(
        declaration: LsiClass,
        properties: List<TypedTuplePropertyMetadata>,
    ): List<Int>? {
        val propertiesByName = properties.withIndex().associateBy { it.value.name }
        val constructors = buildList {
            declaration.primaryConstructor?.let { add(it) }
            addAll(declaration.constructors)
        }.distinctBy { constructor ->
            constructor.parameters.joinToString("|") { parameter ->
                "${parameter.name}:${parameter.type.normalizedSignature()}"
            }
        }
        for (constructor in constructors) {
            if (constructor.isPrivate || constructor.parameters.size != properties.size) {
                continue
            }
            val matchedIndices = mutableListOf<Int>()
            var matched = true
            for (parameter in constructor.parameters) {
                val parameterName = parameter.name
                val propertyWithIndex = propertiesByName[parameterName]
                val propertyType = propertyWithIndex?.value?.type
                if (propertyWithIndex == null || !sameType(propertyType, parameter.type)) {
                    matched = false
                    break
                }
                matchedIndices += propertyWithIndex.index
            }
            if (matched) {
                return matchedIndices
            }
        }
        return null
    }

    private fun hasDefaultConstructor(
        declaration: LsiClass,
    ): Boolean {
        val constructors = declaration.constructors.filterNot { it.isPrivate }
        if (constructors.isEmpty()) {
            return true
        }
        return constructors.any { it.parameters.isEmpty() || it.parameters.all { parameter -> parameter.hasDefault } }
    }

    private fun extractProperties(
        ownerId: String,
        declaration: LsiClass,
        anchorsById: MutableMap<String, LsiDiagnosticAnchor>,
    ): List<TypedTuplePropertyMetadata> {
        val ownerQualifiedName = declaration.qualifiedName
        val fields = declaration.fields.filter { field ->
            val declaringQualifiedName = field.declaringClass?.qualifiedName
            !field.isStatic &&
                (declaringQualifiedName == null || declaringQualifiedName == ownerQualifiedName)
        }
        if (fields.isEmpty()) {
            throw MetaException(
                declaration,
                "There is no non-static property",
            )
        }
        return fields.map { field ->
            val name = field.name
                ?: throw MetaException(field, "Tuple property name cannot be null")
            val fieldId = "$ownerId::$name"
            anchorsById.putIfAbsent(fieldId, fieldAnchor(declaration, field))
            TypedTuplePropertyMetadata(
                id = fieldId,
                ownerTypeId = ownerId,
                name = name,
                type = field.type?.toMetadata(),
            )
        }
    }

    private fun LsiType.toMetadata(): TypedTupleTypeRefMetadata =
        TypedTupleTypeRefMetadata(
            qualifiedName = qualifiedName,
            simpleName = simpleName,
            presentableText = presentableText,
            nullable = isNullable,
            primitive = isPrimitive,
            array = isArray,
            typeArguments = typeParameters.map { it.toMetadata() },
            componentType = componentType?.toMetadata(),
        )

    private fun sameType(
        propertyType: TypedTupleTypeRefMetadata?,
        parameterType: LsiType?,
    ): Boolean =
        propertyType.normalizedSignature() == parameterType.normalizedSignature()

    private fun TypedTupleTypeRefMetadata?.normalizedSignature(): String =
        when (this) {
            null -> "<unknown>"
            else -> (qualifiedName ?: presentableText ?: simpleName ?: "<unknown>")
                .substringBefore('<')
                .removeSuffix("?")
                .removeSuffix("!")
        }

    private fun LsiType?.normalizedSignature(): String =
        when (this) {
            null -> "<unknown>"
            else -> (qualifiedName ?: presentableText ?: simpleName ?: "<unknown>")
                .substringBefore('<')
                .removeSuffix("?")
                .removeSuffix("!")
        }

    private fun LsiClass.isRootObjectLike(): Boolean =
        qualifiedName.isLsiObjectLikeQualifiedName() || simpleName.isLsiObjectLikeQualifiedName()

    private fun setterName(propertyName: String): String =
        identifier("set", propertyName)

    private fun identifier(vararg parts: String): String {
        val builder = StringBuilder()
        var previousPartEndsWithLower = false
        for (part in parts) {
            if (part.isEmpty()) {
                continue
            }
            if (previousPartEndsWithLower) {
                if (part[0].isUpperCase()) {
                    builder.append(part)
                } else {
                    builder.append(part[0].uppercaseChar()).append(part.substring(1))
                }
            } else {
                if (part[0].isLowerCase()) {
                    builder.append(part)
                } else {
                    val chars = part.toCharArray()
                    for (index in chars.indices) {
                        if (chars[index].isLowerCase()) {
                            break
                        }
                        chars[index] = chars[index].lowercaseChar()
                    }
                    builder.append(chars)
                }
            }
            previousPartEndsWithLower = part.last().isLowerCase()
        }
        return builder.toString()
    }

    private fun classAnchor(
        declaration: LsiClass,
    ): LsiDiagnosticAnchor =
        SimpleDiagnosticAnchor(
            kind = LsiDiagnosticAnchor.Kind.CLASS,
            ownerQualifiedName = declaration.qualifiedName,
            symbolName = declaration.simpleName,
        )

    private fun fieldAnchor(
        declaration: LsiClass,
        field: LsiField,
    ): LsiDiagnosticAnchor =
        SimpleDiagnosticAnchor(
            kind = LsiDiagnosticAnchor.Kind.FIELD,
            ownerQualifiedName = declaration.qualifiedName,
            symbolName = field.name,
        )

    private data class SimpleDiagnosticAnchor(
        override val kind: LsiDiagnosticAnchor.Kind,
        override val ownerQualifiedName: String?,
        override val symbolName: String?,
    ) : LsiDiagnosticAnchor

    private enum class LombokConstructionKind {
        NONE,
        CONSTRUCTOR,
        SETTER,
    }

    private fun LsiClass.collectionKey(): String =
        qualifiedName
            ?: simpleName
            ?: error("TypedTuple declaration must have qualifiedName or simpleName for collection")

    companion object {
        private const val LOMBOK_BUILDER = "lombok.Builder"
        private const val LOMBOK_ALL_ARGS_CONSTRUCTOR = "lombok.AllArgsConstructor"
        private const val LOMBOK_NO_ARGS_CONSTRUCTOR = "lombok.NoArgsConstructor"
        private const val LOMBOK_DATA = "lombok.Data"
    }
}

data class TypedTupleMetadataExtraction(
    val types: List<TypedTupleMetadata>,
    val sourceIndex: TypedTupleMetadataSourceIndex,
)

data class TypedTupleMetadataSourceIndex(
    val anchorsById: Map<String, LsiDiagnosticAnchor>,
) {
    fun anchorOf(id: String): LsiDiagnosticAnchor? =
        anchorsById[id]

    fun merge(other: TypedTupleMetadataSourceIndex): TypedTupleMetadataSourceIndex =
        if (anchorsById.isEmpty()) {
            other
        } else if (other.anchorsById.isEmpty()) {
            this
        } else {
            TypedTupleMetadataSourceIndex(
                LinkedHashMap(anchorsById).apply {
                    putAll(other.anchorsById)
                },
            )
        }

    companion object {
        fun empty(): TypedTupleMetadataSourceIndex =
            TypedTupleMetadataSourceIndex(emptyMap())
    }
}
