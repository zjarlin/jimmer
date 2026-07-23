package site.addzero.lsi.jimmer.error

import site.addzero.lsi.core.LsiSymbolId
import site.addzero.lsi.model.LsiAnnotation
import site.addzero.lsi.model.LsiAnnotationValue
import site.addzero.lsi.model.LsiEnumEntry
import site.addzero.lsi.model.LsiPrimitiveType
import site.addzero.lsi.model.LsiTypeDeclaration
import site.addzero.lsi.model.LsiTypeDeclarationKind
import site.addzero.lsi.model.LsiTypeRef
import site.addzero.lsi.model.LsiWorkspace

/** 控制 Error schema 的生成语义。 */
data class ErrorSchemaOptions(
    val checkedException: Boolean = false,
)

/** 表示 Error 领域声明不满足生成约束。 */
class ErrorValidationException(
    val declarationId: LsiSymbolId,
    message: String,
) : IllegalArgumentException(message)

/** 将冻结的 LSI 工作区解析为 Error 领域语义。 */
fun LsiWorkspace.toErrorSchema(
    options: ErrorSchemaOptions = ErrorSchemaOptions(),
    targetTypeIds: Set<LsiSymbolId>? = null,
): ErrorSchema {
    val types = declarationsOfType<LsiTypeDeclaration>()
        .sortedBy(LsiTypeDeclaration::qualifiedName)
    val families = types
        .filter { type -> targetTypeIds == null || type.id in targetTypeIds }
        .filter { type -> type.annotations.hasAnnotation(ERROR_FAMILY_ANNOTATION) }
        .map { type -> compileFamily(type, types, options) }
    return ErrorSchema(families)
}

private fun compileFamily(
    type: LsiTypeDeclaration,
    allTypes: List<LsiTypeDeclaration>,
    options: ErrorSchemaOptions,
): ErrorFamily {
    if (type.kind != LsiTypeDeclarationKind.ENUM) {
        throw ErrorValidationException(
            declarationId = type.id,
            message = "Only enum can be decorated by '@${ERROR_FAMILY_ANNOTATION.value}'",
        )
    }
    val longSimpleName = type.longSimpleName(allTypes)
    val exceptionStem = longSimpleName.errorExceptionStem()
    val familyAnnotation = requireNotNull(type.annotations.annotation(ERROR_FAMILY_ANNOTATION))
    val family = familyAnnotation.stringValue("value")
        ?.takeIf(String::isNotBlank)
        ?: exceptionStem.toUpperSnake()
    val declaredFields = type.annotations.compileFields(type.id)
    val packageName = type.packageName(allTypes)
    val exceptionSimpleName = exceptionStem + "Exception"
    val exceptionTypeId = LsiSymbolId.type(
        if (packageName.isEmpty()) exceptionSimpleName else "$packageName.$exceptionSimpleName"
    )
    if (type.enumEntries.isEmpty()) {
        throw ErrorValidationException(
            declarationId = type.id,
            message = "Error family '${type.qualifiedName}' must declare at least one error code",
        )
    }
    val codes = type.enumEntries.map { entry ->
        compileCode(entry, declaredFields, exceptionTypeId)
    }
    return ErrorFamily(
        id = type.id,
        qualifiedName = type.qualifiedName,
        packageName = packageName,
        family = family,
        exceptionTypeId = exceptionTypeId,
        exceptionSimpleName = exceptionSimpleName,
        checkedException = options.checkedException,
        documentation = type.documentation.normalizedDocumentation(),
        originatingSources = type.origin.source?.let(::setOf).orEmpty(),
        declaredFields = declaredFields,
        codes = codes,
    )
}

private fun compileCode(
    entry: LsiEnumEntry,
    sharedFields: List<ErrorField>,
    familyExceptionTypeId: LsiSymbolId,
): ErrorCode {
    val declaredFields = entry.annotations.compileFields(entry.id)
    val sharedNames = sharedFields.mapTo(hashSetOf(), ErrorField::name)
    val duplicate = declaredFields.firstOrNull { field -> field.name in sharedNames }
    if (duplicate != null) {
        throw ErrorValidationException(
            declarationId = entry.id,
            message = "Error field '${duplicate.name}' has already been declared by the error family",
        )
    }
    val exceptionSimpleName = entry.name.toCamelName(upperHead = true)
    return ErrorCode(
        id = entry.id,
        enumEntryName = entry.name,
        code = entry.name.toUpperSnake(),
        creatorName = entry.name.toCamelName(upperHead = false),
        exceptionTypeId = LsiSymbolId.type(
            "${familyExceptionTypeId.requireTypeQualifiedName()}.$exceptionSimpleName"
        ),
        exceptionSimpleName = exceptionSimpleName,
        documentation = entry.documentation.normalizedDocumentation(),
        declaredFields = declaredFields,
    )
}

