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
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTrees;
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

    private Path output;

    /**
     * The scanning mode; either {@code resolver} (Javadoc block tags) or {@code maven} (the {@code @Config}
     * annotation). Defaults to {@code resolver}.
     */
    private String mode = "resolver";

    private DocTrees docTrees;

    @Override
    public void init(Locale locale, Reporter reporter) {
        // no state to initialize
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
            e.printStackTrace(System.err);
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
                } else {
                    processResolverField(type, field, docComment, discoveredKeys);
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

        String configurationType = getConfigurationType(renderContent(blockTags.get("configurationType")));
        String defValue = resolveDefaultValue(type, field, docComment, blockTags.get("configurationDefaultValue"));

        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("key", String.valueOf(field.getConstantValue()));
        entry.put("defaultValue", nvl(defValue, ""));
        entry.put("fqName", type.getQualifiedName() + "." + field.getSimpleName());
        entry.put("description", cleanseJavadoc(renderContent(docComment.getFullBody())));
        entry.put("since", nvl(getSince(type, docComment), ""));
        entry.put("configurationSource", getConfigurationSource(renderContent(blockTags.get("configurationSource"))));
        entry.put("configurationType", configurationType);
        entry.put("supportRepoIdSuffix", toYesNo(renderContent(blockTags.get("configurationRepoIdSuffix"))));
        discovered.add(entry);
    }

    /**
     * Processes a constant field declared in Maven sources. Maven declares configuration keys via the
     * {@code org.apache.maven.api.annotations.Config} annotation (rather than the custom Javadoc block tags used by
     * Resolver), so the metadata is read from that annotation's attributes.
     */
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
            default:
                break;
        }

        if (configurationType.startsWith("java.lang.")) {
            configurationType = configurationType.substring("java.lang.".length());
        } else if (configurationType.startsWith("java.util.")) {
            configurationType = configurationType.substring("java.util.".length());
        }

        String description = docComment != null ? renderContent(docComment.getFullBody()) : "";
        if (description != null) {
            description = description.replace("*", "\\*");
        }

        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("key", String.valueOf(field.getConstantValue()));
        entry.put("defaultValue", nvl(defaultValue, ""));
        entry.put("fqName", "");
        entry.put("description", nvl(description, ""));
        entry.put("since", nvl(getSince(type, docComment), ""));
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
        return renderContent(content);
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
            return String.valueOf(field.getConstantValue());
        }
        // enum constants don't expose a constant value, fall back to the enum value's name
        if (field.getKind() == ElementKind.ENUM_CONSTANT) {
            return createQualifiedEnumValueConstant(field, field.getSimpleName().toString());
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
        return createQualifiedEnumValueConstant(field, enumConstant);
    }

    /**
     * Creates a qualified enum value constant for a field, e.g. {@code SomeEnum.VALUE}.
     */
    private String createQualifiedEnumValueConstant(VariableElement field, String enumConstant) {
        // prefix the constant with its (simple) enum type name, e.g. SomeEnum.VALUE
        String typeName = field.asType().toString();
        int lastDot = typeName.lastIndexOf('.');
        if (lastDot >= 0) {
            typeName = typeName.substring(lastDot + 1);
        }
        return typeName + "." + enumConstant;
    }

    private String renderContent(List<? extends DocTree> content) {
        if (content == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (DocTree tree : content) {
            sb.append(renderTree(tree));
        }
        return sb.toString().trim();
    }

    private String renderTree(DocTree tree) {
        switch (tree.getKind()) {
            case TEXT:
                return ((TextTree) tree).getBody();
            case LINK:
            case LINK_PLAIN:
                LinkTree link = (LinkTree) tree;
                String ref = link.getReference() != null ? link.getReference().getSignature() : "";
                return "{@link " + ref + "}";
            case CODE:
                return "{@code " + ((LiteralTree) tree).getBody().getBody() + "}";
            case LITERAL:
                return "{@literal " + ((LiteralTree) tree).getBody().getBody() + "}";
            default:
                return tree.toString();
        }
    }

    private String getSince(TypeElement type, DocCommentTree docComment) {
        String since = getSinceTag(docComment);
        if (since == null && type != null) {
            // fall back to the enclosing type's @since
            since = getSinceTag(docTrees.getDocCommentTree(type));
        }
        return since;
    }

    private String getSinceTag(DocCommentTree docComment) {
        if (docComment == null) {
            return null;
        }
        for (DocTree tag : docComment.getBlockTags()) {
            if (tag instanceof SinceTree) {
                return renderContent(((SinceTree) tag).getBody());
            }
        }
        return null;
    }

    private String getConfigurationType(String type) {
        if (type != null) {
            String linkPrefix = "{@link ";
            String linkSuffix = "}";
            if (type.startsWith(linkPrefix) && type.endsWith(linkSuffix)) {
                type = type.substring(linkPrefix.length(), type.length() - linkSuffix.length());
            }
            String javaLangPackage = "java.lang.";
            if (type.startsWith(javaLangPackage)) {
                type = type.substring(javaLangPackage.length());
            }
        }
        return nvl(type, "n/a");
    }

    private String getConfigurationSource(String source) {
        if ("{@link RepositorySystemSession#getConfigProperties()}".equals(source)) {
            return "Session Configuration";
        } else if ("{@link System#getProperty(String,String)}".equals(source)) {
            return "Java System Properties";
        } else {
            return source;
        }
    }

    private String cleanseJavadoc(String fullText) {
        String[] lines = fullText.split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : lines) {
            if (!line.startsWith("@") && !line.trim().isEmpty()) {
                result.append(line);
            }
        }
        return cleanseTags(result.toString());
    }

    private String cleanseTags(String text) {
        // {@code XXX} -> <code>XXX</code>
        // {@link XXX} -> <code>XXX</code>
        Pattern pattern = Pattern.compile("(\\{@\\w\\w\\w\\w (.+?)})");
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        int prevEnd = 0;
        StringBuilder result = new StringBuilder();
        do {
            result.append(text, prevEnd, matcher.start(1));
            result.append("<code>");
            result.append(matcher.group(2));
            result.append("</code>");
            prevEnd = matcher.end(1);
        } while (matcher.find());
        result.append(text, prevEnd, text.length());
        return result.toString();
    }

    private String toYesNo(String value) {
        return "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) ? "Yes" : "No";
    }

    private String nvl(String string, String def) {
        return string == null ? def : string;
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
