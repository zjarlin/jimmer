package site.addzero.lsi.ksp.clazz

import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.validate
import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.assist.checkIsPojo
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.LsiEnumConstant
import site.addzero.lsi.field.LsiField
import site.addzero.lsi.ksp.anno.KspLsiAnnotation
import site.addzero.lsi.ksp.context.KspLsiContext
import site.addzero.lsi.ksp.field.KspLsiField
import site.addzero.lsi.ksp.method.KspLsiMethod
import site.addzero.lsi.ksp.type.KspLsiType
import site.addzero.lsi.method.LsiMethod
import site.addzero.lsi.type.LsiType

class KspLsiClass(
    internal val ksClassDeclaration: KSClassDeclaration,
) : LsiClass {
    // 迁移说明：保留旧构造签名用于兼容现有调用方；resolver 参数已由 KspLsiContext 统一接管
    constructor(@Suppress("UNUSED_PARAMETER") resolver: Resolver, ksClassDeclaration: KSClassDeclaration) : this(
        ksClassDeclaration
    )

    val resolver = KspLsiContext.resolver
    override val simpleName: String? by lazy {
        try {
            ksClassDeclaration.simpleName.asString()
        } catch (e: Exception) {
            null
        }
    }
    override val qualifiedName: String? by lazy {
        try {
            ksClassDeclaration.qualifiedName?.asString()
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override val packageName: String? by lazy {
        ksClassDeclaration.packageName.asString().takeIf { it.isNotEmpty() }
    }

    override val simpleNames: List<String> by lazy {
        generateSequence(ksClassDeclaration as com.google.devtools.ksp.symbol.KSDeclaration?) { current ->
            current.parentDeclaration
        }
            .filterIsInstance<KSClassDeclaration>()
            .map { it.simpleName.asString() }
            .toList()
            .asReversed()
    }

    override val comment: String? by lazy {
        ksClassDeclaration.docString
    }

    override val fields: List<LsiField> by lazy {
        try {
            ksClassDeclaration.getAllProperties()
                .map { KspLsiField(resolver, it) }
                .toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    override val annotations: List<LsiAnnotation> by lazy {
        try {
//          ksClassDeclaration.annotations
            ksClassDeclaration.annotations.filter { it.annotationType.resolve().declaration.validate() }
                .map { KspLsiAnnotation(it) { resolver } }
                .toList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    override val packageAnnotations: List<LsiAnnotation> by lazy {
        try {
            // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ExportDocProcessor.process
            // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../LsiExportDocSupport.exportDocPkg
            // 迁移说明：Kotlin package-level `@file:` 注解统一下沉到 `LsiClass.packageAnnotations`，
            // 让 compiler 业务层不再直连 `KSFile.annotations`
            ksClassDeclaration
                .containingFile
                ?.annotations
                ?.map { KspLsiAnnotation(it) { resolver } }
                ?.toList()
                ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    override val isInterface: Boolean by lazy {
        ksClassDeclaration.classKind == ClassKind.INTERFACE
    }

    override val isClass: Boolean by lazy {
        ksClassDeclaration.classKind == ClassKind.CLASS
    }

    override val isEnum: Boolean by lazy {
        ksClassDeclaration.classKind == ClassKind.ENUM_CLASS
    }

    override val isCollectionType: Boolean by lazy {
        val name = qualifiedName ?: ""
        name.startsWith("kotlin.collections.") || name.startsWith("java.util.") &&
                (name.contains("List") || name.contains("Set") || name.contains("Collection") || name.contains("Map"))
    }

    override val isPojo: Boolean by lazy {
        val isDataClass = isData
        checkIsPojo(
            isInterface = isInterface,
            isEnum = isEnum,
            isAbstract = isAbstract,
            isDataClass = isDataClass,
            annotationNames = annotations.mapNotNull { it.qualifiedName },
            isShortName = false
        )
    }

    override val isTopLevel: Boolean by lazy {
        ksClassDeclaration.parentDeclaration !is KSClassDeclaration
    }

    override val isStatic: Boolean by lazy {
        val parent = ksClassDeclaration.parentDeclaration as? KSClassDeclaration ?: return@lazy false
        when {
            ksClassDeclaration.isCompanionObject -> true
            ksClassDeclaration.classKind == ClassKind.OBJECT -> true
            ksClassDeclaration.modifiers.contains(Modifier.INNER) -> false
            else -> parent != null
        }
    }

    override val isInternal: Boolean by lazy {
        ksClassDeclaration.modifiers.contains(Modifier.INTERNAL)
    }

    override val isProtected: Boolean by lazy {
        ksClassDeclaration.modifiers.contains(Modifier.PROTECTED)
    }

    override val isPrivate: Boolean by lazy {
        ksClassDeclaration.modifiers.contains(Modifier.PRIVATE)
    }

    override val isAbstract: Boolean by lazy {
        ksClassDeclaration.isAbstract()
//    ksClassDeclaration.modifiers.contains(Modifier.ABSTRACT)
    }

    override val isFinal: Boolean by lazy {
        ksClassDeclaration.modifiers.contains(Modifier.FINAL)
    }

    override val isOpen: Boolean by lazy {
        ksClassDeclaration.modifiers.contains(Modifier.OPEN) || isAbstract || isSealed
    }

    override val isData: Boolean by lazy {
        ksClassDeclaration.modifiers.contains(Modifier.DATA)
    }

    override val isSealed: Boolean by lazy {
        ksClassDeclaration.modifiers.contains(Modifier.SEALED)
    }

    override val typeParameterCount: Int by lazy {
        ksClassDeclaration.typeParameters.size
    }

    override val typeParameterNames: List<String> by lazy {
        ksClassDeclaration.typeParameters.map { it.simpleName.asString() }
    }

    override val enumEntryNames: List<String> by lazy {
        if (ksClassDeclaration.classKind != ClassKind.ENUM_CLASS) {
            emptyList()
        } else {
            ksClassDeclaration.declarations
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.classKind == ClassKind.ENUM_ENTRY }
                .map { it.simpleName.asString() }
                .toList()
        }
    }

    override val enumConstants: List<LsiEnumConstant> by lazy {
        if (ksClassDeclaration.classKind != ClassKind.ENUM_CLASS) {
            emptyList()
        } else {
            ksClassDeclaration.declarations
                .filterIsInstance<KSClassDeclaration>()
                .filter { it.classKind == ClassKind.ENUM_ENTRY }
                .map { KspLsiEnumConstant(resolver, it) }
                .toList()
        }
    }

    override val superClasses: List<LsiClass> by lazy {
        ksClassDeclaration.superTypes
            .mapNotNull { superType ->
                val resolvedType = superType.resolve()
                val declaration = resolvedType.declaration
                if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.CLASS) {
                    KspLsiClass(declaration)
                } else null
            }
            .toList()
    }

    override val interfaces: List<LsiClass> by lazy {
        ksClassDeclaration.superTypes
            .mapNotNull { superType ->
                val resolvedType = superType.resolve()
                val declaration = resolvedType.declaration
                if (declaration is KSClassDeclaration && declaration.classKind == ClassKind.INTERFACE) {
                    KspLsiClass(declaration)
                } else null
            }
            .toList()
    }

    override val superTypes: List<LsiType> by lazy {
        ksClassDeclaration.superTypes
            .mapNotNull { superType ->
                runCatching { superType.resolve() }.getOrNull()
            }
            .map { KspLsiType(resolver, it) }
            .toList()
    }

    override val methods: List<LsiMethod> by lazy {
        ksClassDeclaration.getAllFunctions()
            .map { KspLsiMethod(resolver, it) }
            .toList()
    }

    override val constructors: List<LsiMethod> by lazy {
        ksClassDeclaration
            .getConstructors()
            .map { KspLsiMethod(resolver, it) }
            .toList()
    }

    override val primaryConstructor: LsiMethod? by lazy {
        ksClassDeclaration.primaryConstructor?.let {
            KspLsiMethod(resolver, it)
        }
    }

    override val fileName: String? by lazy {
        ksClassDeclaration.containingFile?.fileName?.removeSuffix(".kt")
    }

    override val isObject: Boolean by lazy {
        ksClassDeclaration.classKind == ClassKind.OBJECT
    }

    override val isCompanionObject: Boolean by lazy {
        ksClassDeclaration.isCompanionObject
    }
}
