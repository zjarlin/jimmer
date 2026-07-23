package site.addzero.lsi.jimmer.exportdoc

import site.addzero.lsi.core.LsiLanguage
import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOriginKind
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationScope
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiField
import site.addzero.lsi.model.LsiPrimitiveKind
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiProperty
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

/** 将冻结的 LSI 工作区解析为 ExportDoc 领域语义。 */
fun LsiWorkspace.toExportDocSchema(): ExportDocSchema {
    val packageConfigurations = exportDocPackageConfigurations()
    val typesById = declarationsOfType<LsiTypeDeclaration>().associateBy(LsiTypeDeclaration::id)
    val decisions = mutableMapOf<LsiSymbolId, ExportDocDecision?>()
    val effectiveConfigurationIds = sortedSetOf<LsiSymbolId>()
    val exportedTypes = mutableListOf<LsiTypeDeclaration>()

    fun decision(type: LsiTypeDeclaration): ExportDocDecision? {
        if (type.id in decisions) {
            return decisions[type.id]
        }
        val directDecision = type.exportDocConfiguration()?.let { exported ->
            ExportDocDecision(type.id, exported)
        }
        val inheritedDecision = if (directDecision == null) {
            type.enclosingTypeId
                ?.let(typesById::get)
                ?.let(::decision)
                ?: type.nearestPackageConfiguration(typesById, packageConfigurations)
        } else {
            null
        }
        return (directDecision ?: inheritedDecision).also { resolved ->
            decisions[type.id] = resolved
        }
    }

    typesById.values
        .asSequence()
        .filter { type -> type.isExportDocCandidate(typesById) }
        .sortedBy(LsiTypeDeclaration::id)
        .forEach { type ->
            val typeDecision = decision(type) ?: return@forEach
            effectiveConfigurationIds += typeDecision.configurationId
            if (typeDecision.exported) {
                exportedTypes += type
            }
        }

    val entriesByKey = sortedMapOf<String, ExportDocEntry>()
    exportedTypes.forEach { type ->
        type.sourceDocumentation.standardDocumentation()?.let { documentation ->
            entriesByKey[type.qualifiedName] = ExportDocEntry(
                declarationId = type.id,
                key = type.qualifiedName,
                content = documentation,
            )
        }
        type.exportMemberDocs(this, entriesByKey)
    }
    return ExportDocSchema(
        effectiveConfigurationIds = effectiveConfigurationIds.toList(),
        exportedTypeIds = exportedTypes.map(LsiTypeDeclaration::id).sorted(),
        entries = entriesByKey.values.toList(),
    )
}

/** 表示冻结工作区内相互冲突的 ExportDoc 配置。 */
class ExportDocValidationException(
    val scopeIds: List<LsiSymbolId>,
    val location: LsiLocation?,
    message: String,
) : IllegalArgumentException(message) {
    init {
        require(scopeIds.isNotEmpty()) { "ExportDoc failure must reference at least one scope" }
    }
}

private data class ExportDocPackageConfiguration(
    val scope: LsiAnnotationScope,
    val exported: Boolean,
)

private data class ExportDocDecision(
    val configurationId: LsiSymbolId,
    val exported: Boolean,
)

private fun LsiWorkspace.exportDocPackageConfigurations(): Map<String, ExportDocPackageConfiguration> {
    return annotationScopes
        .mapNotNull { scope ->
            scope.exportDocConfiguration()?.let { exported ->
                ExportDocPackageConfiguration(scope, exported)
            }
        }
        .groupBy { configuration -> configuration.scope.packageName }
        .toSortedMap()
        .mapValues { (packageName, configurations) ->
            val sortedConfigurations = configurations.sortedBy { configuration -> configuration.scope.id }
            if (sortedConfigurations.size > 1) {
                val scopes = sortedConfigurations.map { configuration -> configuration.scope }
                throw ExportDocValidationException(
                    scopeIds = scopes.map(LsiAnnotationScope::id),
                    location = scopes.first().location,
                    message = "Conflicting @ExportDoc configurations for package '$packageName': " +
                        scopes.joinToString { scope -> scope.id.value },
                )
            }
            sortedConfigurations.single()
        }
}

