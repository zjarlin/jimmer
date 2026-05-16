package org.babyfish.jimmer.apt.entry;

import org.babyfish.jimmer.Immutable;
import org.babyfish.jimmer.sql.Embeddable;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.MappedSuperclass;
import site.addzero.lsi.clazz.LsiClass;

import java.util.*;

public class EntryProcessor {

    private static final String IMMUTABLE_ANNOTATION = Immutable.class.getName();

    private static final String ENTITY_ANNOTATION = Entity.class.getName();

    private static final String EMBEDDABLE_ANNOTATION = Embeddable.class.getName();

    private static final String MAPPED_SUPERCLASS_ANNOTATION = MappedSuperclass.class.getName();

    private final Collection<LsiClass> typeElements;

    private final String immutablesTypeName;

    private final String tablesTypeName;

    private final String tableExesTypeName;

    private final String fetchersTypeName;

    private final boolean buddyIgnoreResourceGeneration;

    public EntryProcessor(
            Collection<LsiClass> typeElements,
            String immutablesTypeName,
            String tablesTypeName,
            String tableExesTypeName,
            String fetchersTypeName,
            boolean buddyIgnoreResourceGeneration
    ) {
        this.typeElements = typeElements;
        this.immutablesTypeName = immutablesTypeName;
        this.tablesTypeName = tablesTypeName;
        this.tableExesTypeName = tableExesTypeName;
        this.fetchersTypeName = fetchersTypeName;
        this.buddyIgnoreResourceGeneration = buddyIgnoreResourceGeneration;
    }

    public void process() {

        PackageCollector packageCollector = new PackageCollector();

        IndexFileGenerator entityGenerator = new IndexFileGenerator(
                typeElements,
                packageCollector,
                buddyIgnoreResourceGeneration
        ) {
            @Override
            protected String getListFilePath() {
                return "META-INF/jimmer/entities";
            }

            @Override
            protected boolean isManaged(LsiClass typeElement, boolean strict) {
                if (strict) {
                    return hasAnnotation(typeElement, ENTITY_ANNOTATION);
                }
                return !hasAnnotation(typeElement, MAPPED_SUPERCLASS_ANNOTATION) &&
                        isImmutableType(typeElement);
            }
        };

        IndexFileGenerator immutableGenerator = new IndexFileGenerator(
                typeElements,
                packageCollector,
                buddyIgnoreResourceGeneration
        ) {
            @Override
            protected String getListFilePath() {
                return "META-INF/jimmer/immutables";
            }

            @Override
            protected boolean isManaged(LsiClass typeElement, boolean strict) {
                if (strict) {
                    return hasAnnotation(typeElement, IMMUTABLE_ANNOTATION) ||
                            hasAnnotation(typeElement, EMBEDDABLE_ANNOTATION);
                }
                return !hasAnnotation(typeElement, MAPPED_SUPERCLASS_ANNOTATION) &&
                        isImmutableType(typeElement);
            }
        };

        String packageName = packageCollector.toString();
        Map<String, LsiClass> allElementMap = packageCollector.getElementMap();
        Map<String, LsiClass> entityElementMap = entityGenerator.getElementMap();

        entityGenerator.generate();
        immutableGenerator.generate();

        if (!allElementMap.isEmpty()) {
            new ImmutablesGenerator(packageName, immutablesTypeName, allElementMap.values()).generate();
        }
        if (!entityElementMap.isEmpty()) {
            new TablesGenerator(packageName, tablesTypeName, entityElementMap.values(), false).generate();
            new TablesGenerator(packageName, tableExesTypeName, entityElementMap.values(), true).generate();
            new FetchersGenerator(packageName, fetchersTypeName, entityElementMap.values()).generate();
        }
    }

    private static boolean hasAnnotation(LsiClass type, String annotationQualifiedName) {
        return type.getAnnotations().stream().anyMatch(it -> annotationQualifiedName.equals(it.getQualifiedName()));
    }

    private static boolean isImmutableType(LsiClass type) {
        return hasAnnotation(type, IMMUTABLE_ANNOTATION) ||
                hasAnnotation(type, ENTITY_ANNOTATION) ||
                hasAnnotation(type, MAPPED_SUPERCLASS_ANNOTATION) ||
                hasAnnotation(type, EMBEDDABLE_ANNOTATION);
    }
}
