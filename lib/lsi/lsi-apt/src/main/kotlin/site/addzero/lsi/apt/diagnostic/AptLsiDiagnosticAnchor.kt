package site.addzero.lsi.apt.diagnostic

import site.addzero.lsi.diagnostic.LsiDiagnosticAnchor
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.TypeElement

class AptLsiDiagnosticAnchor(
    private val element: Element
) : LsiDiagnosticAnchor {

    // 覆盖来源：APT MetaException 的 Element 锚点分类
    override val kind: LsiDiagnosticAnchor.Kind
        get() = when (element.kind) {
            ElementKind.CLASS, ElementKind.INTERFACE, ElementKind.ENUM -> LsiDiagnosticAnchor.Kind.CLASS
            ElementKind.FIELD -> LsiDiagnosticAnchor.Kind.FIELD
            ElementKind.METHOD -> LsiDiagnosticAnchor.Kind.METHOD
            ElementKind.PARAMETER -> LsiDiagnosticAnchor.Kind.PARAMETER
            else -> LsiDiagnosticAnchor.Kind.UNKNOWN
        }

    // 覆盖来源：APT 各校验错误输出时需要定位所属类型
    override val ownerQualifiedName: String?
        get() = when (element) {
            is TypeElement -> element.qualifiedName.toString()
            else -> (element.enclosingElement as? TypeElement)?.qualifiedName?.toString()
        }

    // 覆盖来源：APT 各校验错误输出时需要定位当前符号名
    override val symbolName: String?
        get() = element.simpleName?.toString()
}

fun Element.toLsiDiagnosticAnchor(): LsiDiagnosticAnchor =
    AptLsiDiagnosticAnchor(this)
