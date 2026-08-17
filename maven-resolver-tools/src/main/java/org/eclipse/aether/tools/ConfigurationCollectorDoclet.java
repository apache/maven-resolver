/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.tools;

import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.SimpleElementVisitor14;
import javax.lang.model.util.SimpleTypeVisitor14;
import javax.tools.Diagnostic;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.SystemPropertyTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.ValueTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTrees;
import com.sun.source.util.SimpleDocTreeVisitor;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

/**
 * A custom Javadoc {@link Doclet} that scans constant fields for configuration metadata declared via custom Javadoc
 * block tags (e.g. {@code @configurationSource}) and writes the discovered keys into an intermediate
 * {@link Properties} file. That file is subsequently consumed by {@link CollectConfiguration} to render the
 * documentation via Velocity templates.
 * <p>
 * The intermediate file uses an indexed layout:
 * <pre>
 * keys.count=N
 * keys.0.key=...
 * keys.0.description=...
 * ...
 * </pre>
 */
public class ConfigurationCollectorDoclet implements Doclet {

    /**
     * Fully qualified name of the Maven annotation that marks a configuration key when scanning Maven sources.
     */
    private static final String MAVEN_CONFIG_ANNOTATION = "org.apache.maven.api.annotations.Config";

    private static final MethodReference METHOD_REFERENCE_SESSION_CONFIGURATION =
            new MethodReference("org.eclipse.aether.RepositorySystemSession", "getConfigProperties", List.of());
    private static final MethodReference METHOD_REFERENCE_SYSTEM_PROPERTY =
            new MethodReference("java.lang.System", "getProperty", List.of("java.lang.String", "java.lang.String"));

    private Reporter reporter;

    private Path output;

    private enum Mode {
        RESOLVER,
        MAVEN
    }

    private record ConfigurationEntry(
            String key,
            String description,
            String defaultValue,
            String fqName,
            String since,
            String source,
            String type,
            boolean supportsRepoIdSuffix) {

        public ConfigurationEntry {
            Objects.requireNonNull(key);
            Objects.requireNonNull(description);
        }
    }

    /**
     * The scanning mode; either {@code resolver} (Javadoc block tags) or {@code maven} (the {@code @Config}
     * annotation). Defaults to {@code resolver}.
     */
    private Mode mode = Mode.RESOLVER;

    private DocTrees docTrees;

    @Override
    public void init(Locale locale, Reporter reporter) {
        this.reporter = reporter;
    }

    @Override
    public String getName() {
        return "ConfigurationCollector";
    }

