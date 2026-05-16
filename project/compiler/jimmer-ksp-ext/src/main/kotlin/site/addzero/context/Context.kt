package site.addzero.context

import site.addzero.lsi.jimmer.meta.ImmutableType
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.codegen.LsiFiler
import site.addzero.lsi.codegen.JacksonTypes
import site.addzero.lsi.file.LsiFile
import site.addzero.lsi.poet.LsiClassName
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.resolver.LsiResolver
import java.io.File

object Context {
    private const val ENABLE_IMPLICIT_API_ANNOTATION = "org.babyfish.jimmer.client.EnableImplicitApi"

    /** LSI 解析器（推荐新代码使用） */
    lateinit var lsiResolver: LsiResolver
        private set

    /** LSI Filer（用于生成文件） */
    lateinit var lsiFiler: LsiFiler
        private set

    var delayedTupleTypeNames: Collection<String>? = null

    var delayedClientTypeNames: Collection<String>? = null

    private var firstLsiFileProvider: (() -> LsiFile?)? = null

    private var sourceAnchorFilePathProvider: (() -> String?)? = null

    private var generatedJimmerResourceFileProvider: ((String) -> File?)? = null

    private var infoLogger: ((String) -> Unit)? = null

    private var draftImplDocMapProvider: ((LsiClass, String, String) -> Map<String, String>)? = null

    private var options: Map<String, String> = emptyMap()

    /**
     * 每轮处理开始时调用，注入本轮所需的 LSI 语义对象与平台胶水回调。
     */
    fun reset(
        lsiResolver: LsiResolver,
        lsiFiler: LsiFiler,
        options: Map<String, String>,
        firstLsiFileProvider: () -> LsiFile?,
        sourceAnchorFilePathProvider: () -> String?,
        generatedJimmerResourceFileProvider: (String) -> File?,
        infoLogger: (String) -> Unit,
        draftImplDocMapProvider: (LsiClass, String, String) -> Map<String, String>,
    ) {
        // 覆盖来源：project/compiler/jimmer-ksp-ext/.../Context.reset KSP 每轮上下文刷新
        // 迁移说明：Context 不再直接持有 KSP Resolver/Environment，平台层仅以 `KSP -> LSI` 单向注入语义对象与回调
        this.lsiResolver = lsiResolver
        this.lsiFiler = lsiFiler
        this.options = options
        this.firstLsiFileProvider = firstLsiFileProvider
        this.sourceAnchorFilePathProvider = sourceAnchorFilePathProvider
        this.generatedJimmerResourceFileProvider = generatedJimmerResourceFileProvider
        this.infoLogger = infoLogger
        this.draftImplDocMapProvider = draftImplDocMapProvider
        this.jackson3Value = detectIsJackson3()
        this.jacksonTypesValue = createJacksonTypes(this.jackson3Value == true)
    }

    val explicitClientApi: Boolean
        get() =
            lsiResolver
                .allClasses()
                .any { lsiClass ->
                    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../Context.explicitClientApi 的 `lsiClass.include()`
                    // 迁移说明：客户端显式 API 总开关扫描改为复用配置层 `matchesConfiguredSourceFilters()`，去除对旧 `org.babyfish.jimmer.ksp.include` helper 的依赖
                    lsiClass.matchesConfiguredSourceFilters() &&
                        lsiClass.annotations.any {
                            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../Context.explicitClientApi
                            // 迁移说明：客户端显式 API 开关注解判定改为 FQ 常量比对，减少 compiler 对 jimmer-core Java 注解类编译产物的硬依赖
                            it.qualifiedName == ENABLE_IMPLICIT_API_ANNOTATION
                        }
                }

    fun snapshotAllTypeNames() {
        delayedClientTypeNames = lsiResolver
            .allClasses()
            .mapNotNull { it.qualifiedName }
            .toList()
    }

    fun firstLsiFileOrNull(): LsiFile? {
        // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoProcessor.collectDtoFiles 的 `KspLsiFile(Context.resolver, firstFile)`
        // 迁移说明：首文件定位改为平台层回调注入，compiler 侧只消费 `LsiFile`
        return firstLsiFileProvider?.invoke()
    }

