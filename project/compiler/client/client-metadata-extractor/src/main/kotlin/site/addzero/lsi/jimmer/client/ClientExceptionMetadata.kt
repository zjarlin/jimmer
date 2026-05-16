package site.addzero.lsi.jimmer.client

import site.addzero.lsi.clazz.LsiClass

data class ClientExceptionMetadata(
    val declaration: LsiClass,
    val family: String,
    val code: String?,
    val superMetadata: ClientExceptionMetadata?
) {
    private lateinit var _subMetadatas: List<ClientExceptionMetadata>

    var subMetadatas: List<ClientExceptionMetadata>
        get() = _subMetadatas
        internal set(value) { _subMetadatas = value }
}
