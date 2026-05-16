package site.addzero.lsi.codegen

/**
 * 覆盖来源：project/compiler/jimmer-ksp-ext/.../org.babyfish.jimmer.ksp.GeneratorException
 * 迁移说明：生成期基础异常下沉到 `lsi-core`，避免 shared metadata/generator 继续经由 KSP 扩展模块间接获得该能力
 */
class GeneratorException : RuntimeException {
    constructor(message: String) : super(message)
    constructor(message: String, cause: Throwable) : super(message, cause)
}