    val firstSourceFilePath: String?
        get() {
            // 覆盖来源：project/compiler/dto/jimmer-ksp-dto/.../DtoProcessor.dtoDirs 的 `ctx.resolver.getAllFiles().first().filePath`
            // 迁移说明：首文件路径读取统一经由 LSI 文件回调暴露，业务模块只消费普通路径字符串
            return firstLsiFileOrNull()?.filePath
        }

    val sourceAnchorFilePath: String?
        get() {
            // 覆盖来源：APT DTO 通过 generated resource path、KSP DTO 通过首源码文件定位项目根目录
            // 迁移说明：项目锚点路径统一由平台层回调注入，DTO 壳层不再直接依赖具体平台资源/文件 API
            return sourceAnchorFilePathProvider?.invoke() ?: firstSourceFilePath
        }

    fun option(name: String): String? =
        options[name]

    fun guessGeneratedJimmerResourceFile(name: String): File? {
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../JimmerModuleGenerator.generate 的 `guessResourceFile(codeGenerator.generatedFile.firstOrNull(), name)`
        // 迁移说明：resource 探测改为平台层回调注入，业务模块不再直接依赖 KSP CodeGenerator
        return generatedJimmerResourceFileProvider?.invoke(name)
    }

    fun logInfo(message: String) {
        // 覆盖来源：project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.notifyEntityMetaConsumers 的 `ctx.environment.logger.info(...)`
        // 迁移说明：日志输出改为平台层回调注入，业务模块不再直接触碰任何平台 logger 类型
        infoLogger?.invoke(message)
    }

    fun findDraftImplDocMap(
        type: LsiClass,
        annotationQualifiedName: String,
        valueAttributeName: String = "value",
    ): Map<String, String> {
        // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../DocMetadata.implDocStringMap 的 `findKspDraftImplDocMap(...)`
        // 迁移说明：draft Impl 文档提取改为平台层回调注入，client 编译器不再直接依赖 `site.addzero.lsi.ksp.*`
        return draftImplDocMapProvider?.invoke(type, annotationQualifiedName, valueAttributeName)
            ?: emptyMap()
    }

    fun convertedLsiTypeNameOf(
        owner: LsiClass,
        propName: String,
    ): LsiTypeName? {
        // 覆盖来源：client processor 直接读取 `Context.typeOf(owner).properties[propName]?.converterMetadata`
        // 迁移说明：client/export-doc 这类壳层不再直接触碰 shared immutable metadata 大对象，
        // 统一通过 Context 暴露最小化 `LsiTypeName?` 查询结果，避免把 DTO SPI 泄漏继续扩散到壳层编译依赖
        return typeOf(owner)
            .properties[propName]
            ?.converterMetadata
            ?.targetTypeName
    }

    private var jackson3Value: Boolean? = null

    // 覆盖来源：project/compiler/jimmer-ksp-ext/.../Context.jacksonTypesValue 的 `org.babyfish.jimmer.ksp.JacksonTypes`
    // 迁移说明：Jackson 类型表载体迁移到中立 `site.addzero.lsi.codegen.JacksonTypes`，`Context` 仅保留组装职责
    private var jacksonTypesValue: JacksonTypes? = null

    val jackson3: Boolean
        get() {
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../Context.jackson3 的 eager 初始化路径
            // 迁移说明：平台探测改为基于注入的 LSI 语义/普通 options 缓存，避免 object 首次装载时触发平台对象访问
            return jackson3Value
                ?: error("Context.reset(...) must be called before accessing jackson3")
        }

    val jacksonTypes: JacksonTypes
        get() {
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../Context.jacksonTypes 的 eager 初始化路径
            // 迁移说明：Jackson 类型表与 `jackson3` 一起在 reset 后构建，保留原语义并消除 object 初始化时的平台依赖
            return jacksonTypesValue
                ?: error("Context.reset(...) must be called before accessing jacksonTypes")
        }

    /** LSI 类型缓存，key 为类型稳定标识（优先 qualifiedName）。 */
    private val typeMap: MutableMap<String, ImmutableType> = mutableMapOf()

    private var newTypes = typeMap.values.toMutableList()

