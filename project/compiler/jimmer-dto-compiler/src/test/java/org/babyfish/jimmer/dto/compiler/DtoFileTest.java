package org.babyfish.jimmer.dto.compiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

public class DtoFileTest {

    @Test
    public void testFrozenUtf8ContentAndPath() throws IOException {
        Path source = Files.createTempFile("jimmer-dto-file", ".dto");
        String originalContent = "第一行\r\n第二行";
        Files.write(source, originalContent.getBytes(StandardCharsets.UTF_8));
        String sourcePath = source.toFile().getAbsolutePath().replace('\\', '/');
        DtoFile dtoFile = new DtoFile(
                sourcePath,
                "org/babyfish/jimmer/Book.dto",
                new String(Files.readAllBytes(source), StandardCharsets.UTF_8)
        );

        Files.write(source, "已修改".getBytes(StandardCharsets.UTF_8));

        Reader firstReader = dtoFile.openReader();
        Reader secondReader = dtoFile.openReader();
        assertEquals(sourcePath, dtoFile.getSourcePath());
        assertEquals("org/babyfish/jimmer/Book.dto", dtoFile.getRelativePath());
        assertEquals("org.babyfish.jimmer", dtoFile.getPackageName());
        assertEquals("Book.dto", dtoFile.getName());
        assertEquals(originalContent, dtoFile.getContent());
        assertNotSame(firstReader, secondReader);
        assertEquals(originalContent, read(firstReader));
        assertEquals(originalContent, read(secondReader));
        assertEquals(sourcePath, dtoFile.toString());
    }

    private static String read(Reader reader) throws IOException {
        try (Reader currentReader = reader) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[64];
            int readCount;
            while ((readCount = currentReader.read(buffer)) != -1) {
                builder.append(buffer, 0, readCount);
            }
            return builder.toString();
        }
    }
}
