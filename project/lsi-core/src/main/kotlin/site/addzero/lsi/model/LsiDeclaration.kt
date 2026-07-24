package site.addzero.lsi.model

import site.addzero.lsi.core.LsiLocation
import site.addzero.lsi.core.LsiOrigin
import site.addzero.lsi.core.LsiSymbolId

enum class LsiVisibility {
    PUBLIC,
    PROTECTED,
    INTERNAL,
    PACKAGE_PRIVATE,
    PRIVATE,
    LOCAL,
    UNKNOWN
}

enum class LsiModality {
    FINAL,
    OPEN,
    ABSTRACT,
    SEALED,
    UNKNOWN
}

enum class LsiTypeDeclarationKind {
    CLASS,
    INTERFACE,
    ENUM,
    ANNOTATION,
    OBJECT,
    RECORD,
    TYPE_ALIAS
}

/**
 * 指向被覆盖声明，距离一表示直接覆盖。
 */
data class LsiOverride(
    val declarationId: LsiSymbolId,
    val distance: Int = 1
) {

    init {
        require(distance >= 1) { "LSI override distance must be positive: $distance" }
    }
}

sealed interface LsiDeclaration {
    val id: LsiSymbolId
    val name: String
    val visibility: LsiVisibility
    val documentation: String?
    val sourceDocumentation: String?
    val annotations: List<LsiAnnotation>
    val location: LsiLocation?
    val origin: LsiOrigin
}

data class LsiTypeDeclaration(
    override val id: LsiSymbolId,
    override val name: String,
    val qualifiedName: String,
    val kind: LsiTypeDeclarationKind,
    val enclosingTypeId: LsiSymbolId? = null,
    val requiresEnclosingInstance: Boolean = false,
    val abstractDeclaration: Boolean = false,
    val dataClass: Boolean = false,
    override val visibility: LsiVisibility = LsiVisibility.PUBLIC,
    val modality: LsiModality = LsiModality.FINAL,
    val typeParameters: List<LsiTypeParameter> = emptyList(),
    val superTypes: List<LsiTypeRef> = emptyList(),
    val memberIds: List<LsiSymbolId> = emptyList(),
    val enumEntries: List<LsiEnumEntry> = emptyList(),
    val annotationMembers: List<LsiAnnotationMember> = emptyList(),
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin
) : LsiDeclaration {

    init {
        require(name.isNotBlank()) { "LSI type declaration name cannot be blank" }
        require(qualifiedName.isNotBlank()) { "LSI type declaration qualified name cannot be blank" }
        require(enclosingTypeId != id) { "LSI type declaration cannot enclose itself: ${id.value}" }
        require(!requiresEnclosingInstance || enclosingTypeId != null) {
            "LSI type requiring an enclosing instance must be nested: ${id.value}"
        }
        require(!dataClass || kind == LsiTypeDeclarationKind.CLASS) {
            "Only LSI class declarations can be data classes: ${id.value}"
        }
        require(superTypes.filterIsInstance<LsiDeclaredType>().none { superType -> superType.declarationId == id }) {
            "LSI type declaration cannot directly inherit itself: ${id.value}"
        }
        require(memberIds.distinct().size == memberIds.size) {
            "LSI type declaration cannot contain duplicate member ids: ${id.value}"
        }
        require(enumEntries.map(LsiEnumEntry::id).distinct().size == enumEntries.size) {
            "LSI type declaration cannot contain duplicate enum entry ids: ${id.value}"
        }
        require(annotationMembers.isEmpty() || kind == LsiTypeDeclarationKind.ANNOTATION) {
            "Only LSI annotation declarations can contain annotation members: ${id.value}"
        }
        require(annotationMembers == annotationMembers.sortedBy(LsiAnnotationMember::name)) {
            "LSI annotation members must use stable name order: ${id.value}"
        }
        require(annotationMembers.map(LsiAnnotationMember::name).distinct().size == annotationMembers.size) {
            "LSI annotation declaration cannot contain duplicate member names: ${id.value}"
        }
    }
}

data class LsiAnnotationMember(
    val name: String,
    val type: LsiTypeRef,
    val vararg: Boolean = false,
    val hasDefault: Boolean = false,
    val declarationIndex: Int? = null,
) {
    init {
        require(name.isNotBlank()) { "LSI annotation member name cannot be blank" }
        require(declarationIndex == null || declarationIndex >= 0) {
            "LSI annotation member declaration index cannot be negative: $name"
        }
        require(type == type.toAnnotationMemberType()) {
            "LSI annotation member type must use canonical non-null semantics: $name"
        }
        require(!vararg || type is LsiArrayType) {
            "LSI annotation vararg member must expose its array type: $name"
        }
    }
}

