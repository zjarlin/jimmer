package org.babyfish.jimmer.compiler.input

import java.io.IOException
import java.net.JarURLConnection
import java.net.URL
import java.net.URLConnection
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import org.babyfish.jimmer.compiler.CompilerInputDocument
import org.babyfish.jimmer.compiler.CompilerInputDocumentKind
import org.babyfish.jimmer.compiler.CompilerInputDocumentOrigin
import org.babyfish.jimmer.compiler.CompilerSourceSet

internal class CompilerInputDocumentBundleReader(
    private val classLoader: ClassLoader,
) {

    fun read(): List<CompilerInputDocument> {
        val markerUrls = classLoader.getResources(MARKER_PATH)
            .toList()
            .distinctBy(URL::toExternalForm)
            .sortedBy(URL::toExternalForm)
        val descriptors = markerUrls.map(::readDescriptor)
        descriptors.groupBy(BundleDescriptor::bundleId).forEach { (bundleId, candidates) ->
            require(candidates.map(BundleDescriptor::signature).distinct().size == 1) {
                "DTO bundle id '$bundleId' is declared by different manifests: " +
                    candidates.map { descriptor -> descriptor.markerUrl.toExternalForm() }
                        .sorted()
                        .joinToString()
            }
        }
        return descriptors
            .distinctBy(BundleDescriptor::bundleId)
            .sortedBy(BundleDescriptor::bundleId)
            .flatMap(BundleDescriptor::documents)
            .sorted()
    }

    private fun readDescriptor(markerUrl: URL): BundleDescriptor {
        require(markerUrl.protocol == "file" || markerUrl.protocol == "jar") {
            "Cannot load DTO bundle marker '$markerUrl': unsupported URL protocol '${markerUrl.protocol}'"
        }
        val manifest = markerUrl.readManifest()
        val format = manifest.requiredValue(FORMAT_PROPERTY, markerUrl)
        require(format == FORMAT_VERSION) {
            "DTO bundle marker '$markerUrl' uses unsupported format '$format'"
        }
        val bundleId = manifest.requiredValue(BUNDLE_ID_PROPERTY, markerUrl)
        require(BUNDLE_ID_REGEX.matches(bundleId)) {
            "DTO bundle marker '$markerUrl' contains invalid bundle id '$bundleId'"
        }
        val documentCount = manifest.requiredValue(DOCUMENT_COUNT_PROPERTY, markerUrl).toIntOrNull()
        require(documentCount != null && documentCount >= 0) {
            "DTO bundle marker '$markerUrl' contains invalid document count " +
                "'${manifest[DOCUMENT_COUNT_PROPERTY]}'"
        }
        val expectedProperties = buildList {
            add(FORMAT_PROPERTY)
            add(BUNDLE_ID_PROPERTY)
            add(DOCUMENT_COUNT_PROPERTY)
            repeat(documentCount) { index ->
                DOCUMENT_PROPERTIES.forEach { suffix -> add("document.$index.$suffix") }
            }
        }
        val actualProperties = manifest.keys.toList()
        val missingProperties = expectedProperties.toSet() - actualProperties.toSet()
        val unsupportedProperties = actualProperties.toSet() - expectedProperties.toSet()
        require(actualProperties == expectedProperties) {
            buildString {
                append("DTO bundle marker '")
                append(markerUrl)
                append("' does not match format ")
                append(FORMAT_VERSION)
                if (missingProperties.isNotEmpty()) {
                    append(", missing: ")
                    append(missingProperties.sorted().joinToString())
                }
                if (unsupportedProperties.isNotEmpty()) {
                    append(", unsupported: ")
                    append(unsupportedProperties.sorted().joinToString())
                }
                if (missingProperties.isEmpty() && unsupportedProperties.isEmpty()) {
                    append(", properties are not in canonical order")
                }
            }
        }
        val documents = (0 until documentCount).map { index ->
            val prefix = "document.$index."
            val sourceSetText = manifest.requiredValue(prefix + SOURCE_SET_SUFFIX, markerUrl)
            val sourceSet = try {
                CompilerSourceSet.valueOf(sourceSetText)
            } catch (exception: IllegalArgumentException) {
                throw IllegalArgumentException(
                    "DTO bundle marker '$markerUrl' contains invalid source set '$sourceSetText'",
                    exception,
                )
            }
            val sourceRoot = manifest.requiredPath(prefix + SOURCE_ROOT_SUFFIX, markerUrl)
            require(sourceRoot.startsWith(sourceSet.dtoSourceRootPrefix)) {
                "DTO bundle marker '$markerUrl' source root '$sourceRoot' does not start with " +
                    "'${sourceSet.dtoSourceRootPrefix}' for $sourceSet"
            }
            val relativePath = manifest.requiredPath(prefix + RELATIVE_PATH_SUFFIX, markerUrl)
            require(relativePath.endsWith(".dto")) {
                "DTO bundle marker '$markerUrl' document '$relativePath' must use the .dto extension"
            }
            val resourcePath = manifest.requiredPath(prefix + RESOURCE_SUFFIX, markerUrl)
            val expectedChecksum = manifest.requiredValue(prefix + SHA_256_SUFFIX, markerUrl)
            require(SHA_256_REGEX.matches(expectedChecksum)) {
                "DTO bundle marker '$markerUrl' contains invalid SHA-256 '$expectedChecksum'"
            }
            val bytes = markerUrl.readSiblingResource(resourcePath)
            val actualChecksum = sha256(bytes)
            require(actualChecksum == expectedChecksum) {
                "DTO bundle marker '$markerUrl' document '$relativePath' checksum mismatch: " +
                    "expected $expectedChecksum, actual $actualChecksum"
            }
            CompilerInputDocument(
                kind = CompilerInputDocumentKind.DTO,
                sourceSet = sourceSet,
                origin = CompilerInputDocumentOrigin.Bundle(
                    bundleId = bundleId,
                    sourceRoot = sourceRoot,
                    resourcePath = resourcePath,
                    contentSha256 = expectedChecksum,
                ),
                relativePath = relativePath,
                content = bytes.decodeUtf8(resourcePath),
            )
        }
        require(documents.distinctBy { document -> document.source.path }.size == documents.size) {
            "DTO bundle marker '$markerUrl' declares duplicate document source paths"
        }
        return BundleDescriptor(markerUrl, bundleId, documents)
    }

    private data class BundleDescriptor(
        val markerUrl: URL,
        val bundleId: String,
        val documents: List<CompilerInputDocument>,
    ) {
        val signature: String = documents
            .sorted()
            .joinToString(separator = "\u0000") { document -> document.fingerprint }
    }

    companion object {

        const val ENABLED_OPTION = "jimmer.dto.bundle.enabled"

        const val MARKER_PATH = "META-INF/jimmer/dto-bundle.properties"

        private const val FORMAT_PROPERTY = "format"

        private const val FORMAT_VERSION = "2"

        private const val BUNDLE_ID_PROPERTY = "bundleId"

        private const val DOCUMENT_COUNT_PROPERTY = "document.count"

        private const val SOURCE_SET_SUFFIX = "sourceSet"

        private const val SOURCE_ROOT_SUFFIX = "sourceRoot"

        private const val RELATIVE_PATH_SUFFIX = "relativePath"

        private const val RESOURCE_SUFFIX = "resource"

        private const val SHA_256_SUFFIX = "sha256"

        private val DOCUMENT_PROPERTIES = listOf(
            SOURCE_SET_SUFFIX,
            SOURCE_ROOT_SUFFIX,
            RELATIVE_PATH_SUFFIX,
            RESOURCE_SUFFIX,
            SHA_256_SUFFIX,
        )

        private val BUNDLE_ID_REGEX = Regex("[A-Za-z0-9][A-Za-z0-9_.:-]*")

        private val SHA_256_REGEX = Regex("[0-9a-f]{64}")

        fun isEnabled(options: Map<String, String>): Boolean {
            return when (val value = options[ENABLED_OPTION]?.trim()?.lowercase()) {
                null, "", "true" -> true
                "false" -> false
                else -> throw IllegalArgumentException(
                    "The processor option `$ENABLED_OPTION` can only be \"true\" or \"false\"",
                )
            }
        }
    }
}

