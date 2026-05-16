package site.addzero.lsi.resolver

import site.addzero.lsi.clazz.LsiClass

/**
 * LSI 解析器统一入口。
 *
 * 统一 APT/KSP 的“全量扫描、新增扫描、按注解扫描、按名称查询”能力。
 */
interface LsiResolver {

    /**
     * 当前轮次可见的所有类。
     *
     * 替换覆盖点（Jimmer 源码）：
     * - APT: `project/jimmer-apt/.../immutable/ImmutableProcessor.parseImmutableTypes` 的 `roundEnv.getRootElements()`
     * - APT: `project/jimmer-apt/.../client/ClientProcessor.process` 的 `roundEnv.getRootElements()`
     * - APT: `project/jimmer-apt/.../client/ExportDocProcessor.process` 的 `roundEnv.getRootElements()`
     * - KSP: `project/compiler/client/jimmer-ksp-client/.../ClientProcessor.process` 的 `resolver.getAllFiles()`
     * - KSP: `project/compiler/client/jimmer-ksp-client/.../ExportDocProcessor.process` 的 `resolver.getAllFiles()`
     * - KSP: `project/compiler/tuple/jimmer-ksp-tuple/.../TypedTupleProcessor.process` 的 `resolver.getAllFiles()`
     */
    fun allClasses(): Sequence<LsiClass>

    /**
     * 当前轮次新增的类。
     *
     * 对于无法严格区分“新增/全量”的平台实现，可退化为 [allClasses]。
     *
     * 替换覆盖点（Jimmer 源码）：
     * - KSP: `project/compiler/immutable/jimmer-ksp-immutable/.../ImmutableProcessor.findModelMap` 的 `resolver.getNewFiles()`
     * - KSP: `project/compiler/error/jimmer-ksp-error/.../ErrorProcessor.findErrorTypes` 的 `resolver.getNewFiles()`
     * - KSP: `project/compiler/transactional/jimmer-ksp-transactional/.../TxProcessor.process` 的 `resolver.getNewFiles()`
     */
    fun newClasses(): Sequence<LsiClass>

    /**
     * 查询带指定注解的类。
     *
     * @param annotationQualifiedName 注解全限定名
     *
     * 替换覆盖点（Jimmer 源码）：
     * - APT: `project/jimmer-apt/.../transactional/TxProcessor.process` 的 `roundEnv.getElementsAnnotatedWith(...)`
     * - APT: `project/jimmer-apt/.../tuple/TypedTupleProcessor.process` 的 `roundEnv.getElementsAnnotatedWith(TypedTuple.class)`
     * - APT: `project/jimmer-apt/.../immutable/ImmutableProcessor.validateTopLevel` 的注解扫描入口
     * - KSP: 后续替换 `resolver.getSymbolsWithAnnotation(...)` 的统一入口
     */
    fun findClassesAnnotatedWith(annotationQualifiedName: String): Sequence<LsiClass>

    /**
     * 按全限定名查找类。
     *
     * 替换覆盖点（Jimmer 源码）：
     * - APT: `project/jimmer-apt/.../dto/DtoProcessor.parseDtoTypes` 的 `elements.getTypeElement(...)`
     * - APT: `project/jimmer-apt/.../client/ClientProcessor.process` 的 delayed type lookup
     * - KSP: `project/compiler/dto/jimmer-ksp-dto/.../DtoProcessor.findDtoTypeMap` 的 `resolver.getClassDeclarationByName(...)`
     * - KSP: `project/compiler/tuple/jimmer-ksp-tuple/.../TypedTupleProcessor.process` 的 delayed type lookup
     */
    fun findClassByQualifiedName(qualifiedName: String): LsiClass?

    /**
     * 带过滤条件的全量扫描。
     */
    fun allClasses(filter: (LsiClass) -> Boolean): Sequence<LsiClass> =
        allClasses().filter(filter)

    /**
     * 带过滤条件的新增扫描。
     */
    fun newClasses(filter: (LsiClass) -> Boolean): Sequence<LsiClass> =
        newClasses().filter(filter)

    /**
     * 获取类型泛型参数个数（不存在返回 null）。
     *
     * 替换覆盖点（Jimmer 源码）：
     * - APT: `project/jimmer-apt/.../dto/AptDtoCompiler.getGenericTypeCount` 的 `elements.getTypeElement(...).getTypeParameters().size`
     * - KSP: `project/compiler/jimmer-ksp-ext/.../site.addzero.lsi.codegen.LsiDtoCompiler.getGenericTypeCount` 的 `resolver.findClassByQualifiedName(...).typeParameterCount`
     */
    fun genericTypeCount(qualifiedName: String): Int? =
        findClassByQualifiedName(qualifiedName)?.typeParameterCount
}
