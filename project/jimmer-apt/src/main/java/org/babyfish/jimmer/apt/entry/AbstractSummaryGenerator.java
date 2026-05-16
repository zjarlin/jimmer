package org.babyfish.jimmer.apt.entry;

import site.addzero.context.Context;
import site.addzero.lsi.poet.LsiFileSpec;
import site.addzero.lsi.poet.LsiTypeSpec;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class AbstractSummaryGenerator {

    private Map<String, Integer> nameCountMap = new HashMap<>();

    protected String distinctName(String name) {
        int count = nameCountMap.getOrDefault(name, 1);
        nameCountMap.put(name, count + 1);
        if (count == 1) {
            return name;
        }
        return name + '_' + count;
    }

    protected void write(String packageName, LsiTypeSpec typeSpec) {
        Context.INSTANCE.getLsiFiler().createSourceFile(
                new LsiFileSpec(
                        packageName,
                        typeSpec.getName(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.emptyList(),
                        Collections.singletonList(typeSpec)
                )
        );
    }
}
