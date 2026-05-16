package site.addzero.lsi.field

import site.addzero.lsi.anno.LsiAnnotation
import site.addzero.lsi.assist.isNullable
import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.type.LsiType

/**
 * 语言无关的字段结构抽象接口
 * Lsi = Language Structure Interface
 */
interface LsiField {
  /**
   * 获取字段名称
   */
  val name: String?

  /**
   * 获取字段类型
   */
  val type: LsiType?

  /**
   * 获取字段类型名称(不含泛型,如需泛型可以调用对应的扩展函数)
   */
  val typeName: String?

  /**
   * 获取字段注释
   */
  val comment: String?

  /**
   * 获取字段相关的全部注解。
   *
   * 语义约定：
   * - KSP 等支持属性多落点的平台，应统一包含 property/getter/returnType 等位置的注解
   * - APT/反射等无法提供更多落点的平台，至少返回字段本体注解
   */
  val annotations: List<LsiAnnotation>

  /**
   * 判断是否为静态字段
   */
  val isStatic: Boolean

  /**
   * 判断是否为 public 字段/属性。
   */
  val isPublic: Boolean
    get() = true

  /**
   * 判断是否为 private 字段/属性。
   */
  val isPrivate: Boolean
    get() = false

  /**
   * 判断是否为常量字段
   */
  val isConstant: Boolean

  /**
   * 判断是否为枚举
   */
  val isEnum: Boolean

  /**
   * 判断是否为可变字段（Kotlin 的 var）
   * 对于 Java 字段，如果没有 final 修饰符则认为是可变的
   */
  val isVar: Boolean

  /**
   * 判断是否为抽象属性（例如接口抽象 getter）。
   */
  val isAbstract: Boolean
    get() = false

  /**
   * 判断是否为延迟初始化字段（Kotlin 的 lateinit）
   * 对于 Java 字段，始终返回 false
   */
  val isLateInit: Boolean

  /**
   * 判断是否为集合类型
   */
  val isCollectionType: Boolean

  /**
   * 判断字段类型是否为类型别名（typealias）。
   */
  val isTypeAlias: Boolean
    get() = false

  /**
   * 判断字段类型是否为 Kotlin value class。
   */
  val isValueClassType: Boolean
    get() = false

  /**
   * 获取字段的默认值（如果有的话）
   */
  val defaultValue: String?

  /**
   * 获取数据库列名
   * 优先从注解中获取，如果没有则返回 null
   * 支持的注解：
   * - Jimmer: @Column(name = "xxx")
   * - MyBatis Plus: @TableField(value = "xxx")
   */
  val columnName: String?

  /**
   * 获取声明该字段的类
   */
  val declaringClass: LsiClass?

  /**
   * 获取字段类型对应的LsiClass（如果是对象类型）
   */
  val fieldTypeClass: LsiClass?

  /**
   * 判断是否为嵌套对象
   */
  val isNestedObject: Boolean

  /**
   * 获取嵌套字段信息（如果该字段是对象类型）
   */
  val children: List<LsiField>

  /**
   * 判断字段是否可空
   * 对于 Java 字段：基于 JSpecify @Nullable/@NonNull 注解
   * 对于 Kotlin 字段：基于 Kotlin 语言的可空类型标记
   */
  val isNullable: Boolean
    get() {
      val isPrimitive = type?.isPrimitive
      if (isPrimitive == true) return false
      //这里ksp和k1,k2有isMarkedNullable方法,其余都看jspecify 注解短名称
      return annotations.isNullable()
    }

}
