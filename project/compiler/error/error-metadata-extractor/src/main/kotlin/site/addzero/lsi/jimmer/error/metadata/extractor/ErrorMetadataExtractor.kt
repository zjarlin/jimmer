package site.addzero.lsi.jimmer.error.metadata.extractor

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.get
import site.addzero.lsi.anno.getClassArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.LsiEnumConstant
import site.addzero.lsi.clazz.annotation
import site.addzero.lsi.diagnostic.LsiDiagnosticAnchor
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.jimmer.ERROR_FAMILY
import site.addzero.lsi.jimmer.ERROR_FIELD
import site.addzero.lsi.jimmer.ERROR_FIELDS
import site.addzero.lsi.jimmer.error.metadata.model.ErrorFieldMetadata
import site.addzero.lsi.jimmer.error.metadata.model.ErrorItemMetadata
import site.addzero.lsi.jimmer.error.metadata.model.ErrorTypeMetadata
import site.addzero.lsi.poet.isLsiPrimitiveLikeQualifiedName
import site.addzero.lsi.resolver.LsiResolver

/**
 * Error metadata 提取器。
 *
 * 覆盖来源：
 * - `project/compiler/error/jimmer-ksp-error/.../ErrorProcessor.findErrorTypes`
 * - `project/compiler/error/jimmer-ksp-error/.../ErrorGenerator`
 *
 * 迁移说明：将 error 侧“扫描 + 校验 + 命名推导 + 字段解析”前移到 extractor，
 * generator 只消费纯 metadata，不再直接持有 `LsiClass` 大对象。
 */
class ErrorMetadataExtractor {

    fun collectNewTypes(
        resolver: LsiResolver,
        include: (LsiClass) -> Boolean = { true }
    ): ErrorMetadataExtraction =
        extract(
            resolver.newClasses().filter { declaration ->
                declaration.annotation(ERROR_FAMILY) != null && include(declaration)
            }
        )

    fun extract(
        declarations: Sequence<LsiClass>
    ): ErrorMetadataExtraction {
        val typeMetadatas = mutableListOf<ErrorTypeMetadata>()
        val anchorsById = linkedMapOf<String, LsiDiagnosticAnchor>()
        for (declaration in declarations) {
            if (!declaration.isEnum) {
                throw MetaException(
                    declaration,
                    "Only enum can be decorated by @$ERROR_FAMILY"
                )
            }
            val metadata = extractType(declaration, anchorsById)
            typeMetadatas += metadata
        }
        return ErrorMetadataExtraction(
            types = typeMetadatas,
            sourceIndex = ErrorMetadataSourceIndex(anchorsById)
        )
    }

    private fun extractType(
        declaration: LsiClass,
        anchorsById: MutableMap<String, LsiDiagnosticAnchor>
    ): ErrorTypeMetadata {
        val enumSimpleName = declaration.simpleName
            ?: throw MetaException(declaration, "Error type must have simple name")
        val enumQualifiedName = declaration.qualifiedName
            ?: throw MetaException(declaration, "Error type must have qualified name")
        val typeId = enumQualifiedName
        anchorsById.putIfAbsent(typeId, classAnchor(declaration))
        val packageName = enumQualifiedName.substringBeforeLast('.', "")
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.family
        // 迁移说明：错误族推导前移到 metadata extractor，generator 不再自行读取 `LsiClass` 注解
        val family = declaration.annotation(ERROR_FAMILY)
            ?.get<String>(VALUE_ATTRIBUTE)
            ?.takeIf { it.isNotEmpty() }
            ?: snake(baseName(enumSimpleName))
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.exceptionSimpleName
        // 迁移说明：异常类型命名前移到 metadata extractor，generator 只消费纯字符串
        val exceptionSimpleName = "${baseName(enumSimpleName)}Exception"
        val exceptionQualifiedName =
            if (packageName.isEmpty()) {
                exceptionSimpleName
            } else {
                "$packageName.$exceptionSimpleName"
            }
        val declaredFields = extractDeclaredFields(
            ownerId = typeId,
            owner = declaration,
            annotations = declaration.annotations,
            anchorsById = anchorsById,
            onReservedName = { name ->
                throw MetaException(
                    declaration,
                    "The enum \"$enumQualifiedName\" is illegal, it cannot be decorated by \"@$ERROR_FAMILY\" with the name \"$name\""
                )
            },
            onDuplicateName = { name ->
                throw MetaException(declaration, "Duplicate field \"$name\"")
            }
        )
        val sharedFieldNames = declaredFields.mapTo(linkedSetOf()) { it.name }
        val items = declaration.enumConstants.map { item ->
            extractItem(
                typeId = typeId,
                declaration = declaration,
                item = item,
                sharedFieldNames = sharedFieldNames,
                anchorsById = anchorsById
            )
        }
        return ErrorTypeMetadata(
            id = typeId,
            enumSimpleName = enumSimpleName,
            enumQualifiedName = enumQualifiedName,
            packageName = packageName,
            family = family,
            exceptionSimpleName = exceptionSimpleName,
            exceptionQualifiedName = exceptionQualifiedName,
            doc = declaration.comment?.takeIf { it.isNotBlank() },
            declaredFields = declaredFields,
            items = items
        )
    }