private fun URL.readManifest(): Map<String, String> {
    val connection = openConnection().withoutCaches()
    val text = connection.getInputStream().use { input ->
        input.readBytes().decodeUtf8(toExternalForm())
    }
    require(text.endsWith('\n') && '\r' !in text) {
        "DTO bundle marker '$this' must be UTF-8 with LF line endings and a final newline"
    }
    return buildMap {
        text.dropLast(1).split('\n').forEachIndexed { index, line ->
            val separatorIndex = line.indexOf('=')
            require(separatorIndex > 0) {
                "DTO bundle marker '$this' has invalid line ${index + 1}"
            }
            val name = line.substring(0, separatorIndex)
            val value = line.substring(separatorIndex + 1)
            require(name == name.trim() && value == value.trim() && value.isNotEmpty()) {
                "DTO bundle marker '$this' has non-canonical line ${index + 1}"
            }
            require(put(name, value) == null) {
                "DTO bundle marker '$this' declares duplicate property '$name'"
            }
        }
    }
}

private fun Map<String, String>.requiredValue(name: String, markerUrl: URL): String {
    return this[name] ?: throw IllegalArgumentException("DTO bundle marker '$markerUrl' is missing '$name'")
}

private fun Map<String, String>.requiredPath(name: String, markerUrl: URL): String {
    val path = requiredValue(name, markerUrl)
    require(path == path.replace('\\', '/') && !path.startsWith('/')) {
        "DTO bundle marker '$markerUrl' contains non-normalized path '$path'"
    }
    require(path.split('/').none { segment -> segment.isBlank() || segment == "." || segment == ".." }) {
        "DTO bundle marker '$markerUrl' contains non-normalized path '$path'"
    }
    return path
}

