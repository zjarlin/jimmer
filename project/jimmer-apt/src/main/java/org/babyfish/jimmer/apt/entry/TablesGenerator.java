package org.babyfish.jimmer.apt.entry;

import org.babyfish.jimmer.impl.util.StringUtil;
import site.addzero.lsi.clazz.LsiClass;
import site.addzero.lsi.poet.LsiAnnotationSpec;
import site.addzero.lsi.poet.LsiModifier;
import site.addzero.lsi.poet.LsiPropertyAccessExpression;
import site.addzero.lsi.poet.LsiPropertySpec;
import site.addzero.lsi.poet.LsiTypeExpression;
import site.addzero.lsi.poet.LsiTypeSpec;
import site.addzero.lsi.poet.LsiTypeSpecKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static site.addzero.lsi.codegen.JimmerCodegenAnnotationExtKt.generatedAnnotation;
import static site.addzero.lsi.clazz.LsiClassNameExtKt.toLsiClassName;

public class TablesGenerator extends AbstractSummaryGenerator {

    private final String packageName;

    private final String simpleName;

    private final Collection<LsiClass> typeElements;

    private final boolean isEx;

    public TablesGenerator(String packageName, String simpleName, Collection<LsiClass> typeElements, boolean isEx) {
        this.packageName = packageName;
        this.simpleName = simpleName;
        this.typeElements = typeElements;
        this.isEx = isEx;
    }

    public void generate() {
        write(packageName, typeSpec());
    }

    private LsiTypeSpec typeSpec() {
        List<LsiPropertySpec> properties = new ArrayList<>(typeElements.size());
        for (LsiClass typeElement : typeElements) {
            properties.add(field(typeElement));
        }
        return new LsiTypeSpec(
                simpleName,
                LsiTypeSpecKind.INTERFACE,
                Collections.singletonList(generatedAnnotation()),
                EnumSet.of(LsiModifier.PUBLIC),
                null,
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                properties,
                Collections.emptyList(),
                Collections.emptyList(),
                null
        );
    }

    private LsiPropertySpec field(LsiClass typeElement) {
        String suffix = isEx ? "TableEx" : "Table";
        site.addzero.lsi.poet.LsiClassName tableClassName =
                toLsiClassName(typeElement, name -> name + suffix, false);
        return new LsiPropertySpec(
                distinctName(
                        StringUtil.snake(
                                typeElement.getSimpleName() + suffix,
                                StringUtil.SnakeCase.UPPER
                        )
                ),
                tableClassName,
                null,
                Collections.<LsiAnnotationSpec>emptyList(),
                EnumSet.of(LsiModifier.PUBLIC, LsiModifier.STATIC, LsiModifier.FINAL),
                false,
                new LsiPropertyAccessExpression(new LsiTypeExpression(tableClassName), "$$"),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptySet()
        );
    }
}