private fun LsiTypeDeclaration.nearestPackageConfiguration(
    typesById: Map<LsiSymbolId, LsiTypeDeclaration>,
    configurations: Map<String, ExportDocPackageConfiguration>,
): ExportDocDecision? {
    var topLevelType = this
    while (topLevelType.enclosingTypeId != null) {
        topLevelType = typesById[topLevelType.enclosingTypeId] ?: return null
    }
    var packageName = topLevelType.qualifiedName.substringBeforeLast('.', "")
    while (true) {
        configurations[packageName]?.let { configuration ->
            return ExportDocDecision(
                configurationId = configuration.scope.id,
                exported = configuration.exported,
            )
        }
        if (packageName.isEmpty()) {
            return null
        }
        packageName = packageName.substringBeforeLast('.', "")
    }
}

private fun LsiTypeDeclaration.exportMemberDocs(
    workspace: LsiWorkspace,
    destination: MutableMap<String, ExportDocEntry>,
) {
    memberIds
        .mapNotNull(workspace::get)
        .filterIsInstance<LsiField>()
        .filter { field -> !field.static && field.origin.language == LsiLanguage.JAVA }
        .forEach { field ->
            field.sourceDocumentation.standardDocumentation()?.let { documentation ->
                val key = "$qualifiedName.${field.name}"
                destination[key] = ExportDocEntry(field.id, key, documentation)
            }
        }
    memberIds
        .mapNotNull(workspace::get)
        .filterIsInstance<LsiProperty>()
        .filterNot(LsiProperty::static)
        .forEach { property ->
            val propertyName = property.exportedPropertyName() ?: return@forEach
            property.sourceDocumentation.standardDocumentation()?.let { documentation ->
                val key = "$qualifiedName.$propertyName"
                destination[key] = ExportDocEntry(property.id, key, documentation)
            }
        }
}

private fun LsiProperty.exportedPropertyName(): String? {
    if (origin.language != LsiLanguage.JAVA) {
        return name
    }
    val booleanType = (type as? LsiPrimitiveType)?.kind == LsiPrimitiveKind.BOOLEAN
    return getterName.javaPropertyName(booleanType)
}

private fun String.javaPropertyName(booleanType: Boolean): String? {
    if (length > 3 && startsWith("get") && !this[3].isLowerCase()) {
        return substring(3).javaIdentifier()
    }
    if (booleanType && length > 2 && startsWith("is") && !this[2].isLowerCase()) {
        return substring(2).javaIdentifier()
    }
    return null
}

private fun String.javaIdentifier(): String {
    if (first().isLowerCase()) {
        return this
    }
    val characters = toCharArray()
    for (index in characters.indices) {
        if (characters[index].isLowerCase()) {
            break
        }
        characters[index] = characters[index].lowercaseChar()
    }
    return characters.concatToString()
}

private fun LsiTypeDeclaration.isExportDocCandidate(
    typesById: Map<LsiSymbolId, LsiTypeDeclaration>,
): Boolean {
    if (origin.kind !in EXPORTABLE_ORIGIN_KINDS || kind !in EXPORTABLE_TYPE_KINDS) {
        return false
    }
    val visited = mutableSetOf<LsiSymbolId>()
    var enclosingId = enclosingTypeId
    while (enclosingId != null) {
        if (!visited.add(enclosingId)) {
            return false
        }
        val enclosingType = typesById[enclosingId] ?: return false
        if (enclosingType.kind !in EXPORTABLE_TYPE_KINDS) {
            return false
        }
        enclosingId = enclosingType.enclosingTypeId
    }
    return true
}

private fun LsiTypeDeclaration.exportDocConfiguration(): Boolean? =
    annotations.exportDocConfiguration()

private fun LsiAnnotationScope.exportDocConfiguration(): Boolean? =
    annotations.exportDocConfiguration()

private fun List<LsiAnnotation>.exportDocConfiguration(): Boolean? {
    val annotation = firstOrNull { annotation -> annotation.type == EXPORT_DOC_ANNOTATION } ?: return null
    val excluded = (annotation.arguments["excluded"]?.value as? LsiAnnotationValue.BooleanValue)?.value ?: false
    return !excluded
}

private fun String?.standardDocumentation(): String? {
    return this
        ?.replace("\r\n", "\n")
        ?.replace('\r', '\n')
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

private val EXPORT_DOC_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.client.ExportDoc")

private val EXPORTABLE_ORIGIN_KINDS = setOf(
    LsiOriginKind.SOURCE,
    LsiOriginKind.GENERATED,
)

private val EXPORTABLE_TYPE_KINDS = setOf(
    LsiTypeDeclarationKind.CLASS,
    LsiTypeDeclarationKind.INTERFACE,
    LsiTypeDeclarationKind.ENUM,
)
