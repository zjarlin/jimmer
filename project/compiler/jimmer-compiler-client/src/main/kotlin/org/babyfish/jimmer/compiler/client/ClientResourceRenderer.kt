package org.babyfish.jimmer.compiler.client

import java.io.StringWriter
import org.babyfish.jimmer.client.meta.Doc
import org.babyfish.jimmer.client.meta.TypeDefinition
import org.babyfish.jimmer.client.meta.TypeName
import org.babyfish.jimmer.client.meta.impl.ApiOperationImpl
import org.babyfish.jimmer.client.meta.impl.SchemaBuilder
import org.babyfish.jimmer.client.meta.impl.SchemaImpl
import org.babyfish.jimmer.client.meta.impl.Schemas
import org.babyfish.jimmer.client.meta.impl.TypeDefinitionImpl
import org.babyfish.jimmer.client.meta.impl.TypeRefImpl
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.jimmer.client.ClientArrayTypeRef
import site.addzero.lsi.jimmer.client.ClientDeclaredTypeRef
import site.addzero.lsi.jimmer.client.ClientDefinitionKind
import site.addzero.lsi.jimmer.client.ClientIgnoredParameter
import site.addzero.lsi.jimmer.client.ClientOperation
import site.addzero.lsi.jimmer.client.ClientParameter
import site.addzero.lsi.jimmer.client.ClientPrimitiveTypeRef
import site.addzero.lsi.jimmer.client.ClientSchema
import site.addzero.lsi.jimmer.client.ClientService
import site.addzero.lsi.jimmer.client.ClientTypeDefinition
import site.addzero.lsi.jimmer.client.ClientTypeName
import site.addzero.lsi.jimmer.client.ClientTypeParameterRef
import site.addzero.lsi.jimmer.client.ClientTypeRef
import site.addzero.lsi.jimmer.client.ClientUnresolvedTypeRef
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiVariance

class ClientResourceRenderer {

    fun render(schema: ClientSchema): String {
        val definitionsById = schema.definitions.associateBy(ClientTypeDefinition::id)
        val definitionIdsByTypeName = schema.definitions.associate { definition ->
            definition.typeName.qualifiedName to definition.id
        }
        val builder = object : SchemaBuilder<LsiSymbolId>(null) {
            override fun loadSource(typeName: String): LsiSymbolId? = definitionIdsByTypeName[typeName]

            override fun throwException(source: LsiSymbolId, message: String) {
                throw ClientResourceRenderException(source, message)
            }

            override fun fillDefinition(source: LsiSymbolId) {
                fillDefinition(this, definitionsById.getValue(source))
            }
        }
        val renderedSchema = builder.current<SchemaImpl<LsiSymbolId>>()
        schema.services.forEach { service ->
            builder.api(service.id, service.toTypeName()) { renderedService ->
                renderedService.groups = service.groups
                renderedService.doc = service.doc.toDoc()
                service.operations.forEach { operation ->
                    builder.operation(operation.id, operation.name) { renderedOperation ->
                        renderedOperation.groups = operation.groups
                        renderedOperation.doc = operation.doc.toDoc()
                        operation.renderParameters(builder, renderedOperation)
                        operation.returnType?.let { returnType ->
                            renderedOperation.setReturnType(returnType.toRenderedTypeRef())
                        }
                        renderedOperation.setExceptionTypeNames(
                            operation.exceptionTypeIds.map { typeId ->
                                requireNotNull(definitionsById[typeId]) {
                                    "Client exception definition is missing: ${typeId.value}"
                                }.typeName.toTypeName()
                            }
                        )
                        renderedService.addOperation(renderedOperation)
                    }
                }
                renderedSchema.addApiService(renderedService)
            }
        }
        val writer = StringWriter()
        Schemas.writeTo(builder.build(), writer)
        return writer.toString()
    }

    private fun fillDefinition(
        builder: SchemaBuilder<LsiSymbolId>,
        definition: ClientTypeDefinition,
    ) {
        val renderedDefinition = builder.current<TypeDefinitionImpl<LsiSymbolId>>()
        renderedDefinition.kind = when (definition.kind) {
            ClientDefinitionKind.IMMUTABLE -> TypeDefinition.Kind.IMMUTABLE
            ClientDefinitionKind.OBJECT -> TypeDefinition.Kind.OBJECT
            ClientDefinitionKind.ENUM -> TypeDefinition.Kind.ENUM
        }
        renderedDefinition.isApiIgnore = definition.apiIgnore
        renderedDefinition.doc = definition.doc.toDoc()
        renderedDefinition.error = definition.error?.let { error ->
            TypeDefinition.Error(error.family, error.code)
        }
        definition.properties.forEach { property ->
            builder.prop(property.id, property.name) { renderedProperty ->
                renderedProperty.setType(property.type.toRenderedTypeRef())
                renderedProperty.doc = property.doc.toDoc()
                renderedDefinition.addProp(renderedProperty)
            }
        }
        definition.superTypes.forEach { superType ->
            renderedDefinition.addSuperType(superType.toRenderedTypeRef())
        }
        definition.polymorphicBranches.forEach { branch ->
            renderedDefinition.addPolymorphicBranch(branch.toRenderedTypeRef())
        }
        definition.enumConstants.forEach { constant ->
            builder.constant(constant.id, constant.name) { renderedConstant ->
                renderedConstant.doc = constant.doc.toDoc()
                renderedDefinition.addEnumConstant(renderedConstant)
            }
        }
    }
}