    /** 主入口：以 [LsiClass] 创建或获取 [ImmutableType] */
    fun typeOf(lsiClass: LsiClass): ImmutableType {
        val key = lsiClassKey(lsiClass)
        return typeMap[key] ?: ImmutableType(this, lsiClass).also {
            typeMap[key] = it
            newTypes += it
        }
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

    private fun createJacksonTypes(jackson3: Boolean): JacksonTypes =
        if (jackson3) {
            JacksonTypes(
                jsonIgnore = LsiClassName("com.fasterxml.jackson.annotation", listOf("JsonIgnore")),
                jsonValue = LsiClassName("com.fasterxml.jackson.annotation", listOf("JsonValue")),
                jsonFormat = LsiClassName("com.fasterxml.jackson.annotation", listOf("JsonFormat")),
                jsonProperty = LsiClassName("com.fasterxml.jackson.annotation", listOf("JsonProperty")),
                jsonPropertyOrder = LsiClassName("com.fasterxml.jackson.annotation", listOf("JsonPropertyOrder")),
                jsonCreator = LsiClassName("com.fasterxml.jackson.annotation", listOf("JsonCreator")),
                jsonSerializer = LsiClassName("tools.jackson.databind", listOf("ValueSerializer")),
                jsonSerialize = LsiClassName("tools.jackson.databind.annotation", listOf("JsonSerialize")),
                jsonDeserialize = LsiClassName("tools.jackson.databind.annotation", listOf("JsonDeserialize")),
                jsonPojoBuilder = LsiClassName("tools.jackson.databind.annotation", listOf("JsonPOJOBuilder")),
                jsonNaming = LsiClassName("tools.jackson.databind.annotation", listOf("JsonNaming")),
                jsonGenerator = LsiClassName("tools.jackson.core", listOf("JsonGenerator")),
                serializeProvider = LsiClassName("tools.jackson.databind", listOf("SerializationContext"))
            )
        } else {
            JacksonTypes(
                jsonIgnore = LsiClassName("com.fasterxml.jackson.annotation", listOf("JsonIgnore")),
                jsonValue = LsiClassName("com.fasterxml.jackson.annotation", listOf("JsonValue")),
                jsonFormat = LsiClassName("com.fasterxml.jackson.annotation", listOf("JsonFormat")),
                jsonProperty = LsiClassName("com.fasterxml.jackson.annotation", listOf("JsonProperty")),
                jsonPropertyOrder = LsiClassName("com.fasterxml.jackson.annotation", listOf("JsonPropertyOrder")),
                jsonCreator = LsiClassName("com.fasterxml.jackson.annotation", listOf("JsonCreator")),
                jsonSerializer = LsiClassName("com.fasterxml.jackson.databind", listOf("JsonSerializer")),
                jsonSerialize = LsiClassName("com.fasterxml.jackson.databind.annotation", listOf("JsonSerialize")),
                jsonDeserialize = LsiClassName("com.fasterxml.jackson.databind.annotation", listOf("JsonDeserialize")),
                jsonPojoBuilder = LsiClassName("com.fasterxml.jackson.databind.annotation", listOf("JsonPOJOBuilder")),
                jsonNaming = LsiClassName("com.fasterxml.jackson.databind.annotation", listOf("JsonNaming")),
                jsonGenerator = LsiClassName("com.fasterxml.jackson.core", listOf("JsonGenerator")),
                serializeProvider = LsiClassName("com.fasterxml.jackson.databind", listOf("SerializerProvider"))
            )
        }

    private fun detectIsJackson3(): Boolean {
        val jackson3Text = options["jimmer.jackson3"]
        return if (jackson3Text.isNullOrEmpty()) {
            // 覆盖来源：project/compiler/jimmer-ksp-ext/.../Context.detectIsJackson3 依赖探测
            // 迁移说明：类存在性探测仅复用注入的 `lsiResolver`，Context 不再直接依赖 KSP 环境对象
            lsiResolver.findClassByQualifiedName("tools.jackson.databind.ObjectMapper") != null
        } else {
            "true" == jackson3Text
        }
    }

    private fun lsiClassKey(lsiClass: LsiClass): String =
        lsiClass.qualifiedName
            ?: lsiClass.simpleName
            ?: error("Cannot build type cache key for LsiClass with null simpleName and qualifiedName")
}
