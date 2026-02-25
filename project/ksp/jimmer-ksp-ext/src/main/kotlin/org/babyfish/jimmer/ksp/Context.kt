package org.babyfish.jimmer.ksp

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSType
import org.babyfish.jimmer.Immutable
import org.babyfish.jimmer.ksp.immutable.meta.ImmutableType
import org.babyfish.jimmer.sql.Embeddable
import org.babyfish.jimmer.sql.Entity
import org.babyfish.jimmer.sql.MappedSuperclass
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi_impl.impl.ksp.clazz.KspLsiClass
import site.addzero.util.lsi_impl.impl.ksp.clazz.toKSClassDeclaration

object Context {
    lateinit var resolver: Resolver
        private set
    lateinit var environment: SymbolProcessorEnvironment
        private set

    var delayedTupleTypeNames: Collection<String>? = null

    var delayedClientTypeNames: Collection<String>? = null

    // ---- 轮次内缓存（每轮 reset() 时清空，首次访问时初始化）----
    private var _collectionType: KSType? = null
    private var _listType: KSType? = null
    private var _mapType: KSType? = null

    /**
     * 每轮 KSP 处理开始时调用，更新 resolver 并清理与上一轮 Resolver 绑定的缓存。
     * environment 在整个 KSP 生命周期内不变，仅首次赋值。
     */
    fun reset(resolver: Resolver, environment: SymbolProcessorEnvironment) {
        this.resolver = resolver
        this.environment = environment
        // 清理轮次内缓存
        _collectionType = null
        _listType = null
        _mapType = null
    }

    val explicitClientApi: Boolean get() =
        resolver.getAllFiles().any { file ->
            file.declarations.any {
                it is KSClassDeclaration && it.include() && it.annotation(EnableImplicitApi::class) !== null
            }
        }

    fun snapshotAllTypeNames() {
        delayedClientTypeNames = resolver.getAllFiles().flatMap { file ->
            file.declarations.filterIsInstance<KSClassDeclaration>().map { it.fullName }
        }.toList()
    }

    // 使用轮次内缓存：首次访问时从当前 resolver 解析并缓存，同轮内复用，跨轮次由 reset() 清空
    val collectionType: KSType get() = _collectionType ?: resolver
        .getClassDeclarationByName("kotlin.collections.Collection")
        ?.asStarProjectedType()
        ?.also { _collectionType = it }
        ?: error("Internal bug")

    val listType: KSType get() = _listType ?: resolver
        .getClassDeclarationByName("kotlin.collections.List")
        ?.asStarProjectedType()
        ?.also { _listType = it }
        ?: error("Internal bug")

    val mapType: KSType get() = _mapType ?: resolver
        .getClassDeclarationByName("kotlin.collections.Map")
        ?.asStarProjectedType()
        ?.also { _mapType = it }
        ?: error("Internal bug")

    //    @Deprecated("Please use Settings")
//    val isHibernateValidatorEnhancement: Boolean =
//        environment.options["jimmer.dto.hibernateValidatorEnhancement"] == "true"
//
//    @Deprecated("Please use Settings")
//    val isBuddyIgnoreResourceGeneration: Boolean =
//        environment.options["jimmer.buddy.ignoreResourceGeneration"]?.trim() == "true"
//
//    @Deprecated("Please use Settings")
//    private val includes: Array<String>? =
//        environment.options["jimmer.source.includes"]
//            ?.takeIf { it.isNotEmpty() }
//            ?.let {
//                it.trim().split("\\s*,[,;]\\s*").toTypedArray()
//            }
//
//    @Deprecated("Please use Settings")
//    private val excludes: Array<String>? =
//        environment.options["jimmer.source.excludes"]
//            ?.takeIf { it.isNotEmpty() }
//            ?.let {
//                it.trim().split("\\s*[,;]\\s*").toTypedArray()
//            }
//
//    val isHibernateValidatorEnhancement: Boolean =
//        environment.options["jimmer.dto.hibernateValidatorEnhancement"] == "true"

//    @Deprecated("Please use Settings")
//    val isBuddyIgnoreResourceGeneration: Boolean =
//        environment.options["jimmer.buddy.ignoreResourceGeneration"]?.trim() == "true"

    val jackson3 = detectIsJackson3(resolver, environment)

