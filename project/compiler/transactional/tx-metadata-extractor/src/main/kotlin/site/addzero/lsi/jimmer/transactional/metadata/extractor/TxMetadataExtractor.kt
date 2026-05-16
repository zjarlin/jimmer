package site.addzero.lsi.jimmer.transactional.metadata.extractor

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.getClassArgument
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.annotation
import site.addzero.lsi.diagnostic.LsiDiagnosticAnchor
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.jimmer.transactional.metadata.model.TxAnnotationArgumentMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxAnnotationMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxAnnotationValueMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxConstructorMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxMethodMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxParameterMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxTypeMetadata
import site.addzero.lsi.jimmer.transactional.metadata.model.TxTypeRefMetadata
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.method.LsiParameter
import site.addzero.lsi.poet.isLsiObjectLikeQualifiedName
import site.addzero.lsi.resolver.LsiResolver
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.type.isSubtypeOfRuntimeExceptionLike
import kotlin.reflect.KClass

/**
 * Tx metadata 提取器。
 *
 * 覆盖来源：
 * - `project/compiler/transactional/jimmer-ksp-transactional/.../TxProcessor`
 * - `project/compiler/transactional/jimmer-ksp-transactional/.../TxGenerator`
 *
 * 迁移说明：将 Tx 侧“扫描 + 校验 + 命名推导 + 生成输入整理”前移到 extractor，
 * generator 只消费纯 metadata，不再直接持有 `LsiClass` / `LsiMethod` / `LsiField`。
 */
class TxMetadataExtractor {

    fun collectNewTypes(
        resolver: LsiResolver,
    ): TxMetadataExtraction =
        collectNewTypes(resolver) { true }

    fun collectNewTypes(
        resolver: LsiResolver,
        include: (LsiClass) -> Boolean = { true },
    ): TxMetadataExtraction =
        extract(
            resolver.newClasses().filter(include)
        )

    fun extract(
        declarations: Sequence<LsiClass>,
    ): TxMetadataExtraction {
        val typeMetadatas = mutableListOf<TxTypeMetadata>()
        val anchorsById = linkedMapOf<String, LsiDiagnosticAnchor>()
        for (declaration in declarations) {
            when (val scanResult = scan(declaration)) {
                ScanResult.None -> continue
                is ScanResult.Invalid -> throw MetaException(declaration, scanResult.reason)
                is ScanResult.Candidate -> {
                    validateType(declaration)?.let { reason ->
                        throw MetaException(declaration, reason)
                    }
                    typeMetadatas += extractType(
                        declaration = declaration,
                        hasClassLevelTx = scanResult.hasClassLevelTx,
                        anchorsById = anchorsById,
                    )
                }
            }
        }
        return TxMetadataExtraction(
            types = typeMetadatas,
            sourceIndex = TxMetadataSourceIndex(anchorsById),
        )
    }

