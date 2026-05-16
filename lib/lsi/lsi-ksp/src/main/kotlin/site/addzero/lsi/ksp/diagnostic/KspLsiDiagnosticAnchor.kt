package site.addzero.lsi.ksp.diagnostic

import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSValueParameter
import site.addzero.lsi.diagnostic.LsiDiagnosticAnchor

class KspLsiDiagnosticAnchor(
    private val node: KSNode
) : LsiDiagnosticAnchor {

    // 覆盖来源：KSP MetaException 的 KSNode 锚点分类
    override val kind: LsiDiagnosticAnchor.Kind
        get() = when (node) {
            is KSClassDeclaration -> LsiDiagnosticAnchor.Kind.CLASS
            is KSPropertyDeclaration -> LsiDiagnosticAnchor.Kind.FIELD
            is KSFunctionDeclaration -> LsiDiagnosticAnchor.Kind.METHOD
            is KSValueParameter -> LsiDiagnosticAnchor.Kind.PARAMETER
            else -> LsiDiagnosticAnchor.Kind.UNKNOWN
        }

    // 覆盖来源：KSP 各校验错误输出时需要定位所属类型
    override val ownerQualifiedName: String?
        get() = when (node) {
            is KSClassDeclaration -> node.qualifiedName?.asString()
            is KSDeclaration -> (node.parentDeclaration as? KSClassDeclaration)?.qualifiedName?.asString()
            is KSValueParameter -> {
                val parent = node.parent as? KSFunctionDeclaration
                (parent?.parentDeclaration as? KSClassDeclaration)?.qualifiedName?.asString()
            }
            else -> null
        }

    // 覆盖来源：KSP 各校验错误输出时需要定位当前符号名
    override val symbolName: String?
        get() = when (node) {
            is KSDeclaration -> node.simpleName.asString()
            is KSValueParameter -> node.name?.asString()
            else -> null
        }
}

fun KSNode.toLsiDiagnosticAnchor(): LsiDiagnosticAnchor =
    KspLsiDiagnosticAnchor(this)