    private fun extractItem(
        typeId: String,
        declaration: LsiClass,
        item: LsiEnumConstant,
        sharedFieldNames: Set<String>,
        anchorsById: MutableMap<String, LsiDiagnosticAnchor>
    ): ErrorItemMetadata {
        val constantName = item.name
            ?: throw MetaException(item, "Cannot resolve enum constant name")
        val itemId = "$typeId#$constantName"
        anchorsById.putIfAbsent(itemId, enumConstantAnchor(item))
        val localFields = extractDeclaredFields(
            ownerId = itemId,
            owner = item,
            annotations = item.annotations,
            anchorsById = anchorsById,
            onReservedName = { name ->
                throw MetaException(
                    item,
                    "The enum constant \"" +
                        (declaration.qualifiedName ?: declaration.simpleName ?: "<unknown>") +
                        ".$constantName\" is illegal, it cannot be decorated by \"@$ERROR_FAMILY\" with the name \"$name\""
                )
            },
            onDuplicateName = { name ->
                throw MetaException(item, "Duplicate field \"$name\"")
            }
        )
        // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.fieldsOf(enumConstant)
        // 迁移说明：共享字段与局部字段重名校验前移到 metadata extractor，generator 不再处理 `LsiEnumConstant`
        for (field in localFields) {
            if (field.name in sharedFieldNames) {
                throw MetaException(
                    item,
                    "The field \"${field.name}\" has already been defined in enum \"" +
                        (declaration.qualifiedName ?: declaration.simpleName ?: "<unknown>") +
                        "\""
                )
            }
        }
        return ErrorItemMetadata(
            id = itemId,
            ownerTypeId = typeId,
            enumConstantName = constantName,
            exceptionSimpleName = ktName(constantName, upperHead = true),
            code = snake(constantName),
            doc = item.comment?.takeIf { it.isNotBlank() },
            declaredFields = localFields
        )
    }