    private fun extractType(
        declaration: LsiClass,
        hasClassLevelTx: Boolean,
        anchorsById: MutableMap<String, LsiDiagnosticAnchor>,
    ): TxTypeMetadata {
        val sourceSimpleName = declaration.simpleName
            ?: throw MetaException(declaration, "The type uses @Tx must have simple name")
        val sourceQualifiedName = declaration.qualifiedName
            ?: throw MetaException(declaration, "The type uses @Tx must have qualified name")
        val typeId = sourceQualifiedName
        anchorsById.putIfAbsent(typeId, classAnchor(declaration))
        val packageName = sourceQualifiedName.substringBeforeLast('.', "")
        val generatedSimpleName = sourceSimpleName + "Tx"
        val generatedQualifiedName =
            if (packageName.isNotEmpty()) {
                "$packageName.$generatedSimpleName"
            } else {
                generatedSimpleName
            }
        val classTx = declaration.annotation(TX_ANNOTATION)
        val copiedAnnotations = declaration.annotations
            .filterNot { annotation ->
                annotation.qualifiedName == TX_ANNOTATION || annotation.qualifiedName == TARGET_ANNOTATION
            }
            .map { it.toMetadata() }
        val targetAnnotationTypeQualifiedName = declaration.annotation(TARGET_ANNOTATION)
            ?.getClassArgument(VALUE_ATTRIBUTE)
            ?.qualifiedName
            ?: declaration.annotation(TARGET_ANNOTATION)?.let {
                throw MetaException(
                    declaration,
                    "Cannot resolve the annotation class argument of @TargetAnnotation",
                )
            }
        val sqlClientProperty = determineSqlClientProperty(declaration)
        val primaryConstructor = declaration.primaryConstructor
            ?.takeIf { !it.isPrivate }
        val allConstructors = distinctConstructors(
            buildList {
                primaryConstructor?.let(::add)
                addAll(declaration.constructors.filter { !it.isPrivate })
            }
        )
        val constructorMetadataById = allConstructors.associate { constructor ->
            val constructorId = constructorId(typeId, constructor)
            anchorsById.putIfAbsent(constructorId, methodAnchor(constructor))
            constructorId to constructor.toConstructorMetadata(constructorId)
        }
        val declaredMethods = declaredMethods(declaration)
        val methods = buildList {
            for (method in declaredMethods) {
                val methodTx = method.annotations.firstOrNull { it.qualifiedName == TX_ANNOTATION }
                if (methodTx != null && method.isStatic) {
                    throw MetaException(
                        method,
                        "Static method cannot be decorated by @Tx",
                    )
                }
                if (methodTx != null && method.isPrivate) {
                    throw MetaException(
                        method,
                        "Private method cannot be decorated by @Tx",
                    )
                }
                if (methodTx != null && method.isAbstract) {
                    throw MetaException(
                        method,
                        "Abstract method cannot be decorated by @Tx",
                    )
                }
                if (methodTx != null && !method.isOpen) {
                    throw MetaException(
                        method,
                        "Final method cannot be decorated by @Tx",
                    )
                }
                if (methodTx != null) {
                    val illegalThrownType = method.thrownTypes.firstOrNull { thrownType ->
                        !thrownType.isSubtypeOfRuntimeExceptionLike()
                    }
                    if (illegalThrownType != null) {
                        throw MetaException(
                            method,
                            "Method decorated by @Tx can only throw RuntimeException, but it throws " +
                                "\"${illegalThrownType.qualifiedName ?: illegalThrownType.presentableText ?: illegalThrownType.simpleName ?: "<unknown>"}\"",
                        )
                    }
                }
                val finalTx =
                    when {
                        methodTx != null -> methodTx
                        !hasClassLevelTx || classTx == null || !method.isPublic || method.isConstructor -> null
                        else -> {
                            if (!method.isOpen) {
                                throw MetaException(
                                    method,
                                    "The public method inherits the class-level @Tx must be open",
                                )
                            }
                            classTx
                        }
                    } ?: continue
                val methodId = methodId(typeId, method)
                anchorsById.putIfAbsent(methodId, methodAnchor(method))
                add(method.toMethodMetadata(methodId, finalTx))
            }
        }
        return TxTypeMetadata(
            id = typeId,
            sourceSimpleName = sourceSimpleName,
            sourceQualifiedName = sourceQualifiedName,
            packageName = packageName,
            generatedSimpleName = generatedSimpleName,
            generatedQualifiedName = generatedQualifiedName,
            isInternal = declaration.isInternal,
            isAbstract = declaration.isAbstract,
            superTypeQualifiedName = sourceQualifiedName,
            copiedAnnotations = copiedAnnotations,
            targetAnnotationTypeQualifiedName = targetAnnotationTypeQualifiedName,
            sqlClientPropertyName = sqlClientProperty.name
                ?: throw MetaException(sqlClientProperty, "Cannot resolve sqlClient property name"),
            primaryConstructor = primaryConstructor?.let { constructorMetadataById.getValue(constructorId(typeId, it)) },
            secondaryConstructors = allConstructors
                .filterNot { constructor ->
                    primaryConstructor != null && sameConstructor(primaryConstructor, constructor)
                }
                .map { constructor ->
                    constructorMetadataById.getValue(constructorId(typeId, constructor))
                },
            methods = methods,
        )
    }

