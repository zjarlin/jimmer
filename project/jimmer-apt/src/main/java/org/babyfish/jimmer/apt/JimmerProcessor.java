package org.babyfish.jimmer.apt;

import kotlin.Unit;
import site.addzero.context.Context;
import site.addzero.context.Settings;
import site.addzero.lsi.apt.diagnostic.AptLsiDiagnostics;
import org.babyfish.jimmer.apt.client.ClientProcessor;
import org.babyfish.jimmer.apt.client.ExportDocProcessor;
import org.babyfish.jimmer.apt.client.FetchByUnsupportedException;
import org.babyfish.jimmer.apt.dto.DtoProcessor;
import org.babyfish.jimmer.apt.entry.EntryProcessor;
import org.babyfish.jimmer.apt.error.ErrorProcessor;
import org.babyfish.jimmer.apt.immutable.ImmutableProcessor;
import org.babyfish.jimmer.apt.transactional.TxProcessor;
import org.babyfish.jimmer.apt.tuple.TypedTupleProcessor;
import org.babyfish.jimmer.client.EnableImplicitApi;
import org.babyfish.jimmer.client.FetchBy;
import org.babyfish.jimmer.dto.compiler.DtoAstException;
import org.babyfish.jimmer.dto.compiler.DtoUtils;
import org.babyfish.jimmer.sql.EnableDtoGeneration;
import org.babyfish.jimmer.sql.TypedTuple;
import site.addzero.lsi.apt.clazz.AptLsiClassDocMetadata;
import site.addzero.lsi.apt.context.AptLsiContext;
import site.addzero.lsi.clazz.LsiClass;
import site.addzero.lsi.diagnostic.LsiDiagnosticAnchor;
import site.addzero.lsi.diagnostic.MetaException;
import site.addzero.lsi.jimmer.dto.LsiDtoModifier;

import java.io.File;
import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@SupportedAnnotationTypes({
        "org.babyfish.jimmer.Immutable",
        "org.babyfish.jimmer.sql.Entity",
        "org.babyfish.jimmer.sql.MappedSuperclass",
        "org.babyfish.jimmer.sql.Embeddable",
        "org.babyfish.jimmer.sql.EnableDtoGeneration",
        "org.babyfish.jimmer.error.ErrorFamily",
        "org.babyfish.jimmer.client.Api",
        "org.babyfish.jimmer.client.ExportDoc",
        "org.springframework.web.bind.annotation.RestController",
        "org.babyfish.jimmer.sql.transaction.Tx"
})
public class JimmerProcessor extends AbstractProcessor {

    private Elements elements;

    private Messager messager;

    private Collection<String> dtoDirs;

    private Collection<String> dtoTestDirs;

    private LsiDtoModifier defaultNullableInputModifier = LsiDtoModifier.STATIC;

    private boolean checkedException;

    private boolean ignoreJdkWarning;

    private Boolean clientExplicitApi;

    private boolean modelGenerated;

    private boolean toolGenerated;

    private String immutablesTypeName = "Immutables";

    private String tablesTypeName = "Tables";

    private String tableExesTypeName = "TableExes";

    private String fetchersTypeName = "Fetchers";

    private boolean buddyIgnoreResourceGeneration;

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        AptLsiContext.INSTANCE.init(processingEnv, null);
        messager = processingEnv.getMessager();
        this.dtoDirs = dtoDirs(
                processingEnv,
                "jimmer.dto.dirs",
                "src/main/",
                Collections.singletonList("src/main/dto")
        );
        this.dtoTestDirs = dtoDirs(
                processingEnv,
                "jimmer.dto.testDirs",
                "src/test/",
                Collections.singletonList("src/test/dto")
        );
        String inputModifierText = processingEnv.getOptions().get("jimmer.dto.defaultNullableInputModifier");
        if (inputModifierText != null && !inputModifierText.isEmpty()) {
            defaultNullableInputModifier = LsiDtoModifier.fromNullableInputOption(inputModifierText);
        }

