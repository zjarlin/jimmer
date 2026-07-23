package org.babyfish.jimmer.dto.compiler;

import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;

public final class DtoFile {

    private final String sourcePath;

    private final String content;

    private final String relativePath;

    private final String packageName;

    private final String name;

    public DtoFile(
            String sourcePath,
            String relativePath,
            String content
    ) {
        if (sourcePath == null || sourcePath.trim().isEmpty()) {
            throw new IllegalArgumentException("DTO source path cannot be blank");
        }
        if (!sourcePath.equals(sourcePath.trim().replace('\\', '/'))) {
            throw new IllegalArgumentException("DTO source path must be normalized: '" + sourcePath + '\'');
        }
        if (relativePath == null || relativePath.trim().isEmpty()) {
            throw new IllegalArgumentException("DTO relative path cannot be blank");
        }
        if (!relativePath.equals(relativePath.trim().replace('\\', '/')) || relativePath.startsWith("/")) {
            throw new IllegalArgumentException("DTO relative path must be normalized: '" + relativePath + '\'');
        }
        String[] parts = relativePath.split("/", -1);
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) {
                throw new IllegalArgumentException("DTO relative path must be normalized: '" + relativePath + '\'');
            }
        }
        if (content == null) {
            throw new IllegalArgumentException("DTO content cannot be null");
        }
        this.sourcePath = sourcePath;
        this.content = content;
        this.relativePath = relativePath;
        this.packageName = String.join(".", Arrays.copyOf(parts, parts.length - 1));
        this.name = parts[parts.length - 1];
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getContent() {
        return content;
    }

    public Reader openReader() {
        return new StringReader(content);
    }

    public String getRelativePath() {
        return relativePath;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return sourcePath;
    }
}
