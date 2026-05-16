package site.addzero.lsi.poet

class LsiNameAllocator {

    private val allocatedNames = mutableSetOf<String>()

    fun snapshot(): Set<String> = allocatedNames.toSet()

    fun newName(suggestedName: String): String {
        var candidate = sanitize(suggestedName)
        if (allocatedNames.add(candidate)) {
            return candidate
        }
        var index = 1
        while (true) {
            val next = candidate + index++
            if (allocatedNames.add(next)) {
                return next
            }
        }
    }

    fun reserve(name: String) {
        allocatedNames += sanitize(name)
    }

    fun copy(): LsiNameAllocator =
        LsiNameAllocator().also { it.allocatedNames += allocatedNames }

    private fun sanitize(value: String): String =
        value.replace(Regex("[^A-Za-z0-9_]"), "_")
            .let { if (it.isBlank()) "_" else it }
}
