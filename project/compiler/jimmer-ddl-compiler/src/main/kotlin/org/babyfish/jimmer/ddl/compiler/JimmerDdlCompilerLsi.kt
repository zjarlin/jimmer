package org.babyfish.jimmer.ddl.compiler

import org.babyfish.jimmer.ddl.generator.model.AutoDdlColumn
import org.babyfish.jimmer.ddl.generator.model.AutoDdlForeignKey
import org.babyfish.jimmer.ddl.generator.model.AutoDdlIndex
import org.babyfish.jimmer.ddl.generator.model.AutoDdlIndexType
import org.babyfish.jimmer.ddl.generator.model.AutoDdlJunction
import org.babyfish.jimmer.ddl.generator.model.AutoDdlLogicalType
import org.babyfish.jimmer.ddl.generator.model.AutoDdlSchema
import org.babyfish.jimmer.ddl.generator.model.AutoDdlSequence
import org.babyfish.jimmer.ddl.generator.model.AutoDdlTable
import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.anno.LsiAnnotationValue
import site.addzero.lsi.type.LsiArrayType
import site.addzero.lsi.type.LsiDeclaredType
import site.addzero.lsi.type.LsiNullability
import site.addzero.lsi.type.LsiPrimitiveKind
import site.addzero.lsi.type.LsiPrimitiveType
import site.addzero.lsi.model.LsiResolvedProperty
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.type.LsiType
import site.addzero.lsi.model.LsiTypeSystem
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiWorkspace

internal fun LsiWorkspace.jimmerEntityTypes(): List<LsiClass> {
    return declarationsOfType<LsiClass>()
        .filter(LsiClass::isJimmerEntity)
        .distinctBy(LsiClass::id)
        .sortedBy(LsiClass::qualifiedName)
}

internal fun buildJimmerDdlSchema(
    workspace: LsiWorkspace,
    entities: List<LsiClass>,
    relationTargetWorkspace: LsiWorkspace,
    settings: JimmerDdlCompilerSettings,
): AutoDdlSchema {
    return JimmerDdlSchemaBuilder(
        workspace = workspace,
        relationTargetWorkspace = relationTargetWorkspace,
    ).build(entities, settings)
}

internal fun LsiClass.jimmerTableName(): String {
    return annotations.annotation("Table")
        ?.stringValue("name")
        ?.takeIf(String::isNotBlank)
        ?: name.toJimmerSnakeCase()
}

