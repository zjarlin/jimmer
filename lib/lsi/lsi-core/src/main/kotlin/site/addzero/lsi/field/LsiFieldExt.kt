package site.addzero.lsi.field

import site.addzero.lsi.anno.LsiAnnotation
import kotlin.reflect.KClass

fun LsiField.annotations(predicate: (LsiAnnotation) -> Boolean): List<LsiAnnotation> =
  // 覆盖来源：project/compiler/jimmer-ksp-ext/.../utils.KSAnnotated.annotations(KSPropertyDeclaration)
  // 迁移说明：`LsiField.annotations` 过滤语义上收至 lsi-core，统一承接 property/getter/returnType 注解集合
  annotations.filter(predicate)

fun LsiField.annotations(annotationType: KClass<out Annotation>): List<LsiAnnotation> =
  annotations { it.qualifiedName == annotationType.qualifiedName }

fun LsiField.annotation(annotationType: KClass<out Annotation>): LsiAnnotation? =
  annotation(annotationType.qualifiedName!!)

fun LsiField.annotation(qualifiedName: String): LsiAnnotation? =
  // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ClientProcessor field 注解判定
  // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../DocMetadata field Description 判定
  // 覆盖来源：project/compiler/client/jimmer-ksp-client/.../ExportDocProcessor / enum/property 文档扫描
  // 迁移说明：字段单注解查询迁移到 lsi-core，compiler 不再依赖旧 `org.babyfish.jimmer.ksp.annotation`
  annotations.firstOrNull { it.qualifiedName == qualifiedName }


fun LsiField.defaultValue(): String {

  if (this.name == "id") {
    return "null"
  }
  val type = this.type
  val typeDecl = type?.lsiClass
  val fullTypeName = typeDecl?.qualifiedName
  val typeName = typeDecl?.simpleName
  val isNullable = type?.isNullable
  return when {

    this.isEnum -> {
      if (isNullable == true) "null" else "${fullTypeName}.entries.first()"
    }

    isNullable == true -> "null"
    typeName == "String" -> "\"\""
    typeName == "Int" -> "0"
    typeName == "Long" -> "0L"
    typeName == "Double" -> "0.0"
    typeName == "Float" -> "0f"
    typeName == "Boolean" -> "false"
    typeName == "List" -> "emptyList()"
    typeName == "Set" -> "emptySet()"
    typeName == "Map" -> "emptyMap()"
    typeName == "LocalDateTime" -> "kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())"
    typeName == "LocalDate" -> "kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).date"
    typeName == "LocalTime" -> "kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault()).time"
    else -> ""
  }
}

/**
 * 获取字段注解的指定参数值
 * @param annotationSimpleName 注解全限定名
 * @param parameterName 参数名称
 * @return 参数值，如果不存在则返回 null
 */
fun LsiField.getAttribute(annotationSimpleName: String, parameterName: String = "value"): String? {
  val annotation = annotations.find { it.simpleName == annotationSimpleName } ?: return null
  val toString1 = annotation.getAttribute(parameterName)?.toString()
  val toString = toString1
  return toString
}

/**
 * 检查字段是否具有指定的注解
 * @param annotationNames 注解全限定名数组
 * @return 如果字段具有其中任何一个注解，则返回true，否则返回false
 */
fun LsiField.hasAnnotation(vararg annotationNames: String): Boolean {
  return annotationNames.any { annotationName ->
    annotations.any { annotation ->
      annotation.simpleName == annotationName
    }
  }
}

fun LsiField.hasAnnotationIgnoreCase(vararg annotationNames: String): Boolean {
  return annotationNames.any { annotationName ->
    annotations.any { annotation ->
      annotation.simpleName.equals(annotationName, true)
    }
  }
}

/**
 * 检查字段是否具有指定的注解
 * @param annotationNames 注解全限定名数组
 * @return 如果字段具有其中任何一个注解，则返回true，否则返回false
 */
fun LsiField.hasFqAnnotation(vararg annotationNames: String): Boolean {
  return annotationNames.any { annotationName ->
    annotations.any { annotation ->
      annotation.qualifiedName == annotationName
    }
  }
}
