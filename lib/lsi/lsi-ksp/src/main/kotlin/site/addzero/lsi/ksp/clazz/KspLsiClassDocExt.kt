package site.addzero.lsi.ksp.clazz

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.isInternal
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import site.addzero.lsi.clazz.LsiClass

/**
 * 覆盖来源：project/compiler/jimmer-ksp-ext/.../client/DocMetadata.implDocStringMap
 * - getClassDeclarationByName("${qualifiedName}Draft")
 * - "$"/"Impl" 内部类扫描
 * - Impl public 且非 internal 属性的文档注解提取
 */
fun LsiClass.findKspDraftImplDocMap(
    annotationQualifiedName: String,
    valueAttributeName: String = "value",
): Map<String, String> {
    val kspClass = this as? KspLsiClass ?: return emptyMap()
    val qualifiedName = kspClass.ksClassDeclaration.qualifiedName?.asString() ?: return emptyMap()
    val draftDeclaration = kspClass
        .resolver
        .getClassDeclarationByName("${qualifiedName}Draft")
        ?: return emptyMap()
    val producerDeclaration = draftDeclaration
        .declarations
        .filterIsInstance<KSClassDeclaration>()
        .firstOrNull { it.simpleName.asString() == "$" }
        ?: return emptyMap()
    val implDeclaration = producerDeclaration
        .declarations
        .filterIsInstance<KSClassDeclaration>()
        .firstOrNull { it.simpleName.asString() == "Impl" }
        ?: return emptyMap()

    val map = mutableMapOf<String, String>()
    map[""] = implDeclaration
        .findStringAnnotationValue(annotationQualifiedName, valueAttributeName)
        ?: ""
    for (declaration in implDeclaration.declarations) {
        if (declaration is KSPropertyDeclaration && declaration.isPublic() && !declaration.isInternal()) {
            map[declaration.simpleName.asString()] = declaration
                .findStringAnnotationValue(annotationQualifiedName, valueAttributeName)
                ?: ""
        }
    }
    return map
}

private fun KSAnnotated.findStringAnnotationValue(
    annotationQualifiedName: String,
    valueAttributeName: String,
): String? =
    annotations
        .firstOrNull { annotation ->
            annotation.annotationType.resolve().declaration.qualifiedName?.asString() == annotationQualifiedName
        }
        ?.readStringArgument(valueAttributeName)
        ?.takeIf { it.isNotBlank() }

private fun KSAnnotation.readStringArgument(argumentName: String): String? =
    arguments
        .firstOrNull { it.name?.asString() == argumentName }
        ?.value as? String