private class JimmerDdlSchemaBuilder(
    workspace: LsiWorkspace,
    relationTargetWorkspace: LsiWorkspace,
) {
    private val workspace = mergeWorkspaces(
        primary = workspace,
        secondary = relationTargetWorkspace,
    )

    private val typeSystem = LsiTypeSystem(this.workspace)

    private val effectiveProperties = mutableMapOf<LsiSymbolId, List<LsiResolvedProperty>>()

    fun build(
        entities: List<LsiClass>,
        settings: JimmerDdlCompilerSettings,
    ): AutoDdlSchema {
        val tables = entities.map { entity ->
            entity.toAutoDdlTable()
        }
        val sequences = entities.mapNotNull { entity ->
            entity.allProperties()
                .firstOrNull { property -> property.isIdProperty() }
                ?.sequenceName()
                ?.takeIf(String::isNotBlank)
        }.distinct().map(::AutoDdlSequence)
        val junctionTables = if (settings.includeManyToManyTables) {
            scanManyToManyTables(entities)
        } else {
            emptyList()
        }
        return AutoDdlSchema(
            tables = (tables + junctionTables).distinctBy { table -> table.name.lowercase() },
            sequences = sequences,
        )
    }

    private fun LsiClass.toAutoDdlTable(): AutoDdlTable {
        val joinedRoot = joinedInheritanceRoot()
            ?.takeUnless { root -> root.id == id }
        val rootIdProperty = joinedRoot?.allProperties()?.firstOrNull { property -> property.isIdProperty() }
        val tableProperties = tablePropertiesForPhysicalTable(joinedRoot, rootIdProperty)
        val scalarColumns = mutableListOf<AutoDdlColumn>()
        val foreignKeys = mutableListOf<AutoDdlForeignKey>()

        for (property in tableProperties) {
            when {
                property.shouldSkipProperty() -> Unit
                property.isOwningAssociation() -> {
                    val referencedType = property.resolveAssociationTarget() ?: continue
                    val referencedId = referencedType.allProperties().firstOrNull { property -> property.isIdProperty() }
                    val columnName = property.joinColumnName()
                        ?.takeIf(String::isNotBlank)
                        ?: "${property.declaration.name}_id"
                    val referencedColumnName = property.referencedColumnName()
                        ?.takeIf(String::isNotBlank)
                        ?: referencedId?.columnName()?.takeIf(String::isNotBlank)
                        ?: referencedId?.declaration?.name?.takeIf(String::isNotBlank)
                        ?: "id"
                    scalarColumns += AutoDdlColumn(
                        name = columnName,
                        logicalType = referencedId?.toLogicalType() ?: AutoDdlLogicalType.INT64,
                        nullable = property.isNullable(),
                        comment = property.declaration.documentation,
                    )
                    if (!property.isFakeForeignKey()) {
                        foreignKeys += AutoDdlForeignKey(
                            name = "fk_${jimmerTableName()}_$columnName",
                            columnNames = listOf(columnName),
                            referencedTableName = referencedType.jimmerTableName(),
                            referencedColumnNames = listOf(referencedColumnName),
                        )
                    }
                }
                property.isOwningManyToMany() -> Unit
                property.isEmbeddedProperty() -> {
                    scalarColumns += property.toEmbeddedColumns()
                }
                else -> {
                    val column = property.toColumn()
                    scalarColumns += if (joinedRoot != null && property.sameDeclaration(rootIdProperty)) {
                        column.copy(autoIncrement = false, sequenceName = null)
                    } else {
                        column
                    }
                }
            }
        }
        if (joinedRoot != null && rootIdProperty != null) {
            val idColumnName = rootIdProperty.columnName()
            foreignKeys += AutoDdlForeignKey(
                name = "fk_${jimmerTableName()}_$idColumnName",
                columnNames = listOf(idColumnName),
                referencedTableName = joinedRoot.jimmerTableName(),
                referencedColumnNames = listOf(idColumnName),
            )
        }
        return AutoDdlTable(
            name = jimmerTableName(),
            columns = scalarColumns.distinctBy { column -> column.name.lowercase() },
            foreignKeys = foreignKeys,
            indexes = buildIndexes(this, scalarColumns, tableProperties),
            comment = documentation,
        )
    }

    private fun buildIndexes(
        entity: LsiClass,
        columns: List<AutoDdlColumn>,
        properties: List<LsiResolvedProperty>,
    ): List<AutoDdlIndex> {
        val mappedProperties = properties.filter { property ->
            columns.any { column -> column.name.equals(property.columnName(), ignoreCase = true) }
        }
        val groupedKeys = mappedProperties
            .filter { property -> property.hasAnnotation("Key") && !property.isIdProperty() }
            .groupBy { property -> property.annotationValue("Key", "group").orEmpty() }
        return buildList {
            for ((groupName, groupedProperties) in groupedKeys) {
                if (groupedProperties.any { property -> property.isNullable() }) {
                    continue
                }
                val columnNames = groupedProperties.map { property -> property.columnName() }
                if (columnNames.isEmpty()) {
                    continue
                }
                val indexName = if (groupName.isBlank()) {
                    "uk_${entity.jimmerTableName()}_${columnNames.joinToString("_")}"
                } else {
                    "uk_${entity.jimmerTableName()}_$groupName"
                }
                add(
                    AutoDdlIndex(
                        name = indexName,
                        columnNames = columnNames,
                        type = AutoDdlIndexType.UNIQUE,
                    )
                )
            }
            mappedProperties
                .filter { property -> property.isUnique() && !property.hasAnnotation("Key") }
                .forEach { property ->
                    val columnName = property.columnName()
                    add(
                        AutoDdlIndex(
                            name = "uk_${entity.jimmerTableName()}_$columnName",
                            columnNames = listOf(columnName),
                            type = AutoDdlIndexType.UNIQUE,
                        )
                    )
                }
        }.distinctBy { index -> index.name.lowercase() }
    }

    private fun scanManyToManyTables(
        entities: List<LsiClass>,
    ): List<AutoDdlTable> {
        val relationTargetEntities = workspace.jimmerEntityTypes()
        val relationTargetById = relationTargetEntities.associateBy(LsiClass::id)
        return entities.flatMap { entity ->
            val ownerIdProperty = entity.allProperties().firstOrNull { property -> property.isIdProperty() }
                ?: return@flatMap emptyList()
            entity.allProperties()
                .filter { property -> property.isOwningManyToMany() }
                .mapNotNull { property ->
                    val targetId = property.collectionElementDeclarationId() ?: return@mapNotNull null
                    val targetType = relationTargetById[targetId] ?: return@mapNotNull null
                    val targetIdProperty = targetType.allProperties().firstOrNull { targetProperty -> targetProperty.isIdProperty() }
                        ?: return@mapNotNull null
                    val ownerToken = entity.name.toJimmerSnakeCase()
                    val targetToken = targetType.name.toJimmerSnakeCase()
                    val joinTable = property.annotations.annotation("JoinTable")
                    val leftColumnName = joinTable?.stringValue("joinColumnName")
                        ?.takeIf(String::isNotBlank)
                        ?: "${ownerToken}_id"
                    val rightColumnName = joinTable?.stringValue("inverseJoinColumnName")
                        ?.takeIf(String::isNotBlank)
                        ?: "${targetToken}_id"
                    val filterColumn = joinTable?.nestedAnnotation("filter")
                        ?.stringValue("columnName")
                        ?.takeIf { columnName ->
                            columnName.isNotBlank() && columnName != "<illegal-column-name>"
                        }
                        ?.let { columnName ->
                            AutoDdlColumn(
                                name = columnName,
                                logicalType = AutoDdlLogicalType.TEXT,
                                nullable = false,
                                primaryKey = true,
                            )
                        }
                    AutoDdlTable(
                        name = joinTable?.stringValue("name")
                            ?.takeIf(String::isNotBlank)
                            ?: "${ownerToken}_${property.declaration.name.toJimmerSnakeCase()}_mapping",
                        columns = listOf(
                            AutoDdlColumn(
                                name = leftColumnName,
                                logicalType = ownerIdProperty.toLogicalType(),
                                nullable = false,
                                primaryKey = true,
                            ),
                            AutoDdlColumn(
                                name = rightColumnName,
                                logicalType = targetIdProperty.toLogicalType(),
                                nullable = false,
                                primaryKey = true,
                            ),
                        ) + listOfNotNull(filterColumn),
                        junction = AutoDdlJunction(
                            leftTableName = entity.jimmerTableName(),
                            rightTableName = targetType.jimmerTableName(),
                            leftColumnName = leftColumnName,
                            rightColumnName = rightColumnName,
                        ),
                    )
                }
        }
    }

    private fun LsiClass.allProperties(): List<LsiResolvedProperty> {
        return effectiveProperties.getOrPut(id) {
            typeSystem.effectiveProperties(id)
        }
    }

    private fun LsiClass.tablePropertiesForPhysicalTable(
        joinedRoot: LsiClass?,
        rootIdProperty: LsiResolvedProperty?,
    ): List<LsiResolvedProperty> {
        if (joinedRoot == null) {
            return allProperties()
        }
        val declaredProperties = allProperties().filter { property ->
            property.declaration.ownerId == id
        }
        return (listOfNotNull(rootIdProperty) + declaredProperties)
            .distinctBy { property -> property.declaration.name }
    }

    private fun LsiClass.joinedInheritanceRoot(
        visited: MutableSet<LsiSymbolId> = linkedSetOf(),
    ): LsiClass? {
        if (!visited.add(id)) {
            return null
        }
        if (isJoinedInheritanceRoot()) {
            return this
        }
        return superTypes
            .filterIsInstance<LsiDeclaredType>()
            .firstNotNullOfOrNull { superType ->
                val declaration = workspace[superType.declarationId] as? LsiClass
                    ?: return@firstNotNullOfOrNull null
                declaration.joinedInheritanceRoot(visited)
            }
    }

    private fun LsiClass.isJoinedInheritanceRoot(): Boolean {
        return annotations.annotation("Inheritance")
            ?.stringValue("strategy")
            ?.equals("JOINED", ignoreCase = true) == true
    }

    private fun LsiResolvedProperty.toColumn(): AutoDdlColumn {
        return AutoDdlColumn(
            name = columnName(),
            logicalType = toLogicalType(),
            nullable = isNullable(),
            length = length(),
            precision = precision(),
            scale = scale(),
            defaultValue = defaultValue(),
            comment = declaration.documentation,
            primaryKey = isIdProperty(),
            autoIncrement = isAutoIncrement(),
            sequenceName = sequenceName(),
            nativeTypeHint = nativeTypeHint(),
        )
    }

    private fun LsiResolvedProperty.toEmbeddedColumns(
        path: String = "",
        parentNullable: Boolean = false,
        inheritedOverrides: Map<String, String> = emptyMap(),
        visitedTypeIds: Set<LsiSymbolId> = emptySet(),
    ): List<AutoDdlColumn> {
        val embeddedType = embeddedType() ?: return listOf(toColumn())
        if (embeddedType.id in visitedTypeIds) {
            return emptyList()
        }
        val currentPath = path.takeIf(String::isNotBlank)
        val overrides = inheritedOverrides + propOverrides().mapKeys { (relativePath, _) ->
            listOfNotNull(currentPath, relativePath).joinToString(".")
        }
        val nullable = parentNullable || isNullable()
        return embeddedType.allProperties().flatMap { property ->
            if (property.shouldSkipProperty()) {
                return@flatMap emptyList()
            }
            val propertyPath = listOfNotNull(currentPath, property.declaration.name).joinToString(".")
            if (property.isEmbeddedProperty()) {
                property.toEmbeddedColumns(
                    path = propertyPath,
                    parentNullable = nullable,
                    inheritedOverrides = overrides,
                    visitedTypeIds = visitedTypeIds + embeddedType.id,
                )
            } else {
                listOf(
                    property.toColumn().copy(
                        name = overrides[propertyPath] ?: property.columnName(),
                        nullable = nullable || property.isNullable(),
                    )
                )
            }
        }
    }

    private fun LsiResolvedProperty.toLogicalType(): AutoDdlLogicalType {
        if (isJsonType()) {
            return AutoDdlLogicalType.JSON
        }
        return when (val propertyType = type) {
            is LsiPrimitiveType -> propertyType.kind.toLogicalType()
            is LsiArrayType -> {
                if ((propertyType.elementType as? LsiPrimitiveType)?.kind == LsiPrimitiveKind.BYTE) {
                    AutoDdlLogicalType.BINARY
                } else {
                    AutoDdlLogicalType.UNKNOWN
                }
            }
            is LsiDeclaredType -> {
                val declaration = workspace[propertyType.declarationId] as? LsiClass
                if (declaration?.kind == LsiTypeDeclarationKind.ENUM) {
                    if (declaration.annotations.annotation("EnumType")
                            ?.stringValue("value")
                            ?.equals("ORDINAL", ignoreCase = true) == true
                    ) {
                        AutoDdlLogicalType.INT32
                    } else {
                        AutoDdlLogicalType.STRING
                    }
                } else {
                    propertyType.declarationId.typeQualifiedName().toLogicalType(isTextType())
                }
            }
            else -> AutoDdlLogicalType.UNKNOWN
        }
    }

    private fun LsiResolvedProperty.shouldSkipProperty(): Boolean {
        return declaration.static ||
            hasAnnotation("Transient", "Formula", "ManyToManyView", "IdView") ||
            (isCollection() && !isOwningManyToMany() && !isSerializedScalar())
    }

    private fun LsiResolvedProperty.isOwningAssociation(): Boolean {
        if (!hasAnnotation("ManyToOne", "OneToOne")) {
            return false
        }
        return annotationValue("ManyToOne", "mappedBy").isNullOrBlank() &&
            annotationValue("OneToOne", "mappedBy").isNullOrBlank()
    }

    private fun LsiResolvedProperty.resolveAssociationTarget(): LsiClass? {
        val targetId = associationTargetDeclarationId() ?: return null
        return (workspace[targetId] as? LsiClass)
            ?.takeIf(LsiClass::isJimmerEntity)
    }

    private fun LsiResolvedProperty.associationTargetDeclarationId(): LsiSymbolId? {
        val propertyType = type as? LsiDeclaredType ?: return null
        return if (isCollection()) {
            propertyType.arguments.firstOrNull()?.type.declaredTypeId()
        } else {
            propertyType.declarationId
        }
    }

    private fun LsiResolvedProperty.collectionElementDeclarationId(): LsiSymbolId? {
        val propertyType = type as? LsiDeclaredType ?: return null
        return propertyType.arguments.firstOrNull()?.type.declaredTypeId()
    }

    private fun LsiResolvedProperty.isNullable(): Boolean {
        if (annotationValue("ManyToOne", "inputNotNull")?.toBooleanStrictOrNull() == true ||
            annotationValue("OneToOne", "inputNotNull")?.toBooleanStrictOrNull() == true
        ) {
            return false
        }
        if (type is LsiPrimitiveType) {
            return false
        }
        if (type.nullability == LsiNullability.NULLABLE) {
            return true
        }
        return hasAnnotation("Nullable", "Null", "TNullable")
    }

    private fun LsiResolvedProperty.isCollection(): Boolean {
        val declaredType = type as? LsiDeclaredType ?: return false
        return declaredType.declarationId.typeQualifiedName() in COLLECTION_TYPE_NAMES
    }

    private fun LsiResolvedProperty.isIdProperty(): Boolean {
        return hasAnnotation("Id") || declaration.name.equals("id", ignoreCase = true)
    }

    private fun LsiResolvedProperty.isOwningManyToMany(): Boolean {
        return hasAnnotation("ManyToMany") && annotationValue("ManyToMany", "mappedBy").isNullOrBlank()
    }

    private fun LsiResolvedProperty.isEmbeddedProperty(): Boolean {
        return embeddedType() != null
    }

    private fun LsiResolvedProperty.embeddedType(): LsiClass? {
        val declarationId = (type as? LsiDeclaredType)?.declarationId ?: return null
        return (workspace[declarationId] as? LsiClass)?.takeIf { declaration ->
            declaration.annotations.annotation("Embeddable") != null
        }
    }

    private fun LsiResolvedProperty.propOverrides(): Map<String, String> {
        return annotations.flatMap { annotation ->
            when (annotation.simpleName()) {
                "PropOverride" -> listOf(annotation)
                "PropOverrides" -> annotation.nestedAnnotations("value")
                else -> emptyList()
            }
        }.mapNotNull { annotation ->
            val prop = annotation.stringValue("prop")?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            val columnName = annotation.stringValue("columnName")?.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            prop to columnName
        }.toMap()
    }

    private fun LsiResolvedProperty.isFakeForeignKey(): Boolean {
        return annotationValue("JoinColumn", "foreignKeyType")
            ?.equals("FAKE", ignoreCase = true) == true
    }

    private fun LsiResolvedProperty.isAutoIncrement(): Boolean {
        if (!hasAnnotation("GeneratedValue") || hasCustomIdGenerator()) {
            return false
        }
        val strategy = annotationValue("GeneratedValue", "strategy")
        return strategy.isNullOrBlank() ||
            strategy.equals("IDENTITY", ignoreCase = true) ||
            strategy.equals("AUTO", ignoreCase = true)
    }

    private fun LsiResolvedProperty.hasCustomIdGenerator(): Boolean {
        if (!annotationValue("GeneratedValue", "generatorRef").isNullOrBlank()) {
            return true
        }
        val generatorType = annotationValue("GeneratedValue", "generatorType") ?: return false
        return !generatorType.endsWith("UserIdGenerator.None") &&
            !generatorType.endsWith(".None") &&
            !generatorType.endsWith("$" + "None") &&
            !generatorType.equals("None", ignoreCase = true)
    }

    private fun LsiResolvedProperty.sequenceName(): String? {
        if (annotationValue("GeneratedValue", "strategy")?.equals("SEQUENCE", ignoreCase = true) != true) {
            return null
        }
        return annotationValue("GeneratedValue", "sequenceName")?.takeIf(String::isNotBlank)
    }

    private fun LsiResolvedProperty.length(): Int? {
        return annotationValue("Length", "value")?.toIntOrNull()
            ?: annotationValue("Length", "max")?.toIntOrNull()
            ?: annotationValue("Column", "length")?.toIntOrNull()
    }

    private fun LsiResolvedProperty.precision(): Int? {
        return annotationValue("Column", "precision")?.toIntOrNull()
            ?: annotationValue("Precision", "value")?.toIntOrNull()
    }

    private fun LsiResolvedProperty.scale(): Int? {
        return annotationValue("Column", "scale")?.toIntOrNull()
            ?: annotationValue("Scale", "value")?.toIntOrNull()
    }

    private fun LsiResolvedProperty.isTextType(): Boolean {
        val explicitLength = length()
        return explicitLength != null && explicitLength > 1000 ||
            hasAnnotation("Lob") ||
            annotationValue("Column", "sqlType")?.contains("TEXT", ignoreCase = true) == true ||
            annotationValue("Column", "sqlType")?.contains("CLOB", ignoreCase = true) == true ||
            annotationValue("Column", "columnDefinition")?.contains("TEXT", ignoreCase = true) == true ||
            annotationValue("Column", "columnDefinition")?.contains("CLOB", ignoreCase = true) == true
    }

    private fun LsiResolvedProperty.isJsonType(): Boolean {
        return isSerializedScalar() ||
            annotationValue("Column", "sqlType")?.contains("JSON", ignoreCase = true) == true ||
            annotationValue("Column", "columnDefinition")?.contains("JSON", ignoreCase = true) == true
    }

    private fun LsiResolvedProperty.isSerializedScalar(): Boolean {
        return hasAnnotation("Serialized")
    }

    private fun LsiResolvedProperty.nativeTypeHint(): String? {
        return annotationValue("Column", "sqlType")?.takeIf(String::isNotBlank)
            ?: annotationValue("Column", "columnDefinition")?.takeIf(String::isNotBlank)
    }

    private fun LsiResolvedProperty.isUnique(): Boolean {
        return hasAnnotation("Unique") ||
            annotationValue("Column", "unique")?.toBooleanStrictOrNull() == true
    }

    private fun LsiResolvedProperty.defaultValue(): String? {
        return annotationValue("DatabaseDefault", "value")?.takeIf(String::isNotBlank)
            ?: annotationValue("Default", "value")?.takeIf(String::isNotBlank)
    }

    private fun LsiResolvedProperty.columnName(): String {
        return annotationValue("Column", "name")
            ?.takeIf(String::isNotBlank)
            ?: declaration.name.toJimmerSnakeCase()
    }

    private fun LsiResolvedProperty.joinColumnName(): String? {
        return annotationValue("JoinColumn", "name")
    }

    private fun LsiResolvedProperty.referencedColumnName(): String? {
        return annotationValue("JoinColumn", "referencedColumnName")
    }

    private fun LsiResolvedProperty.hasAnnotation(vararg simpleNames: String): Boolean {
        return annotations.any { annotation ->
            annotation.simpleName() in simpleNames
        }
    }

    private fun LsiResolvedProperty.annotationValue(
        simpleName: String,
        attributeName: String,
    ): String? {
        return annotations.annotation(simpleName)?.stringValue(attributeName)
    }

    private fun LsiResolvedProperty.sameDeclaration(other: LsiResolvedProperty?): Boolean {
        return other != null && declaration.id == other.declaration.id
    }
}

