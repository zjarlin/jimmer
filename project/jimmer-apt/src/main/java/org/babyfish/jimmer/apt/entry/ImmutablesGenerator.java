package org.babyfish.jimmer.apt.entry;

import site.addzero.lsi.clazz.LsiClass;
import site.addzero.lsi.poet.LsiAnnotationSpec;
import site.addzero.lsi.poet.LsiCallableSpec;
import site.addzero.lsi.poet.LsiCallableSpecKind;
import site.addzero.lsi.poet.LsiCallExpression;
import site.addzero.lsi.poet.LsiModifier;
import site.addzero.lsi.poet.LsiParameterSpec;
import site.addzero.lsi.poet.LsiPropertyAccessExpression;
import site.addzero.lsi.poet.LsiReturnStatement;
import site.addzero.lsi.poet.LsiTypeExpression;
import site.addzero.lsi.poet.LsiTypeSpec;
import site.addzero.lsi.poet.LsiTypeSpecKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import static site.addzero.lsi.codegen.JavaCodegenConstants.DRAFT_CONSUMER_CLASS_NAME;
import static site.addzero.lsi.codegen.JimmerCodegenAnnotationExtKt.generatedAnnotation;
import static site.addzero.lsi.clazz.LsiClassNameExtKt.toLsiClassName;

public class ImmutablesGenerator extends AbstractSummaryGenerator {

    private final String packageName;

    private final String simpleName;

    private final Collection<LsiClass> typeElements;

    public ImmutablesGenerator(String packageName, String simpleName, Collection<LsiClass> typeElements) {
        this.packageName = packageName;
        this.typeElements = typeElements;
        this.simpleName = simpleName;
    }

    public void generate() {
        write(packageName, typeSpec());
    }

    private LsiTypeSpec typeSpec() {
        List<LsiCallableSpec> callables = new ArrayList<>(typeElements.size() * 2);
        for (LsiClass typeElement : typeElements) {
            String methodName = distinctName("create" + typeElement.getSimpleName());
            callables.add(creator(typeElement, methodName, false));
            callables.add(creator(typeElement, methodName, true));
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
                Collections.emptyList(),
                callables,
                Collections.emptyList(),
                null
        );
    }

    private LsiCallableSpec creator(LsiClass typeElement, String methodName, boolean withBase) {
        site.addzero.lsi.poet.LsiClassName immutableClassName = toLsiClassName(typeElement, name -> name, false);
        site.addzero.lsi.poet.LsiClassName draftClassName = toLsiClassName(typeElement, name -> name + "Draft", false);
        List<LsiParameterSpec> parameters = new ArrayList<>(withBase ? 2 : 1);
        if (withBase) {
            parameters.add(
                    new LsiParameterSpec(
                            "base",
                            immutableClassName,
                            Collections.emptyList(),
                            Collections.emptySet(),
                            null
                    )
            );
        }
        parameters.add(
                new LsiParameterSpec(
                        "block",
                        new site.addzero.lsi.poet.LsiParameterizedTypeName(
                                DRAFT_CONSUMER_CLASS_NAME,
                                Collections.singletonList(draftClassName),
                                false
                        ),
                        Collections.emptyList(),
                        Collections.emptySet(),
                        null
                )
        );
        List<site.addzero.lsi.poet.LsiExpression> arguments = new ArrayList<>(withBase ? 2 : 1);
        if (withBase) {
            arguments.add(new site.addzero.lsi.poet.LsiNameExpression("base"));
        }
        arguments.add(new site.addzero.lsi.poet.LsiNameExpression("block"));
        return new LsiCallableSpec(
                LsiCallableSpecKind.FUNCTION,
                methodName,
                false,
                null,
                Collections.<LsiAnnotationSpec>emptyList(),
                EnumSet.of(LsiModifier.PUBLIC, LsiModifier.STATIC),
                Collections.emptyList(),
                parameters,
                immutableClassName,
                Collections.emptyList(),
                null,
                Collections.singletonList(
                        new LsiReturnStatement(
                                new LsiCallExpression(
                                        new LsiPropertyAccessExpression(new LsiTypeExpression(draftClassName), "$$"),
                                        "produce",
                                        Collections.emptyList(),
                                        arguments
                                )
                        )
                )
        );
    }
}
