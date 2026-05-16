package org.babyfish.jimmer.apt.entry;

import site.addzero.context.Context;
import site.addzero.lsi.apt.diagnostic.AptLsiDiagnostics;
import site.addzero.lsi.clazz.LsiClass;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;

public abstract class IndexFileGenerator {

    private final boolean buddyIgnoreResourceGeneration;

    private final Map<String, LsiClass> elementMap;

    private final String listFilePath;

    public IndexFileGenerator(
            Collection<LsiClass> typeElements,
            PackageCollector packageCollector,
            boolean buddyIgnoreResourceGeneration
    ) {
        this.buddyIgnoreResourceGeneration = buddyIgnoreResourceGeneration;
        this.listFilePath = getListFilePath();

        Map<String, LsiClass> elementMap = new TreeMap<>();
        for (LsiClass typeElement : typeElements) {
            if (typeElement.isInterface()) {
                String qualifiedName = typeElement.getQualifiedName();
                if (qualifiedName == null) {
                    continue;
                }
                if (isManaged(typeElement, true)) {
                    elementMap.put(qualifiedName, typeElement);
                }
                if (isManaged(typeElement, false)) {
                    packageCollector.accept(typeElement);
                }
            }
        }

        String existingContent;
        try {
            existingContent = Context.INSTANCE.getLsiFiler().readResourceText(listFilePath);
        } catch (Exception ex) {
            throw AptLsiDiagnostics.generatorException("Cannot get file object \"" + listFilePath + "\"", ex);
        }
        if (existingContent != null) {
            // For command line or IDE
            try (BufferedReader reader = new BufferedReader(new StringReader(existingContent))) {
                while (true) {
                    String line = reader.readLine();
                    if (line == null) {
                        break;
                    }
                    line = line.trim();
                    if (!line.isEmpty()) {
                        LsiClass typeElement = Context.INSTANCE.getLsiResolver().findClassByQualifiedName(line);
                        if (typeElement != null) {
                            if (isManaged(typeElement, true)) {
                                elementMap.put(line, typeElement);
                            }
                            if (isManaged(typeElement, false)) {
                                packageCollector.accept(typeElement);
                            }
                        }
                    }
                }
            } catch (IOException ex) {
                throw AptLsiDiagnostics.generatorException("Cannot read content of \"" + listFilePath + "\"", ex);
            }
        } else {
            // For jimmer buddy
            for (LsiClass typeElement : typeElements) {
                if (typeElement != null) {
                    String qualifiedName = typeElement.getQualifiedName();
                    if (qualifiedName == null) {
                        continue;
                    }
                    if (isManaged(typeElement, true)) {
                        elementMap.put(qualifiedName, typeElement);
                    }
                    if (isManaged(typeElement, false)) {
                        packageCollector.accept(typeElement);
                    }
                }
            }
        }
        this.elementMap = elementMap;
    }

    public void generate() {
        if (buddyIgnoreResourceGeneration) {
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (String qualifiedName : elementMap.keySet()) {
            builder.append(qualifiedName).append('\n');
        }
        try {
            Context.INSTANCE.getLsiFiler().overwriteResourceFile(listFilePath, builder.toString());
        } catch (Exception ex) {
            throw AptLsiDiagnostics.generatorException("Cannot write \"" + listFilePath + "\"", ex);
        }
    }

    public Map<String, LsiClass> getElementMap() {
        return Collections.unmodifiableMap(elementMap);
    }

    protected abstract String getListFilePath();

    protected abstract boolean isManaged(LsiClass typeElement, boolean strict);
}
