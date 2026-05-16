package org.babyfish.jimmer.apt.entry;

import site.addzero.lsi.clazz.LsiClass;
import java.util.*;
import java.util.regex.Pattern;

public class PackageCollector {

    private static final Pattern DOT_PATTERN = Pattern.compile("\\.");

    private List<String> pathParts;

    private String str;

    private final Map<String, LsiClass> elementMap = new TreeMap<>();

    public void accept(LsiClass typeElement) {
        String qualifiedName = typeElement.getQualifiedName();
        if (qualifiedName == null) {
            return;
        }
        elementMap.put(qualifiedName, typeElement);
        if (pathParts != null && pathParts.isEmpty()) {
            return;
        }
        str = null;
        accept(typeElement.getPackageName());
    }

    private void accept(String path) {
        if (path == null || path.isEmpty()) {
            pathParts = Collections.emptyList();
            return;
        }
        List<String> parts = new ArrayList<>(Arrays.asList(DOT_PATTERN.split(path)));
        if (pathParts == null) {
            pathParts = parts;
        } else {
            int len = Math.min(pathParts.size(), parts.size());
            int index = 0;
            while (index < len) {
                if (!pathParts.get(index).equals(parts.get(index))) {
                    break;
                }
                index++;
            }
            if (index < pathParts.size()) {
                pathParts.subList(index, pathParts.size()).clear();
            }
        }
    }

    public Map<String, LsiClass> getElementMap() {
        return Collections.unmodifiableMap(elementMap);
    }

    @Override
    public String toString() {
        String s = str;
        if (s == null) {
            List<String> ps = pathParts;
            str = s = ps == null || ps.isEmpty() ? "" : String.join(".", ps);
        }
        return s;
    }
}