private fun mergeWorkspaces(
    primary: LsiWorkspace,
    secondary: LsiWorkspace,
): LsiWorkspace {
    val declarations = linkedMapOf<LsiSymbolId, site.addzero.lsi.model.LsiDeclaration>()
    secondary.declarations.forEach { declaration -> declarations[declaration.id] = declaration }
    primary.declarations.forEach { declaration -> declarations[declaration.id] = declaration }
    return LsiWorkspace(
        sources = secondary.sources + primary.sources,
        declarations = declarations.values,
    )
}

private fun LsiClass.isJimmerEntity(): Boolean {
    return annotations.any { annotation ->
        annotation.type.typeQualifiedName() == JIMMER_ENTITY_ANNOTATION
    }
}

private fun List<LsiAnnotation>.annotation(simpleName: String): LsiAnnotation? {
    return firstOrNull { annotation -> annotation.simpleName() == simpleName }
}

private fun LsiAnnotation.simpleName(): String {
    return type.typeQualifiedName().substringAfterLast('.')
}

private fun LsiAnnotation.stringValue(name: String): String? {
    return arguments[name]?.value?.asText()
}

private fun LsiAnnotation.nestedAnnotation(name: String): LsiAnnotation? {
    return (arguments[name]?.value as? LsiAnnotationValue.NestedAnnotationValue)?.annotation
}