data class LsiEnumEntry(
    override val id: LsiSymbolId,
    override val name: String,
    val ownerId: LsiSymbolId,
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin
) : LsiDeclaration {

    override val visibility: LsiVisibility = LsiVisibility.PUBLIC

    init {
        require(name.isNotBlank()) { "LSI enum entry name cannot be blank" }
    }
}

data class LsiField(
    override val id: LsiSymbolId,
    override val name: String,
    val ownerId: LsiSymbolId,
    val type: LsiTypeRef,
    val mutable: Boolean = false,
    val static: Boolean = false,
    override val visibility: LsiVisibility = LsiVisibility.PUBLIC,
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin
) : LsiDeclaration {

    init {
        require(name.isNotBlank()) { "LSI field name cannot be blank" }
    }
}

data class LsiProperty(
    override val id: LsiSymbolId,
    override val name: String,
    val ownerId: LsiSymbolId,
    val type: LsiTypeRef,
    val getterName: String = name,
    val mutable: Boolean = false,
    val static: Boolean = false,
    val modality: LsiModality = LsiModality.UNKNOWN,
    val overrides: List<LsiOverride> = emptyList(),
    override val visibility: LsiVisibility = LsiVisibility.PUBLIC,
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin
) : LsiDeclaration {

    init {
        require(name.isNotBlank()) { "LSI property name cannot be blank" }
        require(getterName.isNotBlank()) { "LSI property getter name cannot be blank" }
        require(overrides.map(LsiOverride::declarationId).distinct().size == overrides.size) {
            "LSI property cannot override the same declaration more than once: ${id.value}"
        }
    }
}

data class LsiFunction(
    override val id: LsiSymbolId,
    override val name: String,
    val ownerId: LsiSymbolId?,
    val returnType: LsiTypeRef,
    val parameters: List<LsiParameter> = emptyList(),
    val receiverType: LsiTypeRef? = null,
    val suspending: Boolean = false,
    val typeParameters: List<LsiTypeParameter> = emptyList(),
    val thrownTypes: List<LsiTypeRef> = emptyList(),
    val static: Boolean = false,
    val modality: LsiModality = LsiModality.UNKNOWN,
    val overrides: List<LsiOverride> = emptyList(),
    override val visibility: LsiVisibility = LsiVisibility.PUBLIC,
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin
) : LsiDeclaration {

    init {
        require(name.isNotBlank()) { "LSI function name cannot be blank" }
        require(parameters.map(LsiParameter::index) == parameters.indices.toList()) {
            "LSI function parameters must use contiguous zero-based indexes: ${id.value}"
        }
        require(parameters.map(LsiParameter::id).distinct().size == parameters.size) {
            "LSI function cannot contain duplicate parameter ids: ${id.value}"
        }
        require(overrides.map(LsiOverride::declarationId).distinct().size == overrides.size) {
            "LSI function cannot override the same declaration more than once: ${id.value}"
        }
    }
}

data class LsiConstructor(
    override val id: LsiSymbolId,
    val ownerId: LsiSymbolId,
    val primary: Boolean = false,
    val parameters: List<LsiParameter> = emptyList(),
    val typeParameters: List<LsiTypeParameter> = emptyList(),
    val thrownTypes: List<LsiTypeRef> = emptyList(),
    override val visibility: LsiVisibility = LsiVisibility.PUBLIC,
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin
) : LsiDeclaration {

    override val name: String = "constructor"

    init {
        require(parameters.map(LsiParameter::index) == parameters.indices.toList()) {
            "LSI constructor parameters must use contiguous zero-based indexes: ${id.value}"
        }
        require(parameters.map(LsiParameter::id).distinct().size == parameters.size) {
            "LSI constructor cannot contain duplicate parameter ids: ${id.value}"
        }
        require(parameters.all { parameter -> parameter.callableId == id }) {
            "LSI constructor parameters must reference their constructor: ${id.value}"
        }
    }
}

data class LsiParameter(
    override val id: LsiSymbolId,
    override val name: String,
    val callableId: LsiSymbolId,
    val index: Int,
    val type: LsiTypeRef,
    val vararg: Boolean = false,
    val hasDefault: Boolean = false,
    override val documentation: String? = null,
    override val sourceDocumentation: String? = null,
    override val annotations: List<LsiAnnotation> = emptyList(),
    override val location: LsiLocation? = null,
    override val origin: LsiOrigin
) : LsiDeclaration {

    override val visibility: LsiVisibility = LsiVisibility.LOCAL

    init {
        require(name.isNotBlank()) { "LSI parameter name cannot be blank" }
        require(index >= 0) { "LSI parameter index cannot be negative: $index" }
    }
}
