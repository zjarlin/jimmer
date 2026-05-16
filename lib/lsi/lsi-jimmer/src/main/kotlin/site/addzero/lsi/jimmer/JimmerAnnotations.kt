package site.addzero.lsi.jimmer

const val ENTITY = "org.babyfish.jimmer.sql.Entity"
const val MAPPED_SUPERCLASS = "org.babyfish.jimmer.sql.MappedSuperclass"
const val EMBEDDABLE = "org.babyfish.jimmer.sql.Embeddable"
const val IMMUTABLE = "org.babyfish.jimmer.Immutable"

const val ID = "org.babyfish.jimmer.sql.Id"
const val VERSION = "org.babyfish.jimmer.sql.Version"
const val LOGICAL_DELETED = "org.babyfish.jimmer.sql.LogicalDeleted"
const val KEY = "org.babyfish.jimmer.sql.Key"
const val GENERATED_VALUE = "org.babyfish.jimmer.sql.GeneratedValue"
const val EXCLUDE_FROM_ALL_SCALARS = "org.babyfish.jimmer.sql.ExcludeFromAllScalars"
const val JOIN_SQL = "org.babyfish.jimmer.sql.JoinSql"
const val DEFAULT = "org.babyfish.jimmer.sql.Default"

const val MANY_TO_ONE = "org.babyfish.jimmer.sql.ManyToOne"
const val ONE_TO_ONE = "org.babyfish.jimmer.sql.OneToOne"
const val ONE_TO_MANY = "org.babyfish.jimmer.sql.OneToMany"
const val MANY_TO_MANY = "org.babyfish.jimmer.sql.ManyToMany"

const val ID_VIEW = "org.babyfish.jimmer.sql.IdView"
const val MANY_TO_MANY_VIEW = "org.babyfish.jimmer.sql.ManyToManyView"

const val FORMULA = "org.babyfish.jimmer.Formula"
const val TRANSIENT = "org.babyfish.jimmer.sql.Transient"
const val SCALAR = "org.babyfish.jimmer.Scalar"
const val JSON_CONVERTER = "org.babyfish.jimmer.jackson.JsonConverter"
const val DESCRIPTION = "org.babyfish.jimmer.client.Description"
const val API = "org.babyfish.jimmer.client.meta.Api"
const val API_IGNORE = "org.babyfish.jimmer.client.ApiIgnore"
const val FETCH_BY = "org.babyfish.jimmer.client.FetchBy"
const val DEFAULT_FETCHER_OWNER = "org.babyfish.jimmer.client.meta.DefaultFetcherOwner"
const val EXPORT_DOC = "org.babyfish.jimmer.client.ExportDoc"
const val REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController"
const val REQUEST_MAPPING = "org.springframework.web.bind.annotation.RequestMapping"
const val GET_MAPPING = "org.springframework.web.bind.annotation.GetMapping"
const val POST_MAPPING = "org.springframework.web.bind.annotation.PostMapping"
const val PUT_MAPPING = "org.springframework.web.bind.annotation.PutMapping"
const val DELETE_MAPPING = "org.springframework.web.bind.annotation.DeleteMapping"
const val PATCH_MAPPING = "org.springframework.web.bind.annotation.PatchMapping"
const val CLIENT_EXCEPTION = "org.babyfish.jimmer.ClientException"
const val CODE_BASED_EXCEPTION = "org.babyfish.jimmer.error.CodeBasedException"
const val CODE_BASED_RUNTIME_EXCEPTION = "org.babyfish.jimmer.error.CodeBasedRuntimeException"
const val DEFAULT_ERROR_FAMILY = "DEFAULT"
const val ERROR_FAMILY = "org.babyfish.jimmer.error.ErrorFamily"
const val ERROR_FIELD = "org.babyfish.jimmer.error.ErrorField"
const val ERROR_FIELDS = "org.babyfish.jimmer.error.ErrorFields"
const val TYPED_TUPLE = "org.babyfish.jimmer.sql.TypedTuple"

val ALL_JIMMER_ENTITY_ANNOTATIONS = setOf(ENTITY, MAPPED_SUPERCLASS, EMBEDDABLE, IMMUTABLE)
val ALL_JIMMER_ASSOCIATION_ANNOTATIONS = setOf(MANY_TO_ONE, ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY)

// 覆盖来源：project/jimmer-core/.../client/meta/ApiOperation.AUTO_OPERATION_ANNOTATIONS
// 覆盖来源：project/compiler/client/jimmer-ksp-client/.../LsiClientApiRules.REST_CONTROLLER_ANNOTATION
// 迁移说明：将 client 自动识别所需的 Spring MVC 注解名单统一收敛到 lsi-jimmer 常量，
// 避免 compiler 侧为 API 判定反向依赖 jimmer-core runtime meta 接口
val AUTO_API_OPERATION_ANNOTATIONS = listOf(
    REQUEST_MAPPING,
    GET_MAPPING,
    POST_MAPPING,
    PUT_MAPPING,
    DELETE_MAPPING,
    PATCH_MAPPING,
)