    @Override
    public Set<? extends Option> getSupportedOptions() {
        return Set.of(
                new SingleArgumentOption(
                        List.of("--output", "-o"),
                        "The intermediate properties file to write discovered keys to",
                        "<file>",
                        arg -> {
                            try {
                                output = Paths.get(arg);
                            } catch (InvalidPathException e) {
                                throw new IllegalArgumentException("Invalid output file path: " + arg, e);
                            }
                        }),
                new SingleArgumentOption(
                        List.of("--mode", "-m"), "The scanning mode, either 'resolver' or 'maven'", "<mode>", arg -> {
                            try {
                                mode = Mode.valueOf(arg.toUpperCase(Locale.ROOT));
                            } catch (IllegalArgumentException e) {
                                throw new IllegalArgumentException(
                                        "Invalid mode: " + arg + ". Must be one of (case-insensitive): "
                                                + String.join(
                                                        ", ",
                                                        Arrays.stream(Mode.values())
                                                                .map(Enum::name)
                                                                .toArray(String[]::new)));
                            }
                        }));
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean run(DocletEnvironment environment) {
        try {
            return doRun(environment);
        } catch (RuntimeException e) {
            reportError("Error running ConfigurationCollectorDoclet: " + e.getMessage());
            return false;
        }
    }

    private boolean doRun(DocletEnvironment environment) {
        if (output == null) {
            reportError("Missing required --output option");
            return false;
        }
        docTrees = environment.getDocTrees();
        List<ConfigurationEntry> configurationEntries = new ArrayList<>();

        Set<TypeElement> types = ElementFilter.typesIn(environment.getIncludedElements());
        for (TypeElement type : types) {
            for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                // check if relevant metadata is present before processing the field, so that we can skip any fields
                // that don't have a constant value or Javadoc
                if (field.getConstantValue() == null) {
                    continue;
                }
                DocCommentTree docComment = docTrees.getDocCommentTree(field);
                if (docComment == null) {
                    // javadoc is mandatory for configuration keys, so skip any fields that don't have a doc comment
                    continue;
                }
                DocTreePath rootPath = new DocTreePath(docTrees.getPath(field), docComment);
                try {
                    ConfigurationEntry entry;
                    switch (mode) {
                        case MAVEN:
                            entry = processMavenField(rootPath, field);
                            break;
                        case RESOLVER:
                            entry = processResolverField(rootPath, field);
                            break;
                        default:
                            throw new IllegalStateException("Unknown mode: " + mode);
                    }
                    if (entry != null) {
                        configurationEntries.add(entry);
                    }
                } catch (DocTreePathAwareRuntimeException e) {
                    reportError(e.getDocTreePath(), e.getMessage());
                } catch (IllegalArgumentException e) {
                    reportError(rootPath, e.getMessage());
                } catch (RuntimeException e) {
                    // log with stacktrace for unexpected errors, but continue
                    reportError(rootPath, e);
                }
            }
        }

        try {
            writeProperties(configurationEntries);
        } catch (IOException e) {
            reportError("Failed to write properties file: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Reports an error message at a specific DocTreePath location.
     *
     * @param path the DocTreePath where the error occurred
     * @param message the error message
     */
    private void reportError(DocTreePath path, Throwable throwable) {
        reportError(path, throwable.getMessage());
        // also emit stack trace
        PrintWriter pw = reporter.getDiagnosticWriter();
        if (pw == null) {
            pw = new PrintWriter(System.err);
        }
        throwable.printStackTrace(pw);
    }

    /**
     * Reports an error message at a specific DocTreePath location.
     *
     * @param path the DocTreePath where the error occurred
     * @param message the error message
     */
    private void reportError(DocTreePath path, String message) {
        if (path != null) {
            reporter.print(Diagnostic.Kind.ERROR, path, message);
        } else {
            reportError(message);
        }
    }

    /**
     * Reports a global error message without location information.
     *
     * @param message the error message
     */
    private void reportError(String message) {
        reporter.print(Diagnostic.Kind.ERROR, message);
    }

    /**
     * Processes a configuration key field declared in Javadoc sources.
     * @param path
     * @param field
     * @return the extracted configuration entry (or {@code null})
     */
    private ConfigurationEntry processResolverField(DocTreePath path, VariableElement field) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(field);
        Map<String, UnknownBlockTagTree> blockTags = collectBlockTags(path.getDocComment());
        if (!blockTags.containsKey("configurationSource")) {
            return null;
        }
        return new ConfigurationEntry(
                String.valueOf(field.getConstantValue()),
                getFullBodyContent(path),
                resolveDefaultValue(path, blockTags).orElse(""),
                getFullyQualifiedName(field),
                getSince(path).orElse(""),
                getConfigurationSource(path, blockTags).orElse(""),
                getConfigurationType(path, blockTags),
                isSupportsRepoIdSuffix(path, blockTags));
    }

    private boolean isSupportsRepoIdSuffix(DocTreePath path, Map<String, UnknownBlockTagTree> blockTags) {
        UnknownBlockTagTree repoIdTag = blockTags.get("configurationRepoIdSuffix");
        if (repoIdTag != null) {
            String content = renderContent(DocTreePath.getPath(path, repoIdTag), RenderMode.PLAIN, true);
            return "yes".equalsIgnoreCase(content) || "true".equalsIgnoreCase(content);
        }
        return false;
    }

    /**
     * Processes a constant field declared in Maven sources. Maven declares configuration keys via the
     * {@code org.apache.maven.api.annotations.Config} annotation (rather than the custom Javadoc block tags used by
     * Resolver), so the metadata is read from that annotation's attributes.
     * @return the extracted configuration entry (or {@code null} if the field is not annotated with {@code @Config})
     */
    // TODO: move to Maven repository module and use the Maven annotation type directly (currently we don't have a
    // dependency on Maven API)
    private ConfigurationEntry processMavenField(DocTreePath path, VariableElement field) {
        AnnotationMirror config = getAnnotation(field, MAVEN_CONFIG_ANNOTATION);
        if (config == null) {
            return null;
        }

        String source = "USER_PROPERTIES";
        String defaultValue = "";
        String configurationType = "java.lang.String";
        for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> attribute :
                config.getElementValues().entrySet()) {
            String name = attribute.getKey().getSimpleName().toString();
            Object value = attribute.getValue().getValue();
            switch (name) {
                case "source":
                    source = value instanceof VariableElement variableElement
                            ? variableElement.getSimpleName().toString()
                            : String.valueOf(value);
                    break;
                case "defaultValue":
                    defaultValue = String.valueOf(value);
                    break;
                case "type":
                    configurationType = String.valueOf(value);
                    break;
                default:
                    break;
            }
        }

        source = source.toLowerCase(Locale.ROOT);
        switch (source) {
            case "model":
                source = "Model properties";
                break;
            case "user_properties":
                source = "User properties";
                break;
            case "system_properties":
                source = "System properties";
                break;
            default:
                break;
        }

        if (configurationType.startsWith("java.lang.")) {
            configurationType = configurationType.substring("java.lang.".length());
        } else if (configurationType.startsWith("java.util.")) {
            configurationType = configurationType.substring("java.util.".length());
        }

        return new ConfigurationEntry(
                String.valueOf(field.getConstantValue()),
                path.getDocComment() != null ? getFullBodyContent(path) : "",
                Objects.toString(defaultValue, ""),
                getFullyQualifiedName(field),
                getSince(path).orElse(""),
                source,
                configurationType,
                false);
    }

    private AnnotationMirror getAnnotation(Element element, String fqName) {
        for (AnnotationMirror annotation : element.getAnnotationMirrors()) {
            Element annotationElement = annotation.getAnnotationType().asElement();
            if (annotationElement instanceof TypeElement
                    && ((TypeElement) annotationElement).getQualifiedName().contentEquals(fqName)) {
                return annotation;
            }
        }
        return null;
    }

    private void writeProperties(List<ConfigurationEntry> configurationEntries) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("keys.count", String.valueOf(configurationEntries.size()));
        for (int i = 0; i < configurationEntries.size(); i++) {
            ConfigurationEntry entry = configurationEntries.get(i);
            writeEntry(properties, entry, "keys." + i + ".");
        }
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            properties.store(writer, "Generated by ConfigurationCollectorDoclet - DO NOT EDIT");
        }
    }

    private void writeEntry(Properties properties, ConfigurationEntry entry, String prefix) {
        properties.setProperty(prefix + "key", entry.key());
        properties.setProperty(prefix + "defaultValue", entry.defaultValue());
        properties.setProperty(prefix + "fqName", entry.fqName());
        properties.setProperty(prefix + "description", entry.description());
        properties.setProperty(prefix + "since", entry.since());
        properties.setProperty(prefix + "configurationSource", entry.source());
        properties.setProperty(prefix + "configurationType", entry.type());
        properties.setProperty(prefix + "supportRepoIdSuffix", toYesNo(entry.supportsRepoIdSuffix()));
    }

    // --- Javadoc extraction helpers -------------------------------------------------------------------------------

    private Map<String, UnknownBlockTagTree> collectBlockTags(DocCommentTree docComment) {
        Map<String, UnknownBlockTagTree> result = new LinkedHashMap<>();
        for (DocTree tag : docComment.getBlockTags()) {
            if (tag instanceof UnknownBlockTagTree unknownBlockTree) {
                result.put(unknownBlockTree.getTagName(), unknownBlockTree);
            }
        }
        return result;
    }

    private String getFullBodyContent(DocTreePath path) {
        return renderContent(path, RenderMode.HTML, true, path.getDocComment().getFullBody());
    }

    private Optional<String> resolveDefaultValue(DocTreePath path, Map<String, UnknownBlockTagTree> blockTags) {
        UnknownBlockTagTree defaultValueTag = blockTags.get("configurationDefaultValue");
        if (defaultValueTag == null) {
            return Optional.empty();
        }
        DocTreePath defaultValuePath = DocTreePath.getPath(path, defaultValueTag);
        for (DocTree tree : defaultValueTag.getContent()) {
            if (tree instanceof LinkTree link) {
                String signature = link.getReference().getSignature();
                DocTreePath linkTreePath = DocTreePath.getPath(path, tree);
                // resolve the referenced constant using the fully qualified signature, so that references
                // to constants declared in other types (e.g. {@link OtherType#CONSTANT}) can be resolved
                VariableElement referenced = resolveReferencedField(linkTreePath, link);
                String value = referenced != null ? lookupConstant(referenced) : null;
                if (value == null) {
                    // hard fail: default value constants must be resolvable; report at the precise
                    // link-reference location if we can resolve a path to it, otherwise at the block tag
                    DocTreePath linkRefPath = DocTreePath.getPath(linkTreePath, link.getReference());
                    throw new DocTreePathAwareRuntimeException(
                            linkRefPath != null ? linkRefPath : linkTreePath,
                            "Could not resolve link to determine default value: " + signature);
                }
                return Optional.ofNullable(value);
            }
        }
        // fallback: render the content of the block tag as-is (e.g. if it contains a literal value rather than a {@code
        // {@link ...}} reference)
        return Optional.of(renderContent(defaultValuePath, RenderMode.PLAIN, true));
    }

    /**
     * Resolves the {@link VariableElement} a {@code {@link ...}} reference points to using the fully qualified
     * signature (so references into other types are supported). Returns {@code null} if the reference cannot be
     * resolved to a field.
     */
    private VariableElement resolveReferencedField(DocTreePath path, LinkTree link) {
        DocTreePath refPath = DocTreePath.getPath(path, link.getReference());
        if (refPath == null) {
            return null;
        }
        Element element = docTrees.getElement(refPath);
        return element instanceof VariableElement variableElement ? variableElement : null;
    }

    private String lookupConstant(VariableElement field) {
        if (field.getConstantValue() != null) {
            Object value = field.getConstantValue();
            if (value instanceof String) {
                return "\"" + value + "\"";
            } else {
                return String.valueOf(field.getConstantValue());
            }
        }
        // enum constants don't expose a constant value, fall back to the enum value's name
        if (field.getKind() == ElementKind.ENUM_CONSTANT) {
            return field.getSimpleName().toString();
        }
        // the field may indirectly reference an enum variable, e.g. "SomeEnum.VALUE";
        // resolve it from the field's initializer
        return resolveEnumReference(field);
    }

    /**
     * Resolves an enum constant that a field is initialized with, including the enum type in the result
     * (e.g. a field declared as {@code SomeEnum FOO = SomeEnum.VALUE} resolves to {@code SomeEnum.VALUE}).
     * Returns {@code null} if the field's initializer is not a simple enum reference.
     */
    private String resolveEnumReference(VariableElement field) {
        if (!(docTrees.getTree(field) instanceof VariableTree variableTree)) {
            return null;
        }
        ExpressionTree initializer = variableTree.getInitializer();
        String enumConstant = null;
        if (initializer instanceof MemberSelectTree memberSelectTree) {
            // e.g. SomeEnum.VALUE -> VALUE
            enumConstant = memberSelectTree.getIdentifier().toString();
        } else if (initializer instanceof IdentifierTree identifierTree) {
            // e.g. statically imported VALUE -> VALUE
            enumConstant = identifierTree.getName().toString();
        }
        if (enumConstant == null) {
            return null;
        }
        return enumConstant;
    }

    private Optional<LinkTree> getFirstLinkInBlockTag(UnknownBlockTagTree tag) {
        for (DocTree tree : tag.getContent()) {
            if (tree instanceof LinkTree link) {
                return Optional.of(link);
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves the fully qualified type name a {@code {@link ...}} reference points to.
     * @param path the path of the given inline link tag
     * @param link the inline link tag
     * @return
     */
    private String getType(DocTreePath path, LinkTree link) {
        String signature = link.getReference().getSignature();
        if (signature.contains("#")) {
            // report at the precise link reference node within the block tag
            DocTreePath linkRefPath = DocTreePath.getPath(path, link.getReference());
            throw new DocTreePathAwareRuntimeException(
                    linkRefPath != null ? linkRefPath : path,
                    "Expected a class link, but got a member reference: " + signature);
        }
        // resolve the referenced type and return its fully qualified name, falling back to the raw signature if it
        // cannot be resolved
        return resolveReferencedType(path, link.getReference())
                .map(t -> t.getQualifiedName().toString())
                .orElse(signature);
    }

    /**
     * Resolves the fully qualified class name a {@code {@link ...}} class reference points to (so that simple names
     * declared via imports are expanded). Falls back to the raw signature if the reference cannot be resolved to a
     * type.
     */
    private Optional<TypeElement> resolveReferencedType(DocTreePath path, ReferenceTree reference) {
        // TODO: try to resolve from type outside the current compilation unit (e.g. from imports)
        DocTreePath refPath = DocTreePath.getPath(path, reference);
        if (refPath == null) {
            return Optional.empty();
        }
        Element element = docTrees.getElement(refPath);
        return element instanceof TypeElement typeElement ? Optional.of(typeElement) : Optional.empty();
    }

    enum RenderMode {
        /** Render the content as plain text. Stripping any rich text markup */
        PLAIN,
        /** Render the content as HTML, escaping special characters and rendering inline tags. */
        HTML
    }

    private String renderContent(DocTreePath docTreePath, RenderMode mode, boolean trim) {
        return renderContent(docTreePath, mode, trim, null);
    }

    /**
     * Renders the content of a Javadoc tag into an HTML string, escaping HTML special characters and rendering inline tags.
     *
     * @param docTreePath encapsulates the doc comment tree and the path to the content being rendered.
     * The latter is used for resolving {@code {@link ...}} references and emitting error messages.
     * @param trim if true, trims the result string (may destroy {@code <pre> </pre>} formatting).
     * @param docTrees the doc trees for which to render the content. If {@code null}, the leaf of the {@code docTreePath} is rendered.
     * @return the rendered content (never {@code null})
     * @see <a href="https://docs.oracle.com/en/java/javase/25/docs/specs/javadoc/doc-comment-spec.html#standard-tags">Javadoc tags</a>
     * @see <a href="https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/InlineTagTree.html">InlineTagTree (common superinterface of all inline tags)</a>
     */
    private String renderContent(
            DocTreePath docTreePath, RenderMode mode, boolean trim, Collection<? extends DocTree> docTreesToRender) {
        Objects.requireNonNull(docTreePath, "docTreePath must not be null");
        StringBuilder sb = new StringBuilder();
        SimpleDocTreeVisitor<String, Void> visitor = new SimpleDocTreeVisitor<String, Void>() {
            @Override
            public String visitText(TextTree node, Void p) {
                return escape(mode, node.getBody());
            }

            @Override
            public String visitLink(LinkTree node, Void p) {
                String ref = node.getReference() != null ? node.getReference().getSignature() : "";
                String label = renderContent(DocTreePath.getPath(docTreePath, node.getReference()), mode, false);
                String text = label == null || label.isEmpty() ? ref : label;
                return node.getKind() == DocTree.Kind.LINK_PLAIN ? escape(mode, text) : renderAsCode(text);
            }

            @Override
            public String visitLiteral(LiteralTree node, Void p) {
                if (node.getKind() == DocTree.Kind.CODE) {
                    return renderAsCode(node.getBody().getBody());
                } else {
                    return escape(mode, node.getBody().getBody());
                }
            }

            @Override
            public String visitSystemProperty(SystemPropertyTree node, Void p) {
                return renderAsCode(node.getPropertyName().toString());
            }

            private String renderAsCode(String text) {
                if (mode == RenderMode.HTML) {
                    return "<code>" + escape(mode, text) + "</code>";
                } else {
                    return escape(mode, text);
                }
            }

            @Override
            public String visitValue(ValueTree node, Void p) {
                if (node.getReference() != null) {
                    DocTreePath refPath = DocTreePath.getPath(docTreePath, node.getReference());
                    if (refPath != null) {
                        Element element = docTrees.getElement(refPath);
                        if (element instanceof VariableElement ve) {
                            String value = lookupConstant(ve);
                            if (value != null) {
                                return renderAsCode(value);
                            }
                        }
                    }
                }
                // fall back to showing the reference signature
                String ref = node.getReference() != null ? node.getReference().getSignature() : "";
                return renderAsCode(ref);
            }

            @Override
            public String visitEntity(EntityTree node, Void p) {
                return "&" + node.getName() + ";";
            }

            @Override
            public String visitUnknownBlockTag(UnknownBlockTagTree node, Void p) {
                StringBuilder sb = new StringBuilder();
                node.getContent().forEach(child -> sb.append(child.accept(this, p)));
                return sb.toString();
            }

            @Override
            public String visitSince(SinceTree node, Void p) {
                return escape(mode, node.getBody().toString());
            }

            @Override
            protected String defaultAction(DocTree node, Void p) {
                // the default action internally calls node.toString(), which uses
                // com.sun.tools.javac.tree.DCTree.toString() which relies on com.sun.tools.javac.tree.DocPretty to
                // render the node
                return node.toString();
            }
        };
        if (docTreesToRender == null) {
            docTreesToRender = Collections.singleton(docTreePath.getLeaf());
        }
        for (DocTree docTreeToRender : docTreesToRender) {
            sb.append(docTreeToRender.accept(visitor, null));
        }

        if (trim) {
            // normalize whitespace not relevant for HTML rendering,
            // trimming behaviour already differs between different Javadoc
            // versions (Java > 21 trims leading whitespace per line)
            return sb.toString().trim().replaceAll("\\s+", " ");
        } else {
            return sb.toString();
        }
    }

    private static String escape(RenderMode mode, String text) {
        if (mode == RenderMode.HTML) {
            return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        } else {
            return text;
        }
    }

    private Optional<String> getSince(DocTreePath path) {
        String since = getSinceTag(path);
        if (since == null && path.getTreePath().getParentPath() != null) {
            // get the @since tag from the enclosing element (e.g. the enclosing class or package)
            return getSince(docTrees.getElement(path.getTreePath().getParentPath()));
        }
        return Optional.ofNullable(since);
    }

    private Optional<String> getSince(Element element) {
        if (element == null) {
            return Optional.empty();
        }
        DocCommentTree docComment = docTrees.getDocCommentTree(element);
        if (docComment != null) {
            DocTreePath path = new DocTreePath(docTrees.getPath(element), docComment);
            Optional<String> since = getSince(path);
            if (since.isPresent()) {
                return since;
            }
        }
        // traverse up the enclosing elements to find a @since tag in the closest enclosing type or package
        return getSince(element.getEnclosingElement());
    }

    private String getSinceTag(DocTreePath path) {
        if (path == null) {
            // may be non existent
            return null;
        }
        for (DocTree tag : path.getDocComment().getBlockTags()) {
            if (tag instanceof SinceTree) {
                return renderContent(DocTreePath.getPath(path, tag), RenderMode.PLAIN, true);
            }
        }
        return null;
    }

    private String getConfigurationType(DocTreePath path, Map<String, UnknownBlockTagTree> blockTags) {
        UnknownBlockTagTree typeTag = blockTags.get("configurationType");
        if (typeTag == null) {
            throw new IllegalStateException("Missing block tag @configurationType");
        }
        DocTreePath configurationTypePath = DocTreePath.getPath(path, typeTag);
        LinkTree linkTree = getFirstLinkInBlockTag(typeTag)
                .orElseThrow(() -> new DocTreePathAwareRuntimeException(
                        configurationTypePath, "No valid {@link ...} reference found in @" + typeTag.getTagName()));

        String type = getType(configurationTypePath, linkTree);
        String javaLangPackage = "java.lang.";
        if (type.startsWith(javaLangPackage)) {
            type = type.substring(javaLangPackage.length());
        }
        return type;
    }

    private Optional<String> getConfigurationSource(DocTreePath path, Map<String, UnknownBlockTagTree> blockTags) {
        UnknownBlockTagTree configurationSourceTag = blockTags.get("configurationSource");
        if (configurationSourceTag == null) {
            return Optional.empty();
        }
        DocTreePath configurationSourcePath = DocTreePath.getPath(path, configurationSourceTag);
        LinkTree linkTree = getFirstLinkInBlockTag(configurationSourceTag)
                .orElseThrow(() -> new DocTreePathAwareRuntimeException(
                        configurationSourcePath,
                        "No valid {@link ...} reference found in @" + configurationSourceTag.getTagName()));

        // javadoc signature is not normalized, use the resolved reference (leveraging ReferenceParser) to get a unique
        // canonical representation of the referenced method
        MethodReference methodReference = getReferencedMethod(configurationSourcePath, linkTree);
        if (methodReference.equals(METHOD_REFERENCE_SESSION_CONFIGURATION)) {
            return Optional.of("Session Configuration");
        } else if (methodReference.equals(METHOD_REFERENCE_SYSTEM_PROPERTY)) {
            return Optional.of("Java System Properties");
        } else {
            reporter.print(
                    Diagnostic.Kind.WARNING,
                    path,
                    "Unknown configuration source: " + linkTree.getReference().getSignature()
                            + ", using raw signature as source");
            return Optional.of(linkTree.getReference().getSignature());
        }
    }

    /**
     * Represents a reference to a method, including the fully qualified class name, method name, and parameter types.
     * This is supposed to be unique as well as canonical.
     * The signature within a Javadoc link is not normalized (e.g. may contain spaces or not, may contain argument names or not)
     * so we need to resolve the reference to get a unique representation of the method.
     * @param fullyQualifiedClassName the fully qualified name of the class containing the method
     * @param methodName the name of the method
     * @param fullyQualifiedParameterTypes a list of fully qualified names (for declared types) or simple names (for primitive types) of the parameter types of the method
     */
    protected record MethodReference(
            String fullyQualifiedClassName, String methodName, List<String> fullyQualifiedParameterTypes) {}

    private MethodReference getReferencedMethod(DocTreePath path, LinkTree link) {
        ExecutableElement ee = getReferencedExecutableElement(path, link);
        String fullyQualifiedClassName =
                ((TypeElement) ee.getEnclosingElement()).getQualifiedName().toString();
        String methodName = ee.getSimpleName().toString();
        List<String> parameterTypes = ee.getParameters().stream()
                .map(p -> getFullyQualifiedName(p.asType()))
                .toList();
        return new MethodReference(fullyQualifiedClassName, methodName, parameterTypes);
    }

    static String getFullyQualifiedName(Element e) {
        return new SimpleElementVisitor14<String, Void>() {
            @Override
            public String visitModule(ModuleElement e, Void p) {
                return e.getQualifiedName().toString();
            }

            @Override
            public String visitPackage(PackageElement e, Void p) {
                return e.getQualifiedName().toString();
            }

            @Override
            public String visitType(TypeElement e, Void p) {
                return e.getQualifiedName().toString();
            }

            @Override
            protected String defaultAction(Element e, Void p) {
                return visit(e.getEnclosingElement()) + "." + e.getSimpleName();
            }
        }.visit(e);
    }

    static String getFullyQualifiedName(TypeMirror e) {
        return new SimpleTypeVisitor14<String, Void>() {
            @Override
            public String visitDeclared(DeclaredType t, Void p) {
                Element e = t.asElement();
                if (e instanceof TypeElement typeElement) {
                    return typeElement.getQualifiedName().toString();
                }
                return super.visitDeclared(t, p);
            }

            @Override
            public String visitPrimitive(PrimitiveType t, Void p) {
                return t.toString();
            }

            @Override
            protected String defaultAction(TypeMirror e, Void p) {
                return e.toString();
            }
        }.visit(e);
    }

    private ExecutableElement getReferencedExecutableElement(DocTreePath path, LinkTree link) {
        DocTreePath linkRefPath = DocTreePath.getPath(path, link.getReference());
        if (linkRefPath == null) {
            throw new DocTreePathAwareRuntimeException(
                    path,
                    "Could not resolve link reference: " + link.getReference().getSignature());
        }
        Element element = docTrees.getElement(linkRefPath);
        if (element instanceof ExecutableElement ee) {
            return ee;
        } else {
            throw new DocTreePathAwareRuntimeException(
                    linkRefPath, "Expected an executable element, but got: " + element);
        }
    }

    private static String toYesNo(boolean value) {
        return value ? "Yes" : "No";
    }

    /**
     * Minimal {@link Option} implementation.
     */
    private static final class SingleArgumentOption implements Option {
        private final List<String> names;
        private final String description;
        private final String parameters;
        private final java.util.function.Consumer<String> processor;

        SingleArgumentOption(
                List<String> names,
                String description,
                String parameters,
                java.util.function.Consumer<String> processor) {
            this.names = names;
            this.description = description;
            this.parameters = parameters;
            this.processor = processor;
        }

        @Override
        public int getArgumentCount() {
            return 1;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public Kind getKind() {
            return Kind.STANDARD;
        }

        @Override
        public List<String> getNames() {
            return names;
        }

        @Override
        public String getParameters() {
            return parameters;
        }

        @Override
        public boolean process(String option, List<String> arguments) {
            processor.accept(arguments.get(0));
            // returning false just leads to a very generic error message (not even exposing the affected option) so
            // rather rely on custom runtime exceptions for validation errors
            return true;
        }
    }
}