        checkedException = "true".equals(processingEnv.getOptions().get("jimmer.client.checkedException"));
        ignoreJdkWarning = "true".equals(processingEnv.getOptions().get("jimmer.client.ignoreJdkWarning"));
        elements = processingEnv.getElementUtils();
        immutablesTypeName = defaultEntryTypeName(processingEnv.getOptions().get("jimmer.entry.immutables"), "Immutables");
        tablesTypeName = defaultEntryTypeName(processingEnv.getOptions().get("jimmer.entry.tables"), "Tables");
        tableExesTypeName = defaultEntryTypeName(processingEnv.getOptions().get("jimmer.entry.tableExes"), "TableExes");
        fetchersTypeName = defaultEntryTypeName(processingEnv.getOptions().get("jimmer.entry.fetchers"), "Fetchers");
        buddyIgnoreResourceGeneration =
                "true".equals(processingEnv.getOptions().get("jimmer.buddy.ignoreResourceGeneration"));
    }

    @Override
    public boolean process(
            Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv
    ) {
        AptLsiContext.INSTANCE.resetRound(roundEnv, null);
        try {
            resetSharedCompilerContext();
            if (clientExplicitApi == null) {
                clientExplicitApi = Context.INSTANCE.getExplicitClientApi();
            }
            if (!modelGenerated) {
                modelGenerated = true;
                if (runModelPhase(roundEnv)) {
                    return true;
                }
            }
            if (!toolGenerated && !buddyIgnoreResourceGeneration) {
                toolGenerated = true;
                runToolPhase();
            }
        } catch (MetaException ex) {
            printLsiMetaException(ex);
        } catch (DtoAstException ex) {
            Collection<? extends Element> elements = roundEnv.getElementsAnnotatedWith(EnableDtoGeneration.class);
            if (elements.isEmpty()) {
                messager.printMessage(Diagnostic.Kind.ERROR, ex.getMessage());
                throw ex;
            } else {
                messager.printMessage(Diagnostic.Kind.ERROR, ex.getMessage(), elements.iterator().next());
            }
        } catch (FetchByUnsupportedException ex) {
            Collection<? extends Element> elements = roundEnv.getElementsAnnotatedWith(EnableImplicitApi.class);
            String message =
                    "In order to parse the `@" +
                            FetchBy.class.getName() +
                            "` annotations that decorate generic type parameters, " +
                            "please make sure the java compiler version is 11 or higher " +
                            "(`source.version` and `target.version` can still remain `1.8`). " +
                            "However, once compilation is complete, " +
                            "you can still use Java 8 to deploy and run the project";
            if (ignoreJdkWarning) {
                messager.printMessage(
                        Diagnostic.Kind.WARNING,
                        message
                );
            } else {
                message += ". If you want to suppress this error" +
                        "(Note, this will lead to generating incorrect client code such as openapi and typescript), " +
                        "please add the argument `-Ajimmer.client.ignoreJdkWarning=true` to java compiler by maven or gradle";
                if (elements.isEmpty()) {
                    messager.printMessage(
                            Diagnostic.Kind.ERROR,
                            message
                    );
                    throw ex;
                } else {
                    messager.printMessage(
                            Diagnostic.Kind.ERROR,
                            message,
                            elements.iterator().next()
                    );
                }
            }
        }
        return true;
    }

    /**
     * 第一阶段只做模型类与其直接依赖产物的生成。
     * 如果这一阶段产出了会影响 tuple/client/export-doc 的新类型，就等待下一轮再进入工具阶段。
     */
    private boolean runModelPhase(RoundEnvironment roundEnv) {
        Collection<LsiClass> immutableTypeElements =
                new ImmutableProcessor(buddyIgnoreResourceGeneration).process();
        new EntryProcessor(
                immutableTypeElements,
                immutablesTypeName,
                tablesTypeName,
                tableExesTypeName,
                fetchersTypeName,
                buddyIgnoreResourceGeneration
        ).process();
        boolean errorGenerated = new ErrorProcessor(checkedException).process();
        boolean dtoGenerated = new DtoProcessor(
                isTest() ? dtoTestDirs : dtoDirs,
                defaultNullableInputModifier
        ).process();
        new TxProcessor(buddyIgnoreResourceGeneration).process();
        if (!immutableTypeElements.isEmpty() || errorGenerated || dtoGenerated) {
            prepareToolPhase(roundEnv);
            return true;
        }
        return false;
    }

    /**
     * 第二阶段只消费前一阶段已经稳定下来的类型快照，生成 tuple、export-doc 与 client 这类工具产物。
     */
    private void runToolPhase() {
        new TypedTupleProcessor().process();
        new ExportDocProcessor().process();
        new ClientProcessor(clientExplicitApi).process();
    }

    /**
     * tuple/client/export-doc 依赖上一阶段产出的新类型，这里把待消费类型名与全量类型快照固定下来，交给下一轮使用。
     */
    private void prepareToolPhase(RoundEnvironment roundEnv) {
        Context.INSTANCE.setDelayedTupleTypeNames(roundEnv
                .getElementsAnnotatedWith(TypedTuple.class)
                .stream()
                .filter(it -> it instanceof TypeElement)
                .map(it -> ((TypeElement) it).getQualifiedName().toString())
                .collect(Collectors.toSet()));
        Context.INSTANCE.snapshotAllTypeNames();
    }

    private static String defaultEntryTypeName(String configuredValue, String defaultValue) {
        if (configuredValue == null || configuredValue.isEmpty()) {
            return defaultValue;
        }
        return configuredValue;
    }

    private void resetSharedCompilerContext() {
        Settings.INSTANCE.fromOptions(AptLsiContext.INSTANCE.getOptions());
        Context.INSTANCE.reset(
                AptLsiContext.INSTANCE.getLsiResolver(),
                AptLsiContext.INSTANCE.getLsiFiler(),
                AptLsiContext.INSTANCE.getOptions(),
                () -> null,
                () -> {
                    try {
                        return findSourceAnchorFilePath();
                    } catch (IOException ex) {
                        throw AptLsiDiagnostics.generatorException("Cannot get the class output dir", ex);
                    }
                },
                this::findGeneratedJimmerResourceFile,
                message -> {
                    messager.printMessage(Diagnostic.Kind.NOTE, message);
                    return Unit.INSTANCE;
                },
                AptLsiClassDocMetadata::findAptDraftImplDocMap
        );
    }

    private void printLsiMetaException(site.addzero.lsi.diagnostic.MetaException ex) {
        Element element = findElement(ex.getAnchor());
        if (element != null) {
            messager.printMessage(Diagnostic.Kind.ERROR, ex.getMessage(), element);
        } else {
            messager.printMessage(Diagnostic.Kind.ERROR, ex.getMessage());
        }
    }

    private Element findElement(LsiDiagnosticAnchor anchor) {
        if (anchor == null) {
            return null;
        }
        String ownerQualifiedName = anchor.getOwnerQualifiedName();
        if (ownerQualifiedName == null || ownerQualifiedName.isEmpty()) {
            return null;
        }
        TypeElement ownerType = elements.getTypeElement(ownerQualifiedName);
        if (ownerType == null) {
            return null;
        }
        if (anchor.getKind() == LsiDiagnosticAnchor.Kind.CLASS || anchor.getSymbolName() == null) {
            return ownerType;
        }
        for (Element enclosedElement : ownerType.getEnclosedElements()) {
            if (anchor.getSymbolName().equals(enclosedElement.getSimpleName().toString()) &&
                    matchesAnchorKind(anchor.getKind(), enclosedElement)) {
                return enclosedElement;
            }
        }
        return ownerType;
    }

    private boolean matchesAnchorKind(LsiDiagnosticAnchor.Kind kind, Element element) {
        switch (kind) {
            case FIELD:
                return element.getKind() == ElementKind.FIELD ||
                        element.getKind() == ElementKind.ENUM_CONSTANT;
            case METHOD:
                return element.getKind() == ElementKind.METHOD;
            case PARAMETER:
                return element.getKind() == ElementKind.PARAMETER;
            case CLASS:
                return element instanceof TypeElement;
            default:
                return true;
        }
    }

    private boolean isTest() {
        try {
            String path = findSourceAnchorFilePath();
            return path.endsWith("/test/dummy.txt");
        } catch (IOException ex) {
            throw AptLsiDiagnostics.generatorException("Cannot get the class output dir", ex);
        }
    }

    private String findSourceAnchorFilePath() throws IOException {
        return AptLsiContext.INSTANCE.getLsiFiler().generatedResourcePath("dummy.txt");
    }

    private File findGeneratedJimmerResourceFile(String name) {
        try {
            File file = AptLsiContext.INSTANCE.getLsiFiler().generatedResourceFile("META-INF/jimmer/" + name);
            return file.exists() ? file : null;
        } catch (IOException ex) {
            return null;
        }
    }

    private static Collection<String> dtoDirs(
            ProcessingEnvironment env,
            String configurationName,
            String prefix,
            Collection<String> defaultDirs) {
        String dtoDirs = env.getOptions().get(configurationName);
        if (dtoDirs != null && !dtoDirs.isEmpty()) {
            Set<String> dirs = new LinkedHashSet<>();
            for (String path : dtoDirs.trim().split("\\*[,:;]\\s*")) {
                if (path.isEmpty() || path.equals("/")) {
                    continue;
                }
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                if (!path.isEmpty()) {
                    dirs.add(path);
                }
            }
            for (String dir : dirs) {
                if (!dir.startsWith(prefix)) {
                    throw AptLsiDiagnostics.generatorException(
                            "Illegal annotation processor configuration \"" +
                                    configurationName +
                                    "\", it contains an illegal path \"" +
                                    dir +
                                    "\" which does not start with \"" +
                                    prefix +
                                    "\"",
                            null
                    );
                }
            }
            return DtoUtils.standardDtoDirs(dirs);
        }
        return defaultDirs;
    }
}