private fun LsiAnnotation.nestedAnnotations(name: String): List<LsiAnnotation> {
    return when (val value = arguments[name]?.value) {
        is LsiAnnotationValue.NestedAnnotationValue -> listOf(value.annotation)
        is LsiAnnotationValue.ArrayValue -> value.elements.mapNotNull { element ->
            (element as? LsiAnnotationValue.NestedAnnotationValue)?.annotation
        }
        else -> emptyList()
    }
}

private fun LsiAnnotationValue.asText(): String? {
    return when (this) {
        is LsiAnnotationValue.BooleanValue -> value.toString()
        is LsiAnnotationValue.ByteValue -> value.toString()
        is LsiAnnotationValue.ShortValue -> value.toString()
        is LsiAnnotationValue.IntValue -> value.toString()
        is LsiAnnotationValue.LongValue -> value.toString()
        is LsiAnnotationValue.FloatValue -> value.toString()
        is LsiAnnotationValue.DoubleValue -> value.toString()
        is LsiAnnotationValue.CharValue -> value.toString()
        is LsiAnnotationValue.StringValue -> value
        is LsiAnnotationValue.EnumValue -> entryName
        is LsiAnnotationValue.ClassValue -> type.declaredTypeId()?.typeQualifiedName()
        is LsiAnnotationValue.NestedAnnotationValue,
        is LsiAnnotationValue.ArrayValue,
        -> null
    }
}

