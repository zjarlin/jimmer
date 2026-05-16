package site.addzero.lsi.diagnostic

/**
 * 统一诊断锚点抽象，用于跨平台定位错误来源。
 *
 * 替换覆盖点（Jimmer 源码）：
 * - APT: `project/jimmer-apt/.../MetaException` 及其在各 Processor/Meta 校验处的 `Element` 锚点
 * - KSP: `project/compiler/jimmer-ksp-ext/.../MetaException` 及其在各 Processor/Meta 校验处的 `KSNode` 锚点
 */
interface LsiDiagnosticAnchor {

    /**
     * 锚点类型。
     */
    val kind: Kind

    /**
     * 所属类型全限定名（若可解析）。
     */
    val ownerQualifiedName: String?

    /**
     * 当前符号名称（字段名/方法名/类型名等）。
     */
    val symbolName: String?

    enum class Kind {
        CLASS,
        FIELD,
        METHOD,
        PARAMETER,
        UNKNOWN,
    }
}