class ClientResourceRenderException(
    val declarationId: LsiSymbolId,
    message: String,
) : IllegalArgumentException(message)

private fun ClientOperation.renderParameters(
    builder: SchemaBuilder<LsiSymbolId>,
    renderedOperation: ApiOperationImpl<LsiSymbolId>,
) {
    val parametersByIndex = parameters.associateBy(ClientParameter::originalIndex)
    val ignoredByIndex = ignoredParameters.associateBy(ClientIgnoredParameter::originalIndex)
    val indexes = (parametersByIndex.keys + ignoredByIndex.keys).sorted()
    indexes.forEach { index ->
        val parameter = parametersByIndex[index]
        val ignored = ignoredByIndex[index]
        check((parameter == null) != (ignored == null)) {
            "Client operation parameter index must be either rendered or ignored: ${id.value}#$index"
        }
        if (parameter != null) {
            builder.parameter(parameter.id, parameter.name) { renderedParameter ->
                renderedParameter.originalIndex = parameter.originalIndex
                renderedParameter.setType(parameter.type.toRenderedTypeRef())
                renderedOperation.addParameter(renderedParameter)
            }
        } else {
            requireNotNull(ignored)
            builder.parameter(ignored.id, ignored.name) { renderedParameter ->
                renderedParameter.originalIndex = ignored.originalIndex
                renderedOperation.addIgnoredParameter(renderedParameter)
            }
        }
    }
}

private fun ClientTypeRef.toRenderedTypeRef(): TypeRefImpl<LsiSymbolId> {
    val rendered = TypeRefImpl<LsiSymbolId>()
    var optionalTarget: TypeRefImpl<LsiSymbolId>? = null
    when (this) {
        is ClientDeclaredTypeRef -> {
            rendered.typeName = typeName.toTypeName()
            arguments.forEach { argument ->
                when (argument.variance) {
                    LsiVariance.STAR -> error("Client renderer does not accept star type arguments")
                    LsiVariance.IN -> error("Client renderer does not accept contravariant type arguments")
                    LsiVariance.INVARIANT,
                    LsiVariance.OUT,
                    -> {
                        val renderedArgument = requireNotNull(argument.type).toRenderedTypeRef()
                        rendered.addArgument(renderedArgument)
                        if (rendered.typeName == TypeName.OPTIONAL) {
                            optionalTarget = renderedArgument
                        }
                    }
                }
            }
        }
        is ClientPrimitiveTypeRef -> rendered.typeName = kind.toTypeName()
        is ClientArrayTypeRef -> {
            rendered.typeName = TypeName.LIST
            rendered.addArgument(elementType.toRenderedTypeRef())
        }
        is ClientTypeParameterRef -> {
            rendered.typeName = ownerTypeName.toTypeName().typeVariable(name)
        }
        is ClientUnresolvedTypeRef -> error("Cannot render unresolved client type '$displayName'")
    }
    rendered.isNullable = nullable
    fetchBy?.let { fetchBy ->
        rendered.fetchBy = fetchBy.value
        rendered.fetcherOwner = fetchBy.ownerTypeName.toTypeName()
        rendered.fetcherDoc = fetchBy.documentation.toDoc()
    }
    if (rendered.typeName == TypeName.OPTIONAL) {
        rendered.replaceBy(requireNotNull(optionalTarget), true)
    }
    return rendered
}

private fun ClientService.toTypeName(): TypeName {
    return ClientTypeName.parse(qualifiedName).toTypeName()
}

private fun ClientTypeName.toTypeName(): TypeName = TypeName.of(packageName, simpleNames)

private fun LsiPrimitiveKind.toTypeName(): TypeName {
    return when (this) {
        LsiPrimitiveKind.BOOLEAN -> TypeName.BOOLEAN
        LsiPrimitiveKind.BYTE -> TypeName.BYTE
        LsiPrimitiveKind.SHORT -> TypeName.SHORT
        LsiPrimitiveKind.INT -> TypeName.INT
        LsiPrimitiveKind.LONG -> TypeName.LONG
        LsiPrimitiveKind.CHAR -> TypeName.CHAR
        LsiPrimitiveKind.FLOAT -> TypeName.FLOAT
        LsiPrimitiveKind.DOUBLE -> TypeName.DOUBLE
        LsiPrimitiveKind.UNIT,
        LsiPrimitiveKind.VOID,
        -> TypeName.VOID
    }
}

private fun String?.toDoc(): Doc? = this?.takeIf(String::isNotBlank)?.let(Doc::parse)
