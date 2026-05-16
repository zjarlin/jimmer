package site.addzero.lsi.jimmer.client.metadata.generator

import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.codegen.GeneratedResourceArtifact
import site.addzero.lsi.jimmer.client.LsiExportDocSupport
import site.addzero.lsi.jimmer.client.collectClientApiServiceTypeNames
import site.addzero.lsi.jimmer.client.metadata.extractor.ClientSchemaMetadataExtractionInput
import site.addzero.lsi.jimmer.client.metadata.extractor.ClientSchemaMetadataExtractor
import site.addzero.lsi.jimmer.client.metadata.extractor.readExistingClientSchema
import site.addzero.lsi.poet.LsiTypeName
import site.addzero.lsi.resolver.LsiResolver
import java.io.File

object ClientProcessorSupport {

    const val EXPORT_DOC_RESOURCE_COMMENT: String = ExportDocResourceGenerator.RESOURCE_COMMENT

    @JvmStatic
    fun collectClientSchemaServiceTypeNames(
        resolver: LsiResolver,
        delayedTypeNames: Collection<String>?,
        explicitClientApi: Boolean,
        matchesSourceFilters: (LsiClass) -> Boolean,
    ): Set<String> =
        collectClientApiServiceTypeNames(
            resolver = resolver,
            delayedTypeNames = delayedTypeNames,
            explicitClientApi = explicitClientApi,
            matchesSourceFilters = matchesSourceFilters,
        )

    @JvmStatic
    fun collectExportDocTypeNames(
        resolver: LsiResolver,
    ): Set<String> =
        LsiExportDocSupport.collectExportDocTypeNames(resolver)

    @JvmStatic
    fun generateClientSchemaArtifact(
        resolver: LsiResolver,
        explicitClientApi: Boolean,
        serviceTypeNames: Collection<String>,
        existingSchemaFile: File?,
        convertedLsiTypeNameOf: (LsiClass, String) -> LsiTypeName?,
        draftImplDocMapOf: (LsiClass, String, String) -> Map<String, String> = { _, _, _ -> emptyMap() },
    ): GeneratedResourceArtifact {
        val metadata = ClientSchemaMetadataExtractor().extract(
            ClientSchemaMetadataExtractionInput(
                resolver = resolver,
                explicitClientApi = explicitClientApi,
                serviceTypeNames = serviceTypeNames,
                existingSchema = readExistingClientSchema(existingSchemaFile),
                convertedLsiTypeNameOf = convertedLsiTypeNameOf,
                draftImplDocMapOf = draftImplDocMapOf,
            )
        )
        return ClientSchemaMetadataGenerator().generate(metadata)
    }

    @JvmStatic
    fun generateExportDocArtifact(
        resolver: LsiResolver,
        typeNames: Collection<String>,
    ): GeneratedResourceArtifact? {
        if (typeNames.isEmpty()) {
            return null
        }
        val declarations = LsiExportDocSupport.resolveExportDocDeclarations(resolver, typeNames)
        if (declarations.isEmpty()) {
            return null
        }
        return ExportDocResourceGenerator().generate(declarations)
    }
}