private fun LsiType?.declaredTypeId(): LsiSymbolId? {
    return (this as? LsiDeclaredType)?.declarationId
}

private fun LsiSymbolId.typeQualifiedName(): String {
    return requireTypeQualifiedName()
}

private fun LsiPrimitiveKind.toLogicalType(): AutoDdlLogicalType {
    return when (this) {
        LsiPrimitiveKind.BOOLEAN -> AutoDdlLogicalType.BOOLEAN
        LsiPrimitiveKind.BYTE -> AutoDdlLogicalType.INT8
        LsiPrimitiveKind.SHORT -> AutoDdlLogicalType.INT16
        LsiPrimitiveKind.INT -> AutoDdlLogicalType.INT32
        LsiPrimitiveKind.LONG -> AutoDdlLogicalType.INT64
        LsiPrimitiveKind.CHAR -> AutoDdlLogicalType.CHAR
        LsiPrimitiveKind.FLOAT -> AutoDdlLogicalType.FLOAT32
        LsiPrimitiveKind.DOUBLE -> AutoDdlLogicalType.FLOAT64
        LsiPrimitiveKind.UNIT,
        LsiPrimitiveKind.VOID,
        -> AutoDdlLogicalType.UNKNOWN
    }
}

private fun String.toLogicalType(text: Boolean): AutoDdlLogicalType {
    return when (this) {
        "java.lang.String", "kotlin.String" -> {
            if (text) AutoDdlLogicalType.TEXT else AutoDdlLogicalType.STRING
        }
        "java.lang.Character", "kotlin.Char" -> AutoDdlLogicalType.CHAR
        "java.lang.Boolean", "kotlin.Boolean" -> AutoDdlLogicalType.BOOLEAN
        "java.lang.Byte", "kotlin.Byte" -> AutoDdlLogicalType.INT8
        "java.lang.Short", "kotlin.Short" -> AutoDdlLogicalType.INT16
        "java.lang.Integer", "kotlin.Int" -> AutoDdlLogicalType.INT32
        "java.lang.Long", "kotlin.Long" -> AutoDdlLogicalType.INT64
        "java.lang.Float", "kotlin.Float" -> AutoDdlLogicalType.FLOAT32
        "java.lang.Double", "kotlin.Double" -> AutoDdlLogicalType.FLOAT64
        "java.math.BigDecimal" -> AutoDdlLogicalType.DECIMAL
        "java.math.BigInteger" -> AutoDdlLogicalType.BIG_INTEGER
        "java.time.LocalDate", "java.sql.Date" -> AutoDdlLogicalType.DATE
        "java.time.LocalTime", "java.sql.Time" -> AutoDdlLogicalType.TIME
        "java.time.Instant", "java.time.OffsetDateTime", "java.time.ZonedDateTime" -> AutoDdlLogicalType.DATETIME_TZ
        "java.time.LocalDateTime", "java.util.Date", "java.sql.Timestamp" -> AutoDdlLogicalType.DATETIME
        "java.time.Duration", "kotlin.time.Duration" -> AutoDdlLogicalType.DURATION
        "java.util.UUID" -> AutoDdlLogicalType.UUID
        "com.fasterxml.jackson.databind.JsonNode", "tools.jackson.databind.JsonNode" -> AutoDdlLogicalType.JSON
        "kotlin.ByteArray" -> AutoDdlLogicalType.BINARY
        else -> AutoDdlLogicalType.UNKNOWN
    }
}

