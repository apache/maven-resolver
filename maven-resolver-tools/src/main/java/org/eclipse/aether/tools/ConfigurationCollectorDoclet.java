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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.Diagnostic;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.StartElementTree;
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

    private Reporter reporter;

    private Path output;

    /**
     * The scanning mode; either {@code resolver} (Javadoc block tags) or {@code maven} (the {@code @Config}
     * annotation). Defaults to {@code resolver}.
     */
    private String mode = "resolver";

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
                new SimpleOption(
                        List.of("--output", "-o"),
                        1,
                        "The intermediate properties file to write discovered keys to",
                        "<file>",
                        args -> output = Paths.get(args.get(0))),
                new SimpleOption(
                        List.of("--mode", "-m"),
                        1,
                        "The scanning mode, either 'resolver' or 'maven'",
                        "<mode>",
                        args -> mode = args.get(0)));
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
            reporter.print(Diagnostic.Kind.ERROR, "Error running ConfigurationCollectorDoclet: " + e.getMessage());
            e.printStackTrace(reporter.getStandardWriter());
            return false;
        }
    }

    private boolean doRun(DocletEnvironment environment) {
        if (output == null) {
            throw new IllegalStateException("Missing required --output option");
        }
        docTrees = environment.getDocTrees();
        List<Map<String, String>> discoveredKeys = new ArrayList<>();

        Set<TypeElement> types = ElementFilter.typesIn(environment.getIncludedElements());
        for (TypeElement type : types) {
            for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                if (field.getConstantValue() == null) {
                    continue;
                }
                DocCommentTree docComment = docTrees.getDocCommentTree(field);
                if ("maven".equals(mode)) {
                    processMavenField(type, field, docComment, discoveredKeys);
                } else if ("resolver".equals(mode)) {
                    processResolverField(type, field, docComment, discoveredKeys);
                } else {
                    throw new IllegalArgumentException("Unknown mode: " + mode);
                }
            }
        }

        writeProperties(discoveredKeys);
        return true;
    }

    private void processResolverField(
            TypeElement type, VariableElement field, DocCommentTree docComment, List<Map<String, String>> discovered) {
        if (docComment == null) {
            return;
        }
        Map<String, List<? extends DocTree>> blockTags = collectBlockTags(docComment);
        if (!blockTags.containsKey("configurationSource")) {
            return;
        }

        String configurationType =
                getConfigurationType(extractClassLink(field, docComment, blockTags.get("configurationType")));
        String defValue = resolveDefaultValue(type, field, docComment, blockTags.get("configurationDefaultValue"));

        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("key", String.valueOf(field.getConstantValue()));
        entry.put("defaultValue", Objects.toString(defValue, ""));
        entry.put("fqName", type.getQualifiedName() + "." + field.getSimpleName());
        entry.put("description", renderContent(docComment.getFullBody(), field, docComment, true));
        entry.put("since", Objects.toString(getSince(type, docComment, field), ""));
        entry.put(
                "configurationSource",
                getConfigurationSource(renderContent(blockTags.get("configurationSource"), field, docComment, true)));
        entry.put("configurationType", configurationType);
        entry.put(
                "supportRepoIdSuffix",
                toYesNo(renderContent(blockTags.get("configurationRepoIdSuffix"), field, docComment, true)));
        discovered.add(entry);
    }

    /**
     * Processes a constant field declared in Maven sources. Maven declares configuration keys via the
     * {@code org.apache.maven.api.annotations.Config} annotation (rather than the custom Javadoc block tags used by
     * Resolver), so the metadata is read from that annotation's attributes.
     */
    // TODO: move to Maven repository module and use the Maven annotation type directly (currently we don't have a
    // dependency on Maven API)
    private void processMavenField(
            TypeElement type, VariableElement field, DocCommentTree docComment, List<Map<String, String>> discovered) {
        AnnotationMirror config = getAnnotation(field, MAVEN_CONFIG_ANNOTATION);
        if (config == null) {
            return;
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
                    source = value instanceof VariableElement
                            ? ((VariableElement) value).getSimpleName().toString()
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

        String description = docComment != null ? renderContent(docComment.getFullBody(), field, docComment, true) : "";
        description = description.replace("*", "\\*");

        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("key", String.valueOf(field.getConstantValue()));
        entry.put("defaultValue", Objects.toString(defaultValue, ""));
        entry.put("fqName", "");
        entry.put("description", Objects.toString(description, ""));
        entry.put("since", Objects.toString(getSince(type, docComment, field), ""));
        entry.put("configurationSource", source);
        entry.put("configurationType", configurationType);
        entry.put("supportRepoIdSuffix", "");
        discovered.add(entry);
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

    private void writeProperties(List<Map<String, String>> discoveredKeys) {
        Properties properties = new Properties();
        properties.setProperty("keys.count", String.valueOf(discoveredKeys.size()));
        for (int i = 0; i < discoveredKeys.size(); i++) {
            Map<String, String> entry = discoveredKeys.get(i);
            for (Map.Entry<String, String> field : entry.entrySet()) {
                properties.setProperty("keys." + i + "." + field.getKey(), field.getValue());
            }
        }
        try {
            if (output.getParent() != null) {
                Files.createDirectories(output.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
                properties.store(writer, "Generated by ConfigurationCollectorDoclet - DO NOT EDIT");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // --- Javadoc extraction helpers -------------------------------------------------------------------------------

    private Map<String, List<? extends DocTree>> collectBlockTags(DocCommentTree docComment) {
        Map<String, List<? extends DocTree>> result = new LinkedHashMap<>();
        for (DocTree tag : docComment.getBlockTags()) {
            if (tag instanceof UnknownBlockTagTree) {
                UnknownBlockTagTree unknown = (UnknownBlockTagTree) tag;
                result.put(unknown.getTagName(), unknown.getContent());
            }
        }
        return result;
    }

    private String resolveDefaultValue(
            TypeElement type,
            VariableElement contextField,
            DocCommentTree docComment,
            List<? extends DocTree> content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        for (DocTree tree : content) {
            if (tree instanceof LinkTree) {
                LinkTree link = (LinkTree) tree;
                if (link.getReference() != null) {
                    String signature = link.getReference().getSignature();
                    // resolve the referenced constant using the fully qualified signature, so that references
                    // to constants declared in other types (e.g. {@link OtherType#CONSTANT}) can be resolved
                    VariableElement referenced = resolveReferencedField(contextField, docComment, link);
                    String value = referenced != null
                            ? lookupConstant(referenced)
                            : lookupConstant(type, signature.substring(signature.indexOf('#') + 1));
                    if (value == null) {
                        // hard fail as in the original implementation: default value constants must be resolvable
                        throw new IllegalArgumentException("Could not look up {@link " + signature
                                + "} for configuration " + type.getQualifiedName());
                    }
                    return value;
                }
            }
        }
        return renderContent(content, contextField, docComment, true);
    }

    /**
     * Resolves the {@link VariableElement} a {@code {@link ...}} reference points to using the fully qualified
     * signature (so references into other types are supported). Returns {@code null} if the reference cannot be
     * resolved to a field.
     */
    private VariableElement resolveReferencedField(
            VariableElement contextField, DocCommentTree docComment, LinkTree link) {
        if (contextField == null || docComment == null) {
            return null;
        }
        DocTreePath rootPath = new DocTreePath(docTrees.getPath(contextField), docComment);
        DocTreePath refPath = DocTreePath.getPath(rootPath, link.getReference());
        if (refPath == null) {
            return null;
        }
        Element element = docTrees.getElement(refPath);
        return element instanceof VariableElement ? (VariableElement) element : null;
    }

    private String lookupConstant(TypeElement type, String constantName) {
        for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (field.getSimpleName().contentEquals(constantName)) {
                String value = lookupConstant(field);
                if (value != null) {
                    return value;
                }
            }
        }
        return null;
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

    private String extractClassLink(
            VariableElement contextField, DocCommentTree docComment, List<? extends DocTree> content) {
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("Missing content for @configurationDefaultValue");
        }
        for (DocTree tree : content) {
            // just use the first link, ignore any other content (e.g. text) in the tag
            if (tree instanceof LinkTree link) {
                String signature = link.getReference().getSignature();
                if (signature.contains("#")) {
                    throw new IllegalArgumentException(
                            "Expected a class link in @configurationDefaultValue, but got a member reference: "
                                    + signature);
                }
                return resolveReferencedType(contextField, docComment, link, signature);
            }
        }
        throw new IllegalArgumentException("No valid {@link ...} reference found in @configurationDefaultValue");
    }

    /**
     * Resolves the fully qualified class name a {@code {@link ...}} class reference points to (so that simple names
     * declared via imports are expanded). Falls back to the raw signature if the reference cannot be resolved to a
     * type.
     */
    private String resolveReferencedType(
            VariableElement contextField, DocCommentTree docComment, LinkTree link, String signature) {
        if (contextField == null || docComment == null) {
            return signature;
        }
        DocTreePath rootPath = new DocTreePath(docTrees.getPath(contextField), docComment);
        DocTreePath refPath = DocTreePath.getPath(rootPath, link.getReference());
        if (refPath == null) {
            return signature;
        }
        Element element = docTrees.getElement(refPath);
        return element instanceof TypeElement
                ? ((TypeElement) element).getQualifiedName().toString()
                : signature;
    }

    /**
     * Renders the content of a Javadoc tag into a string, escaping HTML special characters and rendering inline tags.
     * @param content
     * @param context
     * @param contextDoc
     * @param trim if true, trims the result string
     * @return
     * @see <a href="https://docs.oracle.com/en/java/javase/25/docs/specs/javadoc/doc-comment-spec.html#standard-tags">Javadoc tags</a>
     * @see <a href="https://docs.oracle.com/en/java/javase/25/docs/api/jdk.compiler/com/sun/source/doctree/InlineTagTree.html">InlineTagTree (common superinterface of all inline tags)</a>
     */
    private String renderContent(
            List<? extends DocTree> content, VariableElement context, DocCommentTree contextDoc, boolean trim) {
        if (content == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        SimpleDocTreeVisitor<String, Void> visitor = new SimpleDocTreeVisitor<String, Void>() {
            @Override
            public String visitText(TextTree node, Void p) {
                return escapeHtml(node.getBody());
            }

            @Override
            public String visitLink(LinkTree node, Void p) {
                String ref = node.getReference() != null ? node.getReference().getSignature() : "";
                String label = renderContent(node.getLabel(), null, null, false);
                String text = label == null || label.isEmpty() ? ref : label;
                return node.getKind() == DocTree.Kind.LINK_PLAIN ? escapeHtml(text) : renderAsCode(text);
            }

            @Override
            public String visitLiteral(LiteralTree node, Void p) {
                if (node.getKind() == DocTree.Kind.LITERAL) {
                    return renderAsCode(node.getBody().getBody());
                } else {
                    return escapeHtml(node.getBody().getBody());
                }
            }

            @Override
            public String visitSystemProperty(SystemPropertyTree node, Void p) {
                return renderAsCode(node.getPropertyName().toString());
            }

            private String renderAsCode(String text) {
                return "<code>" + escapeHtml(text) + "</code>";
            }

            @Override
            public String visitValue(ValueTree node, Void p) {
                if (node.getReference() != null && context != null && contextDoc != null) {
                    DocTreePath rootPath = new DocTreePath(docTrees.getPath(context), contextDoc);
                    DocTreePath refPath = DocTreePath.getPath(rootPath, node.getReference());
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
            public String visitStartElement(StartElementTree node, Void p) {
                StringBuilder sb = new StringBuilder("<");
                sb.append(node.getName());
                for (DocTree attr : node.getAttributes()) {
                    if (attr instanceof AttributeTree a) {
                        sb.append(" ").append(a.getName());
                        if (a.getValueKind() != AttributeTree.ValueKind.EMPTY) {
                            String quote = a.getValueKind() == AttributeTree.ValueKind.SINGLE ? "'" : "\"";
                            sb.append("=").append(quote);
                            sb.append(renderContent(a.getValue(), null, null, false));
                            sb.append(quote);
                        }
                    } else {
                        sb.append(attr.toString());
                    }
                }
                sb.append(node.isSelfClosing() ? "/>" : ">");
                return sb.toString();
            }

            @Override
            public String visitEndElement(EndElementTree node, Void p) {
                return "</" + node.getName() + ">";
            }

            @Override
            public String visitEntity(EntityTree node, Void p) {
                return "&" + node.getName() + ";";
            }

            @Override
            protected String defaultAction(DocTree node, Void p) {
                return node.toString();
            }
        };
        for (DocTree tree : content) {
            sb.append(tree.accept(visitor, null));
        }
        // normalized whitespace not relevant for HTML rendering
        if (trim) {
            String[] lines = sb.toString().split("\n");
            StringBuilder result = new StringBuilder();
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    result.append(line);
                }
            }
            return result.toString().trim();
        } else {
            return sb.toString();
        }
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String getSince(TypeElement type, DocCommentTree docComment, VariableElement fieldContext) {
        String since = getSinceTag(docComment, fieldContext);
        if (since == null && type != null) {
            // fall back to the enclosing type's @since
            since = getSinceTag(docTrees.getDocCommentTree(type), null);
        }
        return since;
    }

    private String getSinceTag(DocCommentTree docComment, VariableElement fieldContext) {
        if (docComment == null) {
            return null;
        }
        for (DocTree tag : docComment.getBlockTags()) {
            if (tag instanceof SinceTree sinceTree) {
                return renderContent(sinceTree.getBody(), fieldContext, docComment, true);
            }
        }
        return null;
    }

    private String getConfigurationType(String type) {
        if (type != null) {
            String javaLangPackage = "java.lang.";
            if (type.startsWith(javaLangPackage)) {
                type = type.substring(javaLangPackage.length());
            }
        }
        return Objects.toString(type, "n/a");
    }

    private String getConfigurationSource(String source) {
        if ("<code>RepositorySystemSession#getConfigProperties()</code>".equals(source)) {
            return "Session Configuration";
        } else if ("<code>System#getProperty(String,String)</code>".equals(source)) {
            return "Java System Properties";
        } else {
            return source;
        }
    }

    private String toYesNo(String value) {
        return "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) ? "Yes" : "No";
    }

    /**
     * Minimal {@link Option} implementation.
     */
    private static final class SimpleOption implements Option {
        private final List<String> names;
        private final int argumentCount;
        private final String description;
        private final String parameters;
        private final java.util.function.Consumer<List<String>> processor;

        SimpleOption(
                List<String> names,
                int argumentCount,
                String description,
                String parameters,
                java.util.function.Consumer<List<String>> processor) {
            this.names = names;
            this.argumentCount = argumentCount;
            this.description = description;
            this.parameters = parameters;
            this.processor = processor;
        }

        @Override
        public int getArgumentCount() {
            return argumentCount;
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
            processor.accept(arguments);
            return true;
        }
    }
}
