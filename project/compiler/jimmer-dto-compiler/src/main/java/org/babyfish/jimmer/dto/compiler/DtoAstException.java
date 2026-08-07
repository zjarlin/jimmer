package org.babyfish.jimmer.dto.compiler;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class DtoAstException extends RuntimeException {

    private final String sourcePath;

    private final int lineNumber;

    private final int colNumber;

    public DtoAstException(DtoFile file, int lineNumber, int colNumber, String message) {
        super(
                file.getSourcePath() +
                        ':' +
                        lineNumber +
                        " : " +
                        message + positionString(file, lineNumber, colNumber)
        );
        this.sourcePath = file.getSourcePath();
        this.lineNumber = lineNumber;
        this.colNumber = colNumber;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getColNumber() {
        return colNumber;
    }

    private static String positionString(DtoFile dtoFile, int lineNumber, int colNumber) {
        try (BufferedReader reader = new BufferedReader(dtoFile.openReader())) {
            int lineNo = 1;
            while (true) {
                String line = reader.readLine();
                if (line == null) {
                    break;
                }
                if (lineNo == lineNumber) {
                    StringBuilder builder = new StringBuilder();
                    builder.append('\n').append(line).append('\n');
                    for (int i = colNumber; i > 0; --i) {
                        builder.append(' ');
                    }
                    builder.append('^');
                    return builder.toString();
                }
                lineNo++;
            }
        } catch (IOException ex) {
            return "";
        }
        return "";
    }
}