private fun URL.readSiblingResource(resourcePath: String): ByteArray {
    return when (protocol) {
        "jar" -> readJarResource(resourcePath)
        "file" -> readFileResource(resourcePath)
        else -> throw IOException(
            "Cannot load DTO bundle marker '$this': unsupported URL protocol '$protocol'",
        )
    }
}

private fun URL.readJarResource(resourcePath: String): ByteArray {
    val connection = openConnection().withoutCaches()
    require(connection is JarURLConnection) {
        "Cannot open DTO bundle marker '$this' as a JAR resource"
    }
    return connection.jarFile.use { jarFile ->
        val entry = jarFile.getJarEntry(resourcePath)
            ?: throw IOException("DTO bundle marker '$this' references missing resource '$resourcePath'")
        if (entry.isDirectory) {
            throw IOException("DTO bundle marker '$this' references directory '$resourcePath'")
        }
        jarFile.getInputStream(entry).use { input -> input.readBytes() }
    }
}

private fun URL.readFileResource(resourcePath: String): ByteArray {
    val markerPath = try {
        Paths.get(toURI())
    } catch (exception: Exception) {
        throw IOException("Cannot resolve DTO bundle marker '$this'", exception)
    }
    val classpathRoot = MARKER_SEGMENTS.fold(markerPath) { path, _ ->
        path.parent ?: throw IOException("Illegal DTO bundle marker path '$markerPath'")
    }
    val resource = classpathRoot.resolve(resourcePath).normalize()
    if (!Files.exists(resource)) {
        throw IOException("DTO bundle marker '$this' references missing resource '$resourcePath'")
    }
    val classpathRootRealPath = classpathRoot.toRealPath()
    val resourceRealPath = resource.toRealPath()
    require(resourceRealPath.startsWith(classpathRootRealPath)) {
        "DTO bundle resource '$resourcePath' escapes classpath root '$classpathRoot'"
    }
    if (!Files.isRegularFile(resourceRealPath)) {
        throw IOException("DTO bundle marker '$this' references missing resource '$resourcePath'")
    }
    return Files.readAllBytes(resourceRealPath)
}

private fun ByteArray.decodeUtf8(resourcePath: String): String {
    return try {
        StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(this))
            .toString()
    } catch (exception: Exception) {
        throw IOException("DTO bundle resource '$resourcePath' is not valid UTF-8", exception)
    }
}

private fun sha256(bytes: ByteArray): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { byte -> "%02x".format(byte) }
}

private fun URLConnection.withoutCaches(): URLConnection = apply { useCaches = false }

private fun <T> java.util.Enumeration<T>.toList(): List<T> = buildList {
    while (hasMoreElements()) {
        add(nextElement())
    }
}

private val MARKER_SEGMENTS = CompilerInputDocumentBundleReader.MARKER_PATH.split('/')