    private fun extractDeclaredFields(
        ownerId: String,
        owner: Any,
        annotations: List<LsiAnnotation>,
        anchorsById: MutableMap<String, LsiDiagnosticAnchor>,
        onReservedName: (String) -> Nothing,
        onDuplicateName: (String) -> Nothing
    ): List<ErrorFieldMetadata> {
        val fields = mutableListOf<ErrorFieldMetadata>()
        val usedNames = linkedSetOf<String>()
        for (annotation in expandErrorFieldAnnotations(annotations)) {
            // 覆盖来源：project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.toFieldMap
            // 迁移说明：ErrorField 解析前移到 metadata extractor，generator 只消费字段元数据
            val name = annotation.get<String>(NAME_ATTRIBUTE)
                ?: throw IllegalStateException("@$ERROR_FIELD.$NAME_ATTRIBUTE must exist")
            if (name == "family" || name == "code") {
                onReservedName(name)
            }
            if (!usedNames.add(name)) {
                onDuplicateName(name)
            }
            val typeName = annotation.getClassArgument(TYPE_ATTRIBUTE)
                ?.qualifiedName
                ?: throw IllegalStateException("@$ERROR_FIELD.$TYPE_ATTRIBUTE must exist")
            if (annotation.get<Boolean>(LIST_ATTRIBUTE) == true && typeName.isLsiPrimitiveLikeQualifiedName()) {
                // 覆盖来源：project/jimmer-apt/.../error/ErrorGenerator.Field.of primitive-list legality check
                // 迁移说明：原先留在 Java generator 的 `primitive + list=true` 非法性前移到 shared metadata extractor，
                // 避免 APT/KSP metadata-first 入口在生成阶段重新依赖平台注解对象做校验
                throw primitiveListMetaException(owner)
            }
            val fieldId = "$ownerId::$name"
            anchorsById.putIfAbsent(fieldId, fieldAnchor(owner, name))
            fields += ErrorFieldMetadata(
                id = fieldId,
                ownerId = ownerId,
                name = name,
                typeName = typeName,
                nullable = annotation.get<Boolean>(NULLABLE_ATTRIBUTE) == true,
                list = annotation.get<Boolean>(LIST_ATTRIBUTE) == true,
                doc = annotation.get<String>(DOC_ATTRIBUTE)?.takeIf { it.isNotBlank() }
            )
        }
        return fields
    }

    private fun expandErrorFieldAnnotations(
        annotations: List<LsiAnnotation>
    ): List<LsiAnnotation> =
        buildList {
            for (annotation in annotations) {
                when (annotation.qualifiedName) {
                    ERROR_FIELD -> add(annotation)
                    ERROR_FIELDS -> addAll(
                        ((annotation[VALUE_ATTRIBUTE] as? List<*>)
                            ?.filterIsInstance<LsiAnnotation>())
                            .orEmpty()
                    )
                }
            }
        }

    private fun baseName(simpleName: String): String =
        when {
            simpleName.endsWith("_ErrorCode") -> simpleName.substring(0, simpleName.length - 10)
            simpleName.endsWith("ErrorCode") -> simpleName.substring(0, simpleName.length - 9)
            simpleName.endsWith("_Error") -> simpleName.substring(0, simpleName.length - 6)
            simpleName.endsWith("Error") -> simpleName.substring(0, simpleName.length - 5)
            else -> simpleName
        }

    private fun classAnchor(declaration: LsiClass): LsiDiagnosticAnchor =
        SimpleDiagnosticAnchor(
            kind = LsiDiagnosticAnchor.Kind.CLASS,
            ownerQualifiedName = declaration.qualifiedName,
            symbolName = declaration.simpleName
        )

    private fun enumConstantAnchor(declaration: LsiEnumConstant): LsiDiagnosticAnchor =
        SimpleDiagnosticAnchor(
            kind = LsiDiagnosticAnchor.Kind.FIELD,
            ownerQualifiedName = declaration.declaringClass?.qualifiedName,
            symbolName = declaration.name
        )

    private fun fieldAnchor(owner: Any, fieldName: String): LsiDiagnosticAnchor =
        when (owner) {
            is LsiClass -> SimpleDiagnosticAnchor(
                kind = LsiDiagnosticAnchor.Kind.FIELD,
                ownerQualifiedName = owner.qualifiedName,
                symbolName = fieldName
            )
            is LsiEnumConstant -> SimpleDiagnosticAnchor(
                kind = LsiDiagnosticAnchor.Kind.FIELD,
                ownerQualifiedName = owner.declaringClass?.qualifiedName,
                symbolName = fieldName
            )
            else -> SimpleDiagnosticAnchor(
                kind = LsiDiagnosticAnchor.Kind.UNKNOWN,
                ownerQualifiedName = null,
                symbolName = fieldName
            )
        }