    private fun determineSqlClientProperty(
        declaration: LsiClass,
    ): LsiField {
        val ownerQualifiedName = declaration.qualifiedName
        val props = declaration.fields
            .filter { field ->
                !field.isStatic &&
                    (ownerQualifiedName == null || field.declaringClass?.qualifiedName == ownerQualifiedName) &&
                    field.type?.isSqlClientType() == true
            }
            .toList()
        if (props.isEmpty()) {
            throw MetaException(
                declaration,
                "The class uses @Tx must have a non-static property whose type is KSqlClient or JSqlClient",
            )
        }
        if (props.size > 1) {
            throw MetaException(
                declaration,
                "The class uses @Tx cannot multiple non-static sqlClient properties",
            )
        }
        val prop = props[0]
        if (prop.isPrivate) {
            throw MetaException(
                prop,
                "The sqlClient field of the class uses @Tx cannot be private, protected or internal is recommended",
            )
        }
        return prop
    }

    private fun declaredMethods(declaration: LsiClass): List<LsiMethod> {
        val ownerQualifiedName = declaration.qualifiedName
        return declaration.methods.filter { method ->
            ownerQualifiedName == null || method.declaringClass?.qualifiedName == ownerQualifiedName
        }
    }

    private fun distinctConstructors(
        constructors: List<LsiMethod>,
    ): List<LsiMethod> =
        constructors.distinctBy { constructor ->
            constructor.parameters.map { parameter ->
                parameter.type?.presentableText ?: parameter.type?.qualifiedName ?: parameter.typeName ?: "?"
            }
        }

    private fun sameConstructor(
        left: LsiMethod,
        right: LsiMethod,
    ): Boolean =
        left.parameters.map { parameter ->
            parameter.type?.presentableText ?: parameter.type?.qualifiedName ?: parameter.typeName ?: "?"
        } == right.parameters.map { parameter ->
            parameter.type?.presentableText ?: parameter.type?.qualifiedName ?: parameter.typeName ?: "?"
        }

