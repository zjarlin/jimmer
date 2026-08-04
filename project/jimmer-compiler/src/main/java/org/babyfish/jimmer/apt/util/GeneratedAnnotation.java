package org.babyfish.jimmer.apt.util;

import com.squareup.javapoet.AnnotationSpec;
import org.babyfish.jimmer.apt.immutable.generator.Constants;

public class GeneratedAnnotation {
    private GeneratedAnnotation() {
    }

    public static AnnotationSpec generatedAnnotation() {
        return AnnotationSpec
                .builder(Constants.GENERATED_BY_CLASS_NAME)
                .build();
    }

    public static AnnotationSpec generatedAnnotation(String sourcePath) {
        return AnnotationSpec
                .builder(Constants.GENERATED_BY_CLASS_NAME)
                .addMember(
                        "file",
                        "$S",
                        sourcePath
                )
                .build();
    }
}
