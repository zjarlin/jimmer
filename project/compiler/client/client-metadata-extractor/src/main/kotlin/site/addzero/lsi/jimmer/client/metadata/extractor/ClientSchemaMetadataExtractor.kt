package site.addzero.lsi.jimmer.client.metadata.extractor

import org.babyfish.jimmer.client.meta.ApiOperation
import org.babyfish.jimmer.client.meta.ApiParameter
import org.babyfish.jimmer.client.meta.ApiService
import org.babyfish.jimmer.client.meta.Doc
import org.babyfish.jimmer.client.meta.EnumConstant
import org.babyfish.jimmer.client.meta.Prop
import org.babyfish.jimmer.client.meta.Schema
import org.babyfish.jimmer.client.meta.TypeDefinition
import org.babyfish.jimmer.client.meta.TypeRef
import org.babyfish.jimmer.client.meta.impl.ApiOperationImpl
import org.babyfish.jimmer.client.meta.impl.SchemaBuilder
import org.babyfish.jimmer.client.meta.impl.Schemas
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.codegen.GeneratorException
import site.addzero.lsi.diagnostic.MetaException
import site.addzero.lsi.jimmer.client.ClientExceptionContext
import site.addzero.lsi.jimmer.client.DocMetadata
import site.addzero.lsi.jimmer.client.LsiClientSchemaMaterializationInput
import site.addzero.lsi.jimmer.client.LsiClientSchemaTraversalInput
import site.addzero.lsi.jimmer.client.fillClientDefinition
import site.addzero.lsi.jimmer.client.fillClientType
import site.addzero.lsi.jimmer.client.handleClientApiService
import site.addzero.lsi.jimmer.client.metadata.model.ClientEnumConstantMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientOperationMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientParameterMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientPropertyMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientSchemaMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientServiceMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientTypeDefinitionErrorMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientTypeDefinitionKindMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientTypeDefinitionMetadata
import site.addzero.lsi.jimmer.client.metadata.model.ClientTypeRefMetadata
import site.addzero.lsi.jimmer.client.resolveClientExceptionTypeNames
import site.addzero.lsi.jimmer.isJimmerType
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.resolver.LsiResolver
import java.io.File
import java.io.FileReader

data class ClientSchemaMetadataExtractionInput(
    val resolver: LsiResolver,
    val explicitClientApi: Boolean,
    val serviceTypeNames: Collection<String>,
    val existingSchema: Schema? = null,
    val convertedLsiTypeNameOf: (LsiClass, String) -> LsiTypeName?,
    val draftImplDocMapOf: (LsiClass, String, String) -> Map<String, String> = { _, _, _ -> emptyMap() },
)

/**
 * Client schema metadata 提取器。
 *
 * 覆盖来源：
 * - `project/compiler/client/jimmer-ksp-client/.../ClientProcessor`
 * - `project/compiler/client/jimmer-ksp-client/.../LsiClientSchemaTraversal`
 * - `project/compiler/client/jimmer-ksp-client/.../LsiClientSchemaMaterialization`
 *
 * 迁移说明：先复用现有 LSI-first traversal/materialization 生成 runtime schema，
 * 再统一抽成纯 `ClientSchemaMetadata`，把 processor 与 generator 一起从 `SchemaBuilder<LsiClass>` 收口出去。
 */
class ClientSchemaMetadataExtractor {
    fun extract(
        input: ClientSchemaMetadataExtractionInput,
    ): ClientSchemaMetadata {
        val docMetadata = DocMetadata(input.draftImplDocMapOf)
        val clientExceptionContext = ClientExceptionContext()
        val materializationInput = LsiClientSchemaMaterializationInput(
            docMetadata = docMetadata,
            clientExceptionContext = clientExceptionContext,
            resolver = input.resolver,
            convertedLsiTypeNameOf = input.convertedLsiTypeNameOf,
        )
        val builder = createBuilder(
            existingSchema = input.existingSchema,
            materializationInput = materializationInput,
        )
        val traversalInput = LsiClientSchemaTraversalInput(
            explicitClientApi = input.explicitClientApi,
            docMetadata = docMetadata,
            getExceptionTypeNames = { method ->
                resolveClientExceptionTypeNames(method, clientExceptionContext)
            },
            fillType = { type ->
                fillClientType(type, materializationInput)
            },
            throwMeta = { source, message ->
                throw MetaException(source, message)
            },
        )
        for (serviceTypeName in input.serviceTypeNames) {
            val lsiClass = input.resolver.findClassByQualifiedName(serviceTypeName) ?: continue
            builder.handleClientApiService(lsiClass, traversalInput)
        }
        return extract(builder.build())
    }

