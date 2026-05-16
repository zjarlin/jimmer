package site.addzero.lsi.jimmer.client.metadata.generator

import org.babyfish.jimmer.client.meta.Doc
import org.babyfish.jimmer.client.meta.Schema
import org.babyfish.jimmer.client.meta.TypeDefinition
import org.babyfish.jimmer.client.meta.TypeName
import org.babyfish.jimmer.client.meta.impl.EnumConstantImpl
import org.babyfish.jimmer.client.meta.impl.SchemaBuilder
import org.babyfish.jimmer.client.meta.impl.SchemaImpl
import org.babyfish.jimmer.client.meta.impl.Schemas
import org.babyfish.jimmer.client.meta.impl.TypeRefImpl
import site.addzero.lsi.codegen.GeneratedResourceArtifact
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
import java.io.StringWriter

/**
 * Client schema metadata -> resource artifact 生成器。
 *
 * 覆盖来源：`project/compiler/client/jimmer-ksp-client/.../ClientProcessor.onFinish`
 * 迁移说明：Client generator 只消费纯 `ClientSchemaMetadata`，再物化回 runtime schema 并序列化，
 * 不再直接接收 `LsiClass` / `SchemaBuilder<LsiClass>` / `Context`。
 */
class ClientSchemaMetadataGenerator {

    fun generate(
        metadata: ClientSchemaMetadata,
    ): GeneratedResourceArtifact {
        val schema = ClientSchemaRuntimeBuilder().buildSchema(metadata)
        val writer = StringWriter()
        Schemas.writeTo(schema, writer)
        return GeneratedResourceArtifact(
            path = CLIENT_SCHEMA_RESOURCE_PATH,
            content = writer.toString(),
        )
    }

    private class ClientSchemaRuntimeBuilder : SchemaBuilder<String>(null) {

        override fun loadSource(typeName: String): String? = null

        override fun throwException(source: String, message: String) {
            throw IllegalStateException(message)
        }

        override fun fillDefinition(source: String) {
            throw UnsupportedOperationException(
                "ClientSchemaRuntimeBuilder does not resolve definitions from source symbols",
            )
        }

        fun buildSchema(
            metadata: ClientSchemaMetadata,
        ): Schema {
            val schema = current<SchemaImpl<String>>()
            for (serviceMetadata in metadata.services) {
                api(serviceMetadata.typeName, TypeName.parse(serviceMetadata.typeName)) { service ->
                    fillService(service, serviceMetadata)
                    schema.addApiService(service)
                }
            }
            for (definitionMetadata in metadata.definitions) {
                definition(definitionMetadata.typeName, TypeName.parse(definitionMetadata.typeName)) { definition ->
                    fillDefinition(definition, definitionMetadata)
                    schema.addTypeDefinition(definition)
                }
            }
            return schema
        }

        private fun fillService(
            service: org.babyfish.jimmer.client.meta.impl.ApiServiceImpl<String>,
            metadata: ClientServiceMetadata,
        ) {
            service.setGroups(metadata.groups.takeIf { it.isNotEmpty() })
            service.setDoc(metadata.doc.toRuntimeDoc())
            for (operationMetadata in metadata.operations) {
                operation(metadata.typeName, operationMetadata.name) { operation ->
                    fillOperation(operation, operationMetadata, metadata.typeName)
                    service.addOperation(operation)
                }
            }
        }

        private fun fillOperation(
            operation: org.babyfish.jimmer.client.meta.impl.ApiOperationImpl<String>,
            metadata: ClientOperationMetadata,
            serviceTypeName: String,
        ) {
            operation.setGroups(metadata.groups.takeIf { it.isNotEmpty() })
            operation.setDoc(metadata.doc.toRuntimeDoc())
            for (parameterMetadata in metadata.parameters) {
                parameter(serviceTypeName, parameterMetadata.name) { parameter ->
                    fillParameter(parameter, parameterMetadata)
                    operation.addParameter(parameter)
                }
            }
            metadata.returnType?.let { returnType ->
                operation.setReturnType(returnType.toRuntimeTypeRef())
            }
            operation.setExceptionTypeNames(
                metadata.exceptionTypeNames.map(TypeName::parse),
            )
            // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor.handleOperation 的 operation.key() 持久化
            // 迁移说明：generator 明确回填 metadata 已解析的 operation key，避免重新依赖 runtime 惰性推导顺序
            operation.setKey(metadata.key)
        }

        private fun fillParameter(
            parameter: org.babyfish.jimmer.client.meta.impl.ApiParameterImpl<String>,
            metadata: ClientParameterMetadata,
        ) {
            parameter.setOriginalIndex(metadata.originalIndex)
            parameter.setType(metadata.type.toRuntimeTypeRef())
        }

        private fun fillDefinition(
            definition: org.babyfish.jimmer.client.meta.impl.TypeDefinitionImpl<String>,
            metadata: ClientTypeDefinitionMetadata,
        ) {
            definition.setKind(TypeDefinition.Kind.valueOf(metadata.kind.name))
            definition.setApiIgnore(metadata.apiIgnore)
            definition.setDoc(metadata.doc.toRuntimeDoc())
            metadata.error?.let { error ->
                definition.setError(error.toRuntimeError())
            }
            definition.mergeGroups(metadata.groups.takeIf { it.isNotEmpty() })
            for (propertyMetadata in metadata.properties) {
                prop(metadata.typeName, propertyMetadata.name) { prop ->
                    fillProperty(prop, propertyMetadata)
                    definition.addProp(prop)
                }
            }
            for (superTypeMetadata in metadata.superTypes) {
                definition.addSuperType(superTypeMetadata.toRuntimeTypeRef())
            }
            for (constantMetadata in metadata.enumConstants) {
                definition.addEnumConstant(constantMetadata.toRuntimeEnumConstant(metadata.typeName))
            }
        }

        private fun fillProperty(
            prop: org.babyfish.jimmer.client.meta.impl.PropImpl<String>,
            metadata: ClientPropertyMetadata,
        ) {
            prop.setDoc(metadata.doc.toRuntimeDoc())
            prop.setType(metadata.type.toRuntimeTypeRef())
        }
    }

    companion object {
        private const val CLIENT_SCHEMA_RESOURCE_PATH = "META-INF/jimmer/client"
    }
}

private fun ClientTypeDefinitionErrorMetadata.toRuntimeError(): TypeDefinition.Error =
    TypeDefinition.Error(family, code)

private fun ClientEnumConstantMetadata.toRuntimeEnumConstant(
    source: String,
): EnumConstantImpl<String> =
    EnumConstantImpl(source, name).apply {
        setDoc(this@toRuntimeEnumConstant.doc.toRuntimeDoc())
    }

private fun ClientTypeRefMetadata.toRuntimeTypeRef(): TypeRefImpl<String> =
    TypeRefImpl<String>().apply {
        setTypeName(TypeName.parse(this@toRuntimeTypeRef.typeName))
        setNullable(this@toRuntimeTypeRef.nullable)
        for (argument in this@toRuntimeTypeRef.arguments) {
            addArgument(argument.toRuntimeTypeRef())
        }
        this@toRuntimeTypeRef.fetchBy?.let(::setFetchBy)
        this@toRuntimeTypeRef.fetcherOwnerTypeName?.let { ownerTypeName ->
            setFetcherOwner(TypeName.parse(ownerTypeName))
        }
        this@toRuntimeTypeRef.fetcherDoc?.let { doc ->
            setFetcherDoc(doc.toRuntimeDoc())
        }
    }

private fun String?.toRuntimeDoc(): Doc? =
    Doc.parse(this)
