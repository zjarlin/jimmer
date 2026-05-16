package site.addzero.lsi.assist

import site.addzero.lsi.poet.isLsiCollectionLikeQualifiedName

fun String?.isCollectionType(): Boolean {
    return isLsiCollectionLikeQualifiedName()
}