    val jacksonTypes: JacksonTypes =
        if (jackson3) {
            JacksonTypes(
                jsonIgnore = ClassName("com.fasterxml.jackson.annotation", "JsonIgnore"),
                jsonValue = ClassName("com.fasterxml.jackson.annotation", "JsonValue"),
                jsonFormat = ClassName("com.fasterxml.jackson.annotation", "JsonFormat"),
                jsonProperty = ClassName("com.fasterxml.jackson.annotation", "JsonProperty"),
                jsonPropertyOrder = ClassName("com.fasterxml.jackson.annotation", "JsonPropertyOrder"),
                jsonCreator = ClassName("com.fasterxml.jackson.annotation", "JsonCreator"),
                jsonSerializer = ClassName("tools.jackson.databind", "ValueSerializer"),
                jsonSerialize = ClassName("tools.jackson.databind.annotation", "JsonSerialize"),
                jsonDeserialize = ClassName("tools.jackson.databind.annotation", "JsonDeserialize"),
                jsonPojoBuilder = ClassName("tools.jackson.databind.annotation", "JsonPOJOBuilder"),
                jsonNaming = ClassName("tools.jackson.databind.annotation", "JsonNaming"),
                jsonGenerator = ClassName("tools.jackson.core", "JsonGenerator"),
                serializeProvider = ClassName("tools.jackson.databind", "SerializationContext")
            )
        } else {
            JacksonTypes(
                jsonIgnore = ClassName("com.fasterxml.jackson.annotation", "JsonIgnore"),
                jsonValue = ClassName("com.fasterxml.jackson.annotation", "JsonValue"),
                jsonFormat = ClassName("com.fasterxml.jackson.annotation", "JsonFormat"),
                jsonProperty = ClassName("com.fasterxml.jackson.annotation", "JsonProperty"),
                jsonPropertyOrder = ClassName("com.fasterxml.jackson.annotation", "JsonPropertyOrder"),
                jsonCreator = ClassName("com.fasterxml.jackson.annotation", "JsonCreator"),
                jsonSerializer = ClassName("com.fasterxml.jackson.databind", "JsonSerializer"),
                jsonSerialize = ClassName("com.fasterxml.jackson.databind.annotation", "JsonSerialize"),
                jsonDeserialize = ClassName("com.fasterxml.jackson.databind.annotation", "JsonDeserialize"),
                jsonPojoBuilder = ClassName("com.fasterxml.jackson.databind.annotation", "JsonPOJOBuilder"),
                jsonNaming = ClassName("com.fasterxml.jackson.databind.annotation", "JsonNaming"),
                jsonGenerator = ClassName("com.fasterxml.jackson.core", "JsonGenerator"),
                serializeProvider = ClassName("com.fasterxml.jackson.databind", "SerializerProvider")
            )
        }

    private val includes: Array<String>? =
        environment.options["jimmer.source.includes"]
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                it.trim().split("\\s*,[,;]\\s*").toTypedArray()
            }

    private val excludes: Array<String>? =
        environment.options["jimmer.source.excludes"]
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                it.trim().split("\\s*[,;]\\s*").toTypedArray()
            }

    private val typeMap: MutableMap<KSClassDeclaration, ImmutableType> = mutableMapOf()

    private var newTypes = typeMap.values.toMutableList()

    /** 主入口：以 [LsiClass] 创建或获取 [ImmutableType]，map key 使用底层 KSClassDeclaration 保证唯一性 */
    fun typeOf(lsiClass: LsiClass): ImmutableType {
        val ksDecl = lsiClass.toKSClassDeclaration()
        return typeMap[ksDecl] ?: ImmutableType(this, lsiClass).also {
            typeMap[ksDecl] = it
            newTypes += it
        }
    }

    /** 兼容重载：接受 KSClassDeclaration，内部包装为 KspLsiClass 后调用主入口 */
    fun typeOf(classDeclaration: KSClassDeclaration): ImmutableType =
        typeOf(KspLsiClass(resolver, classDeclaration))

    fun typeAnnotationOf(classDeclaration: KSClassDeclaration): KSAnnotation? {
        var sqlAnnotation: KSAnnotation? = null
        for (ormAnnotationType in ORM_ANNOTATION_TYPES) {
            val anno = classDeclaration.annotation(ormAnnotationType) ?: continue
            if (sqlAnnotation !== null) {
                throw MetaException(
                    classDeclaration,
                    null,
                    "it cannot be decorated by both " +
                            "@${sqlAnnotation.fullName} and ${anno.fullName}"
                )
            }
            sqlAnnotation = anno
        }
        return sqlAnnotation ?: classDeclaration.annotation(Immutable::class)
    }

    fun resolve() {
        while (this.newTypes.isNotEmpty()) {
            val newTypes = this.newTypes
            this.newTypes = mutableListOf()
            for (newType in newTypes) {
                for (step in 0..4) {
                    newType.resolve(this, step)
                }
            }
        }
    }

//    fun include(declaration: KSClassDeclaration): Boolean {
//        val qualifiedName = declaration.qualifiedName!!.asString()
//        if (includes !== null && !includes.any { qualifiedName.startsWith(it) }) {
//            return false
//        }
//        if (excludes !== null && excludes.any { qualifiedName.startsWith(it) }) {
//            return false
//        }
//        return true
//    }

    private fun detectIsJackson3(resolver: Resolver, environment: SymbolProcessorEnvironment): Boolean {
        val jackson3Text = environment.options["jimmer.jackson3"]
        return if (jackson3Text.isNullOrEmpty()) {
            resolver.getClassDeclarationByName("tools.jackson.databind.ObjectMapper") != null
        } else {
            "true" == jackson3Text
        }
    }

    private val ORM_ANNOTATION_TYPES = listOf(
        Entity::class,
        MappedSuperclass::class,
        Embeddable::class
    )
}