    private data class SimpleDiagnosticAnchor(
        override val kind: LsiDiagnosticAnchor.Kind,
        override val ownerQualifiedName: String?,
        override val symbolName: String?
    ) : LsiDiagnosticAnchor

    companion object {
        private const val VALUE_ATTRIBUTE = "value"
        private const val NAME_ATTRIBUTE = "name"
        private const val TYPE_ATTRIBUTE = "type"
        private const val LIST_ATTRIBUTE = "list"
        private const val NULLABLE_ATTRIBUTE = "nullable"
        private const val DOC_ATTRIBUTE = "doc"

        private fun ktName(simpleName: String, upperHead: Boolean): String {
            val size = simpleName.length
            var toUpper = upperHead
            val builder = StringBuilder()
            for (i in 0 until size) {
                val c = simpleName[i]
                toUpper = if (c == '_') {
                    true
                } else {
                    if (toUpper) {
                        builder.append(c.uppercaseChar())
                    } else {
                        builder.append(c.lowercaseChar())
                    }
                    false
                }
            }
            return builder.toString()
        }

        /**
         * 覆盖来源：`project/compiler/error/jimmer-ksp-error/.../ErrorGenerator.family`
         * 迁移说明：为避免 metadata-extractor 再依赖 `jimmer-core` 的 `StringUtil` 运行时，这里内聚保留 error family 所需的最小 snake 规则。
         */
        private fun snake(text: String): String {
            val builder = StringBuilder()
            var previousIsLowerOrDigit = false
            for (char in text) {
                val currentIsLowerOrDigit = char.isLowerCase() || char.isDigit()
                if (previousIsLowerOrDigit && !currentIsLowerOrDigit) {
                    builder.append('_')
                }
                previousIsLowerOrDigit = currentIsLowerOrDigit
                builder.append(char.uppercaseChar())
            }
            return builder.toString()
        }

        private fun primitiveListErrorMessage(owner: Any): String =
            when (owner) {
                is LsiEnumConstant ->
                    "The enum constant \"" +
                        ((owner.declaringClass?.qualifiedName ?: owner.declaringClass?.simpleName)
                            ?: "<unknown>") +
                        "." +
                        (owner.name ?: "<unknown>") +
                        "\" is decorated by @$ERROR_FIELD, this annotation is illegal because its `type` is primitive but its `list` is true"

                is LsiClass ->
                    "The enum \"" +
                        ((owner.qualifiedName ?: owner.simpleName) ?: "<unknown>") +
                        "\" is decorated by @$ERROR_FIELD, this annotation is illegal because its `type` is primitive but its `list` is true"

                else ->
                    "The @$ERROR_FIELD annotation is illegal because its `type` is primitive but its `list` is true"
            }

        private fun primitiveListMetaException(owner: Any): MetaException =
            when (owner) {
                is LsiClass -> MetaException(owner, primitiveListErrorMessage(owner))
                is LsiEnumConstant -> MetaException(owner, primitiveListErrorMessage(owner))
                else -> error("Unsupported error-field owner: ${owner::class.qualifiedName}")
            }
    }
}

data class ErrorMetadataExtraction(
    val types: List<ErrorTypeMetadata>,
    val sourceIndex: ErrorMetadataSourceIndex
)

data class ErrorMetadataSourceIndex(
    val anchorsById: Map<String, LsiDiagnosticAnchor>
) {
    fun anchorOf(id: String): LsiDiagnosticAnchor? =
        anchorsById[id]

    fun merge(other: ErrorMetadataSourceIndex): ErrorMetadataSourceIndex =
        if (anchorsById.isEmpty()) {
            other
        } else if (other.anchorsById.isEmpty()) {
            this
        } else {
            ErrorMetadataSourceIndex(
                LinkedHashMap(anchorsById).apply {
                    putAll(other.anchorsById)
                }
            )
        }

    companion object {
        fun empty(): ErrorMetadataSourceIndex =
            ErrorMetadataSourceIndex(emptyMap())
    }
}