private fun String.toJimmerSnakeCase(): String {
    return buildString {
        this@toJimmerSnakeCase.forEachIndexed { index, char ->
            val previous = this@toJimmerSnakeCase.getOrNull(index - 1)
            val next = this@toJimmerSnakeCase.getOrNull(index + 1)
            when {
                char == '-' || char == '.' || char == ' ' -> append('_')
                char.isUpperCase() -> {
                    val shouldSplit = index > 0 && lastOrNull() != '_' &&
                        (previous?.isLowerCase() == true || previous?.isDigit() == true || next?.isLowerCase() == true)
                    if (shouldSplit) {
                        append('_')
                    }
                    append(char.lowercaseChar())
                }
                else -> append(char)
            }
        }
    }.replace(Regex("_+"), "_").trim('_')
}

private const val JIMMER_ENTITY_ANNOTATION = "org.babyfish.jimmer.sql.Entity"

private val COLLECTION_TYPE_NAMES = setOf(
    "java.lang.Iterable",
    "java.util.Collection",
    "java.util.List",
    "java.util.Set",
    "kotlin.collections.Collection",
    "kotlin.collections.Iterable",
    "kotlin.collections.List",
    "kotlin.collections.Set",
    "kotlin.collections.MutableCollection",
    "kotlin.collections.MutableIterable",
    "kotlin.collections.MutableList",
    "kotlin.collections.MutableSet",
)