    private fun constructorId(
        ownerId: String,
        constructor: LsiMethod,
    ): String =
        "$ownerId#<init>(${constructor.parameters.joinToString(",") { parameter ->
            parameter.type?.qualifiedName ?: parameter.type?.presentableText ?: parameter.typeName ?: "?"
        }})"

    private fun methodId(
        ownerId: String,
        method: LsiMethod,
    ): String =
        "$ownerId#${method.name ?: "<unknown>"}(${method.parameters.joinToString(",") { parameter ->
            parameter.type?.qualifiedName ?: parameter.type?.presentableText ?: parameter.typeName ?: "?"
        }})"

    private fun LsiMethod.toConstructorMetadata(
        id: String,
    ): TxConstructorMetadata =
        TxConstructorMetadata(
            id = id,
            isProtected = isProtected,
            isInternal = isInternal,
            annotations = annotations.map { it.toMetadata() },
            parameters = parameters.mapIndexed { index, parameter ->
                parameter.toMetadata("$id#$index")
            },
        )

    private fun LsiMethod.toMethodMetadata(
        id: String,
        tx: LsiAnnotation,
    ): TxMethodMetadata =
        TxMethodMetadata(
            id = id,
            name = name ?: throw MetaException(this, "Cannot resolve method name"),
            propagation = tx.getAttribute(VALUE_ATTRIBUTE).toString().substringAfterLast('.'),
            isProtected = isProtected,
            isInternal = isInternal,
            annotations = annotations
                .filterNot { annotation -> annotation.qualifiedName == TX_ANNOTATION }
                .map { it.toMetadata() },
            parameters = parameters.mapIndexed { index, parameter ->
                parameter.toMetadata("$id#$index")
            },
            returnType = returnType?.toMetadata(),
            thrownTypes = thrownTypes.map { it.toMetadata() },
        )

    private fun LsiParameter.toMetadata(
        id: String,
    ): TxParameterMetadata =
        TxParameterMetadata(
            id = id,
            name = name ?: error("LsiParameter.name must not be null after extractor validation"),
            type = type?.toMetadata()
                ?: error("LsiParameter.type must not be null after extractor validation"),
        )

    private fun LsiType.toMetadata(): TxTypeRefMetadata =
        TxTypeRefMetadata(
            qualifiedName = qualifiedName,
            simpleName = simpleName,
            presentableText = presentableText,
            nullable = isNullable,
            primitive = isPrimitive,
            array = isArray,
            typeArguments = typeParameters.map { it.toMetadata() },
            componentType = componentType?.toMetadata(),
        )

    private fun LsiAnnotation.toMetadata(): TxAnnotationMetadata =
        TxAnnotationMetadata(
            qualifiedName = qualifiedName
                ?: error("LsiAnnotation.qualifiedName must not be null when extracting annotation metadata"),
            arguments = attributes.entries.map { (name, value) ->
                TxAnnotationArgumentMetadata(
                    name = name,
                    value = value.toMetadataValue(),
                )
            },
        )

    private fun Any?.toMetadataValue(): TxAnnotationValueMetadata =
        when (this) {
            null -> TxAnnotationValueMetadata.NullValue
            is String -> TxAnnotationValueMetadata.StringValue(this)
            is Boolean -> TxAnnotationValueMetadata.BooleanValue(this)
            is Byte, is Short, is Int, is Long, is Float, is Double -> {
                TxAnnotationValueMetadata.NumberValue(this as Number)
            }
            is Char -> TxAnnotationValueMetadata.CharValue(this)
            is Enum<*> -> TxAnnotationValueMetadata.EnumValue(
                typeQualifiedName = javaClass.name,
                entryName = name,
            )
            is LsiClass -> TxAnnotationValueMetadata.ClassValue(
                TxTypeRefMetadata(
                    qualifiedName = qualifiedName,
                    simpleName = simpleName,
                    presentableText = qualifiedName ?: simpleName,
                    nullable = false,
                    primitive = false,
                    array = false,
                    typeArguments = emptyList(),
                    componentType = null,
                )
            )
            is LsiType -> TxAnnotationValueMetadata.TypeValue(toMetadata())
            is LsiAnnotation -> TxAnnotationValueMetadata.AnnotationValue(toMetadata())
            is List<*> -> TxAnnotationValueMetadata.ListValue(map { it.toMetadataValue() })
            is Class<*> -> TxAnnotationValueMetadata.ClassValue(
                TxTypeRefMetadata(
                    qualifiedName = name,
                    simpleName = simpleName,
                    presentableText = name,
                    nullable = false,
                    primitive = isPrimitive,
                    array = isArray,
                    typeArguments = emptyList(),
                    componentType = componentType?.let { component ->
                        TxTypeRefMetadata(
                            qualifiedName = component.name,
                            simpleName = component.simpleName,
                            presentableText = component.name,
                            nullable = false,
                            primitive = component.isPrimitive,
                            array = component.isArray,
                            typeArguments = emptyList(),
                            componentType = null,
                        )
                    },
                )
            )
            is KClass<*> -> TxAnnotationValueMetadata.ClassValue(
                TxTypeRefMetadata(
                    qualifiedName = qualifiedName,
                    simpleName = simpleName,
                    presentableText = qualifiedName ?: simpleName,
                    nullable = false,
                    primitive = false,
                    array = false,
                    typeArguments = emptyList(),
                    componentType = null,
                )
            )
            else -> error("Unsupported annotation attribute value: ${this::class.qualifiedName}")
        }

    private fun LsiType.isSqlClientType(
        visited: MutableSet<String> = mutableSetOf(),
    ): Boolean {
        val typeName = qualifiedName ?: presentableText?.substringBefore('<') ?: simpleName
        if (typeName == K_SQL_CLIENT || typeName == J_SQL_CLIENT) {
            return true
        }
        if (typeName != null && !visited.add(typeName)) {
            return false
        }
        val lsiClass = lsiClass ?: return false
        return lsiClass.superTypes.any {
            it.isSqlClientType(visited)
        }
    }

    private fun scan(
        lsiClass: LsiClass,
    ): ScanResult {
        val ownerQualifiedName = lsiClass.qualifiedName
        val declaredFields = lsiClass.fields.filter { field ->
            ownerQualifiedName == null || field.declaringClass?.qualifiedName == ownerQualifiedName
        }
        val declaredMethods = declaredMethods(lsiClass)
        val declaredConstructors = distinctConstructors(
            buildList {
                lsiClass.primaryConstructor?.let(::add)
                addAll(lsiClass.constructors)
            }
        )
        if (declaredFields.any { field -> field.annotations.any { it.qualifiedName == TX_ANNOTATION } }) {
            return ScanResult.Invalid("it cannot be decorated by @Tx")
        }
        if (declaredConstructors.any { constructor ->
                constructor.annotations.any { it.qualifiedName == TX_ANNOTATION }
            }) {
            return ScanResult.Invalid("it cannot be decorated by @Tx")
        }
        val txMethods = declaredMethods.filter { method ->
            method.annotations.any { it.qualifiedName == TX_ANNOTATION }
        }
        if (txMethods.any { it.isConstructor }) {
            return ScanResult.Invalid("it cannot be decorated by @Tx")
        }
        val hasClassLevelTx = lsiClass.annotations.any { it.qualifiedName == TX_ANNOTATION }
        if (hasClassLevelTx || txMethods.isNotEmpty()) {
            return ScanResult.Candidate(hasClassLevelTx = hasClassLevelTx)
        }
        return ScanResult.None
    }

    private fun validateType(
        lsiClass: LsiClass,
    ): String? {
        if (!lsiClass.isClass) {
            return "The type uses @Tx must be class"
        }
        if (!lsiClass.isTopLevel) {
            return "The class uses @Tx must be top-level class"
        }
        if (lsiClass.isData) {
            return "The class uses @Tx cannot be data class"
        }
        if (lsiClass.isSealed) {
            return "The class uses @Tx cannot be sealed class"
        }
        if (!lsiClass.isOpen) {
            return "The class uses @Tx must be open"
        }
        if (lsiClass.typeParameterCount > 0) {
            return "The current version does not yet support the use of generics for types annotated with @Tx"
        }
        if (lsiClass.superClasses.any { superClass ->
                !superClass.qualifiedName.isLsiObjectLikeQualifiedName()
            }) {
            return "The current version does not yet support the use of inheritance for types annotated with @Tx"
        }
        return null
    }

    private fun classAnchor(
        declaration: LsiClass,
    ): LsiDiagnosticAnchor =
        SimpleDiagnosticAnchor(
            kind = LsiDiagnosticAnchor.Kind.CLASS,
            ownerQualifiedName = declaration.qualifiedName,
            symbolName = declaration.simpleName,
        )

    private fun methodAnchor(
        declaration: LsiMethod,
    ): LsiDiagnosticAnchor =
        SimpleDiagnosticAnchor(
            kind = LsiDiagnosticAnchor.Kind.METHOD,
            ownerQualifiedName = declaration.declaringClass?.qualifiedName,
            symbolName = declaration.name,
        )

    private data class SimpleDiagnosticAnchor(
        override val kind: LsiDiagnosticAnchor.Kind,
        override val ownerQualifiedName: String?,
        override val symbolName: String?,
    ) : LsiDiagnosticAnchor

    private sealed interface ScanResult {
        data object None : ScanResult

        data class Candidate(
            val hasClassLevelTx: Boolean,
        ) : ScanResult

        data class Invalid(
            val reason: String,
        ) : ScanResult
    }

    companion object {
        private const val K_SQL_CLIENT = "org.babyfish.jimmer.sql.kt.KSqlClient"
        private const val J_SQL_CLIENT = "org.babyfish.jimmer.sql.JSqlClient"
        private const val TX_ANNOTATION = "org.babyfish.jimmer.sql.transaction.Tx"
        private const val TARGET_ANNOTATION = "org.babyfish.jimmer.sql.transaction.TargetAnnotation"
        private const val VALUE_ATTRIBUTE = "value"
    }
}

data class TxMetadataExtraction(
    val types: List<TxTypeMetadata>,
    val sourceIndex: TxMetadataSourceIndex,
)

data class TxMetadataSourceIndex(
    val anchorsById: Map<String, LsiDiagnosticAnchor>,
) {
    fun anchorOf(id: String): LsiDiagnosticAnchor? =
        anchorsById[id]

    fun merge(other: TxMetadataSourceIndex): TxMetadataSourceIndex =
        if (anchorsById.isEmpty()) {
            other
        } else if (other.anchorsById.isEmpty()) {
            this
        } else {
            TxMetadataSourceIndex(
                LinkedHashMap(anchorsById).apply {
                    putAll(other.anchorsById)
                }
            )
        }

    companion object {
        fun empty(): TxMetadataSourceIndex =
            TxMetadataSourceIndex(emptyMap())
    }
}
