package site.addzero.lsi.jimmer

import site.addzero.lsi.clazz.LsiClass
import site.addzero.lsi.clazz.toLsiClassName
import site.addzero.lsi.clazz.toLsiNestedClassName
import site.addzero.lsi.poet.LsiClassName

private const val DRAFT = "Draft"
private const val PROPS = "Props"
private const val FETCHER = "Fetcher"
private const val FETCHER_DSL = "FetcherDsl"
private const val PROP_EXPRESSION = "PropExpression"
private const val TABLE = "Table"
private const val TABLE_EX = "TableEx"
private const val REMOTE = "Remote"
private const val PRODUCER = "Producer"

/** 实体接口本身的 LsiClassName，例如 `com.example.Book` */
val LsiClass.lsiClassName: LsiClassName
    get() = toLsiClassName()

/** Props 对象的 LsiClassName，例如 `com.example.BookProps` */
val LsiClass.lsiPropsClassName: LsiClassName
    get() = toLsiClassName(nameTransformer = { "$it$PROPS" })

/** Draft 接口的 LsiClassName，例如 `com.example.BookDraft` */
val LsiClass.lsiDraftClassName: LsiClassName
    get() = toLsiClassName(nameTransformer = { "$it$DRAFT" })

/** Fetcher 类的 LsiClassName，例如 `com.example.BookFetcher` */
val LsiClass.lsiFetcherClassName: LsiClassName
    get() = toLsiClassName(nameTransformer = { "$it$FETCHER" })

/** FetcherDsl 接口的 LsiClassName，例如 `com.example.BookFetcherDsl` */
val LsiClass.lsiFetcherDslClassName: LsiClassName
    get() = toLsiClassName(nameTransformer = { "$it$FETCHER_DSL" })

/** PropExpression 类的 LsiClassName，例如 `com.example.AddressPropExpression` */
val LsiClass.lsiPropExpressionClassName: LsiClassName
    get() = toLsiClassName(nameTransformer = { "$it$PROP_EXPRESSION" })

/** Table 类的 LsiClassName，例如 `com.example.BookTable` */
val LsiClass.lsiTableClassName: LsiClassName
    get() = toLsiClassName(nameTransformer = { "$it$TABLE" })

/** TableEx 类的 LsiClassName，例如 `com.example.BookTableEx` */
val LsiClass.lsiTableExClassName: LsiClassName
    get() = toLsiClassName(nameTransformer = { "$it$TABLE_EX" })

/** Remote table nested class，例如 `com.example.BookTable.Remote` */
val LsiClass.lsiRemoteTableClassName: LsiClassName
    get() = lsiTableClassName.copy(simpleNames = lsiTableClassName.simpleNames + REMOTE)

/** Draft.Producer nested class，例如 `com.example.BookDraft.Producer` */
val LsiClass.lsiProducerClassName: LsiClassName
    get() = lsiDraftClassName(PRODUCER)

fun LsiClass.lsiDraftClassName(vararg nestedNames: String): LsiClassName =
    toLsiNestedClassName(namesTransformer = { simpleNames ->
        mutableListOf<String>().apply {
            addAll(simpleNames.dropLast(1))
            add("${simpleNames.last()}$DRAFT")
            addAll(nestedNames)
        }
    })