    fun extract(schema: Schema): ClientSchemaMetadata =
        ClientSchemaMetadata(
            services = schema.apiServiceMap.values.map { service ->
                service.toMetadata()
            },
            definitions = schema.typeDefinitionMap.values.map { definition ->
                definition.toMetadata()
            },
        )

    private fun createBuilder(
        existingSchema: Schema?,
        materializationInput: LsiClientSchemaMaterializationInput,
    ): SchemaBuilder<LsiClass> =
        object : SchemaBuilder<LsiClass>(existingSchema) {

            override fun loadSource(typeName: String): LsiClass? =
                materializationInput.resolver.findClassByQualifiedName(typeName)

            override fun throwException(source: LsiClass, message: String) {
                throw MetaException(source, message)
            }

            override fun fillDefinition(source: LsiClass) {
                fillClientDefinition(
                    declaration = source,
                    // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.fillDefinition immutable 判定
                    // 迁移说明：immutable 判定下沉到 metadata extractor 内部 builder override，processor 不再直接装配这条分支
                    immutable = source.isJimmerType,
                    input = materializationInput,
                )
            }
        }

    private fun ApiService.toMetadata(): ClientServiceMetadata =
        ClientServiceMetadata(
            typeName = typeName.toString(),
            groups = groups.orEmpty(),
            doc = doc.toMetadataString(),
            operations = operations.map { operation ->
                operation.toMetadata()
            },
        )

    private fun ApiOperation.toMetadata(): ClientOperationMetadata =
        ClientOperationMetadata(
            name = name,
            // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleOperation 的 operation.key() 输出语义
            // 迁移说明：metadata 提取优先复用 runtime `ApiOperationImpl.key()`，避免再用 `toString()` 反推签名键
            key = metadataKey(),
            groups = groups.orEmpty(),
            doc = doc.toMetadataString(),
            parameters = parameters.map { parameter ->
                parameter.toMetadata()
            },
            returnType = returnType?.toMetadata(),
            exceptionTypeNames = exceptionTypes.map { typeRef ->
                typeRef.typeName.toString()
            },
        )

    private fun ApiOperation.metadataKey(): String =
        when (this) {
            is ApiOperationImpl<*> -> key()
            else -> buildString {
                append(name)
                for (parameter in parameters) {
                    append(':')
                    append(parameter.type.typeName.toString())
                }
            }
        }

    private fun ApiParameter.toMetadata(): ClientParameterMetadata =
        ClientParameterMetadata(
            name = name,
            originalIndex = originalIndex,
            type = type.toMetadata(),
        )

    private fun TypeDefinition.toMetadata(): ClientTypeDefinitionMetadata =
        ClientTypeDefinitionMetadata(
            typeName = typeName.toString(),
            kind = kind?.let { ClientTypeDefinitionKindMetadata.valueOf(it.name) }
                ?: ClientTypeDefinitionKindMetadata.OBJECT,
            apiIgnore = isApiIgnore,
            doc = doc.toMetadataString(),
            error = error?.let {
                ClientTypeDefinitionErrorMetadata(
                    family = it.family,
                    code = it.code,
                )
            },
            groups = groups.orEmpty(),
            properties = propMap.values.map { prop ->
                prop.toMetadata()
            },
            superTypes = superTypes.map { superType ->
                superType.toMetadata()
            },
            enumConstants = enumConstantMap.values.map { enumConstant ->
                enumConstant.toMetadata()
            },
        )

    private fun Prop.toMetadata(): ClientPropertyMetadata =
        ClientPropertyMetadata(
            name = name,
            doc = doc.toMetadataString(),
            type = type.toMetadata(),
        )

    private fun EnumConstant.toMetadata(): ClientEnumConstantMetadata =
        ClientEnumConstantMetadata(
            name = name,
            doc = doc.toMetadataString(),
        )

    private fun TypeRef.toMetadata(): ClientTypeRefMetadata =
        ClientTypeRefMetadata(
            typeName = typeName.toString(),
            nullable = isNullable,
            arguments = arguments.map { argument ->
                argument.toMetadata()
            },
            fetchBy = fetchBy,
            fetcherOwnerTypeName = fetcherOwner?.toString(),
            fetcherDoc = fetcherDoc.toMetadataString(),
        )

    private fun Doc?.toMetadataString(): String? =
        this?.toString()
}

fun readExistingClientSchema(
    file: File?,
): Schema? =
    file
        ?.takeIf { it.exists() }
        ?.let { resourceFile ->
            try {
                FileReader(resourceFile).use(Schemas::readServicesFrom)
            } catch (ex: Exception) {
                // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.existingSchema 旧资源读取
                // 迁移说明：已有 client schema 资源读取下沉到 metadata extractor 模块，processor 入口只保留 orchestration
                throw GeneratorException("Cannot read content of \"$resourceFile\"", ex)
            }
        }