private fun List<LsiAnnotation>.compileFields(
    declarationId: LsiSymbolId,
): List<ErrorField> {
    val annotations = flatMap { annotation ->
        when (annotation.type) {
            ERROR_FIELD_ANNOTATION -> listOf(annotation)
            ERROR_FIELDS_ANNOTATION -> annotation.nestedAnnotations("value")
            else -> emptyList()
        }
    }
    val names = hashSetOf<String>()
    return annotations.map { annotation ->
        val name = annotation.stringValue("name")
            ?.takeIf(String::isNotBlank)
            ?: throw ErrorValidationException(
                declarationId = declarationId,
                message = "Error field name cannot be blank",
            )
        if (name == "family" || name == "code") {
            throw ErrorValidationException(
                declarationId = declarationId,
                message = "Error field '$name' conflicts with built-in exception metadata",
            )
        }
        if (!names.add(name)) {
            throw ErrorValidationException(
                declarationId = declarationId,
                message = "Duplicate error field '$name'",
            )
        }
        val type = annotation.classValue("type")
            ?: throw ErrorValidationException(
                declarationId = declarationId,
                message = "Error field '$name' must declare a type",
            )
        val list = annotation.booleanValue("list")
        if (list && type is LsiPrimitiveType) {
            throw ErrorValidationException(
                declarationId = declarationId,
                message = "Error field '$name' cannot be a list of primitive values",
            )
        }
        ErrorField(
            name = name,
            type = type,
            list = list,
            nullable = annotation.booleanValue("nullable"),
            documentation = annotation.stringValue("doc").normalizedDocumentation(),
            declaredBy = declarationId,
        )
    }
}

private fun LsiTypeDeclaration.longSimpleName(
    allTypes: List<LsiTypeDeclaration>,
): String {
    val enclosingType = enclosingType(allTypes) ?: return name
    return enclosingType.longSimpleName(allTypes) + "_" + name
}

private fun LsiTypeDeclaration.packageName(
    allTypes: List<LsiTypeDeclaration>,
): String {
    val enclosingType = enclosingType(allTypes)
    if (enclosingType != null) {
        return enclosingType.packageName(allTypes)
    }
    return qualifiedName.removeSuffix(".$name").takeUnless { value -> value == qualifiedName }.orEmpty()
}

private fun LsiTypeDeclaration.enclosingType(
    allTypes: List<LsiTypeDeclaration>,
): LsiTypeDeclaration? {
    return allTypes
        .asSequence()
        .filter { candidate -> candidate.id != id }
        .filter { candidate -> qualifiedName.startsWith(candidate.qualifiedName + ".") }
        .maxByOrNull { candidate -> candidate.qualifiedName.length }
}

private fun String.errorExceptionStem(): String {
    return when {
        endsWith("_ErrorCode") -> dropLast(10)
        endsWith("ErrorCode") -> dropLast(9)
        endsWith("_Error") -> dropLast(6)
        endsWith("Error") -> dropLast(5)
        else -> this
    }
}

private fun String.toCamelName(upperHead: Boolean): String {
    val result = StringBuilder(length)
    var uppercaseNext = upperHead
    for (character in this) {
        if (character == '_') {
            uppercaseNext = true
        } else {
            result.append(if (uppercaseNext) character.uppercaseChar() else character.lowercaseChar())
            uppercaseNext = false
        }
    }
    return result.toString()
}

private fun String.toUpperSnake(): String {
    val result = StringBuilder(length + 8)
    var previousLowerCaseOrDigit = false
    for (character in this) {
        if (character == '_') {
            if (result.isNotEmpty() && result.last() != '_') {
                result.append('_')
            }
            previousLowerCaseOrDigit = false
            continue
        }
        val lowerCaseOrDigit = character.isLowerCase() || character.isDigit()
        if (previousLowerCaseOrDigit && !lowerCaseOrDigit) {
            result.append('_')
        }
        previousLowerCaseOrDigit = lowerCaseOrDigit
        result.append(character.uppercaseChar())
    }
    return result.toString()
}

private fun String?.normalizedDocumentation(): String? {
    return this
        ?.replace("\r\n", "\n")
        ?.replace('\r', '\n')
        ?.trim()
        ?.takeIf(String::isNotEmpty)
}

private fun List<LsiAnnotation>.annotation(type: LsiSymbolId): LsiAnnotation? {
    return firstOrNull { annotation -> annotation.type == type }
}

private fun List<LsiAnnotation>.hasAnnotation(type: LsiSymbolId): Boolean {
    return any { annotation -> annotation.type == type }
}

private fun LsiAnnotation.stringValue(name: String): String? {
    return (arguments[name]?.value as? LsiAnnotationValue.StringValue)?.value
}

private fun LsiAnnotation.booleanValue(name: String): Boolean {
    return (arguments[name]?.value as? LsiAnnotationValue.BooleanValue)?.value ?: false
}

private fun LsiAnnotation.classValue(name: String): LsiTypeRef? {
    return (arguments[name]?.value as? LsiAnnotationValue.ClassValue)?.type
}

private fun LsiAnnotation.nestedAnnotations(name: String): List<LsiAnnotation> {
    val value = arguments[name]?.value ?: return emptyList()
    return when (value) {
        is LsiAnnotationValue.NestedAnnotationValue -> listOf(value.annotation)
        is LsiAnnotationValue.ArrayValue -> value.elements.mapNotNull { element ->
            (element as? LsiAnnotationValue.NestedAnnotationValue)?.annotation
        }
        else -> emptyList()
    }
}

private val ERROR_FAMILY_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.error.ErrorFamily")
private val ERROR_FIELD_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.error.ErrorField")
private val ERROR_FIELDS_ANNOTATION = LsiSymbolId.type("org.babyfish.jimmer.error.ErrorFields")
