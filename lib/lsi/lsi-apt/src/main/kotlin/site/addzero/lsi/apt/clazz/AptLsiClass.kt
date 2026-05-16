package site.addzero.lsi.apt.clazz

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.assist.checkIsPojo
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.LsiEnumConstant
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.apt.anno.classComment
import site.addzero.lsi.apt.anno.toLsiAnnotations
import site.addzero.lsi.apt.context.AptLsiContext
import site.addzero.lsi.apt.element.getDocComment
import site.addzero.lsi.apt.element.isField
import site.addzero.lsi.apt.element.isRecordComponent
import site.addzero.lsi.apt.field.AptLsiField
import site.addzero.lsi.apt.field.toLsiFieldOrNull
import site.addzero.lsi.apt.method.AptLsiMethod
import site.addzero.lsi.apt.type.AptLsiType
import site.addzero.lsi.type.LsiType
import site.addzero.util.str.firstNotBlank
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.Elements

class AptLsiClass(
    private val typeElement: TypeElement
) : LsiClass {
    constructor(@Suppress("UNUSED_PARAMETER") elements: Elements, typeElement: TypeElement) : this(typeElement)

    val elements = AptLsiContext.elements

    override val simpleName: String? by lazy {
        typeElement.simpleName.toString()
    }

    override val qualifiedName: String? by lazy {
        typeElement.qualifiedName.toString()
    }

    override val packageName: String? by lazy {
        (typeElement.enclosingElement as? PackageElement)
            ?.qualifiedName
            ?.toString()
            ?.takeIf { it.isNotEmpty() }
    }

    override val simpleNames: List<String> by lazy {
        generateSequence(typeElement as javax.lang.model.element.Element?) { current ->
            current.enclosingElement
                ?.takeIf { it !is PackageElement }
        }
            .filterIsInstance<TypeElement>()
            .map { it.simpleName.toString() }
            .toList()
            .asReversed()
    }

    override val comment: String? by lazy {
        val docComment = typeElement.getDocComment(elements)

        firstNotBlank(
            docComment,
            typeElement.annotationMirrors.classComment()
        )
    }

    override val fields: List<LsiField> by lazy {
        // 覆盖来源：project/jimmer-apt/.../client/ClientProcessor.fillDefinition 的 RECORD_COMPONENT -> accessor 兼容逻辑
        // 迁移说明：APT `LsiClass.fields` 现在直接承接 Java record 的逻辑属性；若存在同名 backing field，
        // 优先暴露 record component，避免共享 helper 看到重复属性
        val fields = linkedMapOf<String, LsiField>()
        for (element in typeElement.enclosedElements) {
            if (!element.isField() && !element.isRecordComponent()) {
                continue
            }
            val lsiField = element.toLsiFieldOrNull(elements) ?: continue
            val name = lsiField.name ?: continue
            if (element.isRecordComponent() || name !in fields) {
                fields[name] = lsiField
            }
        }
        fields.values.toList()
    }

    override val annotations: List<LsiAnnotation> by lazy {
        typeElement.annotationMirrors.toLsiAnnotations()
    }

    override val packageAnnotations: List<LsiAnnotation> by lazy {
        // 覆盖来源：project/jimmer-apt/.../client/ExportDocProcessor.pkg 的 `PackageElement.getAnnotation(ExportDoc.class)`
        // 迁移说明：APT package-info 注解读取统一下沉到 `LsiClass.packageAnnotations`，
        // 让共享 compiler/helper 不再直接依赖 `PackageElement`
        (typeElement.enclosingElement as? PackageElement)
            ?.annotationMirrors
            ?.toLsiAnnotations()
            ?: emptyList()
    }

    override val isInterface: Boolean by lazy {
        typeElement.kind == ElementKind.INTERFACE
    }

    override val isClass: Boolean by lazy {
        typeElement.kind == ElementKind.CLASS
    }

    override val isEnum: Boolean by lazy {
        typeElement.kind == ElementKind.ENUM
    }

    override val isCollectionType: Boolean by lazy {
        val name = qualifiedName ?: ""
        name.startsWith("java.util.") &&
            (name.contains("List") || name.contains("Set") || name.contains("Collection"))
    }

    override val isPojo: Boolean by lazy {
        checkIsPojo(
            isInterface = isInterface,
            isEnum = isEnum,
            isAbstract = isAbstract,
            isDataClass = false,
            annotationNames = annotations.mapNotNull { it.qualifiedName },
            isShortName = false
        )
    }

    override val isTopLevel: Boolean by lazy {
        typeElement.enclosingElement is PackageElement
    }

    override val isStatic: Boolean by lazy {
        !isTopLevel && typeElement.modifiers.contains(Modifier.STATIC)
    }

    override val isInternal: Boolean
        get() = false

    override val isProtected: Boolean by lazy {
        typeElement.modifiers.contains(Modifier.PROTECTED)
    }

    override val isPrivate: Boolean by lazy {
        typeElement.modifiers.contains(Modifier.PRIVATE)
    }

    override val isAbstract: Boolean by lazy {
        typeElement.modifiers.contains(Modifier.ABSTRACT)
    }

    override val isFinal: Boolean by lazy {
        typeElement.modifiers.contains(Modifier.FINAL)
    }

    override val isOpen: Boolean by lazy {
        isClass && !isFinal
    }

    override val typeParameterCount: Int by lazy {
        typeElement.typeParameters.size
    }

    override val typeParameterNames: List<String> by lazy {
        typeElement.typeParameters.map { it.simpleName.toString() }
    }

    override val enumEntryNames: List<String> by lazy {
        if (typeElement.kind != ElementKind.ENUM) {
            emptyList()
        } else {
            typeElement.enclosedElements
                .filter { it.kind == ElementKind.ENUM_CONSTANT }
                .map { it.simpleName.toString() }
        }
    }

    override val enumConstants: List<LsiEnumConstant> by lazy {
        if (typeElement.kind != ElementKind.ENUM) {
            emptyList()
        } else {
            typeElement.enclosedElements
                .filterIsInstance<VariableElement>()
                .filter { it.kind == ElementKind.ENUM_CONSTANT }
                .map { AptLsiEnumConstant(elements, it) }
        }
    }

    override val superClasses: List<LsiClass> by lazy {
        val superclass = typeElement.superclass
        if (superclass is DeclaredType) {
            val element = superclass.asElement() as? TypeElement
            element?.let { listOf(AptLsiClass(it)) } ?: emptyList()
        } else {
            emptyList()
        }
    }

    override val interfaces: List<LsiClass> by lazy {
        typeElement.interfaces.mapNotNull { interfaceType ->
            (interfaceType as? DeclaredType)?.asElement()?.let {
                _root_ide_package_.site.addzero.lsi.apt.clazz.AptLsiClass( it as TypeElement)
            }
        }
    }

    override val methods: List<LsiMethod> by lazy {
        typeElement.enclosedElements
            .filterIsInstance<ExecutableElement>()
            .filter { it.kind == ElementKind.METHOD }
            .map { _root_ide_package_.site.addzero.lsi.apt.method.AptLsiMethod(elements, it) }
    }

    override val constructors: List<LsiMethod> by lazy {
        typeElement.enclosedElements
            .filterIsInstance<ExecutableElement>()
            .filter { it.kind == ElementKind.CONSTRUCTOR }
            .map { _root_ide_package_.site.addzero.lsi.apt.method.AptLsiMethod(elements, it) }
    }

    override val superTypes: List<LsiType> by lazy {
        val mirrors = mutableListOf<TypeMirror>()
        typeElement.superclass?.let { mirrors += it }
        mirrors += typeElement.interfaces
        mirrors
            .filterIsInstance<DeclaredType>()
            .map { AptLsiType(elements, it) }
    }

    override val fileName: String? by lazy {
        simpleNames.firstOrNull()
    }

    override val isObject: Boolean
        get() = false

    override val isCompanionObject: Boolean
        get() = false
}
