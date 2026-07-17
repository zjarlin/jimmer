package org.babyfish.jimmer.dto.compiler;

import java.io.Reader;
import java.io.StringReader;
import java.util.List;

public final class DtoFile {

    private final String absolutePath;

    private final String content;

    private final String projectDir;

    private final String dtoDir;

    private final String packageName;

    private final String name;

    private final String path;

    public DtoFile(
            String absolutePath,
            String content,
            String projectDir,
            String dtoDir,
            List<String> packagePaths,
            String name
    ) {
        this.absolutePath = absolutePath;
        this.content = content;
        this.projectDir = projectDir;
        this.dtoDir = dtoDir;
        this.packageName = String.join(".", packagePaths);
        this.name = name;
        this.path = '<' + projectDir + '>' +
                (dtoDir.isEmpty() ? "" : '/' + dtoDir) +
                (packagePaths.isEmpty() ? "" : '/' + String.join("/", packagePaths)) +
                '/' + name;
    }

    public String getAbsolutePath() {
        return absolutePath;
    }

    public String getContent() {
        return content;
    }

    public Reader openReader() {
        return new StringReader(content);
    }

    public String getProjectDir() {
        return projectDir;
    }

    public String getDtoDir() {
        return dtoDir;
    }

    public String getPackageName() {
        return packageName;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        DtoFile dtoFile = (DtoFile) o;
        return path.equals(dtoFile.path);
    }

    @Override
    public String toString() {
        return path;
    }
}
