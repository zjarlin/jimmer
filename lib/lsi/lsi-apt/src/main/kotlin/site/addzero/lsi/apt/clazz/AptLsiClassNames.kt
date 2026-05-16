package site.addzero.lsi.apt.clazz

import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.poet.LsiClassName
import java.util.function.UnaryOperator
import javax.lang.model.element.Element
import javax.lang.model.element.PackageElement
import javax.lang.model.element.TypeElement

object AptLsiClassNames {

    @JvmStatic
    fun of(lsiClass: LsiClass, simpleNameConverter: UnaryOperator<String>? = null): LsiClassName {
        val packageName = lsiClass.packageName.orEmpty()
        val simpleNames = lsiClass.simpleNames.toMutableList()
        require(simpleNames.isNotEmpty()) { "LsiClass.simpleNames must not be empty" }
        if (simpleNameConverter != null) {
            val index = simpleNames.lastIndex
            simpleNames[index] = simpleNameConverter.apply(simpleNames[index])
        }
        return LsiClassName(
            packageName = packageName,
            simpleNames = simpleNames.toList(),
            nullable = false,
        )
    }

    @JvmStatic
    fun of(typeElement: TypeElement, simpleNameConverter: UnaryOperator<String>? = null): LsiClassName {
        val collector = Collector()
        collect(typeElement, collector)
        if (simpleNameConverter != null) {
            val index = collector.simpleNames.lastIndex
            collector.simpleNames[index] = simpleNameConverter.apply(collector.simpleNames[index])
        }
        return LsiClassName(
            packageName = collector.packageName,
            simpleNames = collector.simpleNames.toList(),
            nullable = false,
        )
    }

    private fun collect(element: Element, collector: Collector) {
        if (element is PackageElement) {
            collector.packageName = element.qualifiedName.toString()
            return
        }
        collector.simpleNames.add(0, element.simpleName.toString())
        collect(element.enclosingElement, collector)
    }

    private class Collector {
        var packageName: String = ""
        val simpleNames = mutableListOf<String>()
    }
}
