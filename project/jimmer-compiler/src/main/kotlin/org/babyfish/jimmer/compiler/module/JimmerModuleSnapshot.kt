package org.babyfish.jimmer.compiler.module

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

fun JimmerModuleSchema.normalizedSnapshot(): String {
    return buildString {
        appendRecord(
            "module-schema",
            platform.name,
            packageName,
            options.immutablesName,
            options.tablesName,
            options.tableExesName,
            options.fetchersName,
            options.moduleRequired.toString(),
            options.resourceGeneration.toString(),
        )
        summaries.sortedBy(JimmerModuleSummary::kind).forEach { summary ->
            appendRecord(
                "summary",
                summary.kind.name,
                summary.packageName,
                summary.simpleName,
            )
            summary.members.forEach { member ->
                appendRecord(
                    "summary-member",
                    summary.kind.name,
                    member.typeId.value,
                    member.qualifiedTypeName,
                    member.packageName,
                    member.simpleTypeName,
                    member.generatedName,
                )
            }
            appendDependencies("summary-dependencies", summary.kind.name, summary.dependencies)
        }
        module?.let { source ->
            appendRecord(
                "module-source",
                source.packageName,
                source.simpleName,
                source.entityTypeIds.joinToString(",") { typeId -> typeId.value },
                source.entityNamePrefix.orEmpty(),
            )
            appendDependencies("module-dependencies", source.simpleName, source.dependencies)
        }
        resources.sortedBy(JimmerModuleResource::path).forEach { resource ->
            appendRecord(
                "resource",
                resource.kind.name,
                resource.path,
                resource.qualifiedTypeNames.joinToString(","),
                resource.contentTypeIds.joinToString(",") { typeId -> typeId.value },
                resource.mergeExistingContent.toString(),
            )
            appendDependencies("resource-dependencies", resource.path, resource.dependencies)
        }
    }
}

fun JimmerModuleSchema.fingerprint(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(normalizedSnapshot().toByteArray(StandardCharsets.UTF_8))
    return bytes.joinToString("") { byte -> "%02x".format(byte) }
}

private fun StringBuilder.appendDependencies(
    kind: String,
    owner: String,
    dependencies: JimmerModuleArtifactDependencies,
) {
    appendRecord(
        kind,
        owner,
        dependencies.typeIds.joinToString(",") { typeId -> typeId.value },
        dependencies.originatingTypeIds.joinToString(",") { typeId -> typeId.value },
        dependencies.packageNames.joinToString(","),
        dependencies.scope.name,
        dependencies.aggregationMode.name,
    )
}

private fun StringBuilder.appendRecord(
    kind: String,
    vararg fields: String,
) {
    append(kind)
    fields.forEach { field ->
        append('|')
        append(field.escapeSnapshotField())
    }
    append('\n')
}

private fun String.escapeSnapshotField(): String {
    return buildString {
        for (character in this@escapeSnapshotField) {
            when (character) {
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '|' -> append("\\|")
                ',' -> append("\\,")
                else -> append(character)
            }
        }
    }
}
