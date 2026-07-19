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
        List<Map<String, String>> discoveredKeys = new ArrayList<>();

        Set<TypeElement> types = ElementFilter.typesIn(environment.getIncludedElements());
        for (TypeElement type : types) {
            for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                if (field.getConstantValue() == null) {
                    continue;
                }
                DocCommentTree docComment = docTrees.getDocCommentTree(field);
                try {
                    if ("maven".equals(mode)) {
                        processMavenField(type, field, docComment, discoveredKeys);
                    } else if ("resolver".equals(mode)) {
                        processResolverField(type, field, docComment, discoveredKeys);
                    } else {
                        // TODO: move to beginning of run() and validate mode before processing any types
                        reportError("Unknown mode: " + mode);
                        return false;
                    }
                } catch (DocTreePathAwareRuntimeException e) {
                    reportError(e.getDocTreePath(), e.getMessage());
                } catch (RuntimeException e) {
                    DocTreePath rootPath = new DocTreePath(docTrees.getPath(field), docComment);
                    reportError(rootPath, e.getMessage());
                }
            }
        }

        try {
            writeProperties(discoveredKeys);
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

    private void processResolverField(
            TypeElement type, VariableElement field, DocCommentTree docComment, List<Map<String, String>> discovered) {
        if (docComment == null) {
            return;
        }
        Map<String, UnknownBlockTagTree> blockTags = collectBlockTags(docComment);
        if (!blockTags.containsKey("configurationSource")) {
            return;
        }

        String configurationType =
                getConfigurationType(extractClassLink(field, docComment, blockTags, "configurationType"));
        String defValue = resolveDefaultValue(type, field, docComment, blockTags.get("configurationDefaultValue"));

        if (defValue == null) {
            // Error was already reported, skip this field
            return;
        }

        UnknownBlockTagTree sourceTag = blockTags.get("configurationSource");
        UnknownBlockTagTree repoIdTag = blockTags.get("configurationRepoIdSuffix");

        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("key", String.valueOf(field.getConstantValue()));
        entry.put("defaultValue", nvl(defValue, ""));
        entry.put("fqName", type.getQualifiedName() + "." + field.getSimpleName());
        entry.put("description", cleanseJavadoc(renderContent(docComment.getFullBody())));
        entry.put("since", nvl(getSince(type, docComment), ""));
        entry.put(
                "configurationSource",
                getConfigurationSource(renderContent(sourceTag != null ? sourceTag.getContent() : null)));
        entry.put("configurationType", configurationType);
        entry.put("supportRepoIdSuffix", toYesNo(renderContent(repoIdTag != null ? repoIdTag.getContent() : null)));
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

        String description = docComment != null ? renderContent(docComment.getFullBody()) : "";
        description = description.replace("*", "\\*");

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

    private void writeProperties(List<Map<String, String>> discoveredKeys) throws IOException {
        Properties properties = new Properties();
        properties.setProperty("keys.count", String.valueOf(discoveredKeys.size()));
        for (int i = 0; i < discoveredKeys.size(); i++) {
            Map<String, String> entry = discoveredKeys.get(i);
            for (Map.Entry<String, String> field : entry.entrySet()) {
                properties.setProperty("keys." + i + "." + field.getKey(), field.getValue());
            }
        }
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        try (Writer writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            properties.store(writer, "Generated by ConfigurationCollectorDoclet - DO NOT EDIT");
        }
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

    /**
     * Builds a {@link DocTreePath} pointing to a specific block tag within a field's doc comment, enabling
     * precise Javadoc-level error messages via {@link Reporter#print(Diagnostic.Kind, DocTreePath, String)}.
     */
    private DocTreePath buildTagPath(VariableElement field, DocCommentTree docComment, UnknownBlockTagTree tag) {
        if (field == null || docComment == null || tag == null) {
            return null;
        }
        DocTreePath docCommentPath = new DocTreePath(docTrees.getPath(field), docComment);
        return new DocTreePath(docCommentPath, tag);
    }

    private String resolveDefaultValue(
            TypeElement type, VariableElement contextField, DocCommentTree docComment, UnknownBlockTagTree contentTag) {
        if (contentTag == null) {
            return null;
        }
        List<? extends DocTree> content = contentTag.getContent();
        if (content.isEmpty()) {
            return null;
        }
        DocTreePath tagPath = buildTagPath(contextField, docComment, contentTag);
        for (DocTree tree : content) {
            if (tree instanceof LinkTree link) {
                if (link.getReference() != null) {
                    String signature = link.getReference().getSignature();
                    // resolve the referenced constant using the fully qualified signature, so that references
                    // to constants declared in other types (e.g. {@link OtherType#CONSTANT}) can be resolved
                    VariableElement referenced = resolveReferencedField(contextField, docComment, link);
                    String value = referenced != null
                            ? lookupConstant(referenced)
                            : lookupConstant(type, signature.substring(signature.indexOf('#') + 1));
                    if (value == null) {
                        // hard fail: default value constants must be resolvable; report at the precise
                        // link-reference location if we can resolve a path to it, otherwise at the block tag
                        DocTreePath rootPath = new DocTreePath(docTrees.getPath(contextField), docComment);
                        DocTreePath linkRefPath = DocTreePath.getPath(rootPath, link.getReference());
                        throw new DocTreePathAwareRuntimeException(
                                linkRefPath != null ? linkRefPath : tagPath, "Could not resolve link: " + signature);
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
        return element instanceof VariableElement variableElement ? variableElement : null;
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
            VariableElement contextField,
            DocCommentTree docComment,
            Map<String, UnknownBlockTagTree> blockTags,
            String tagName) {
        UnknownBlockTagTree tag = blockTags.get(tagName);
        if (tag == null || tag.getContent().isEmpty()) {

            throw new IllegalArgumentException("Missing content for @" + tagName);
        }
        DocTreePath tagPath = buildTagPath(contextField, docComment, tag);
        for (DocTree tree : tag.getContent()) {
            // just use the first link, ignore any other content (e.g. text) in the tag
            if (tree instanceof LinkTree link) {
                String signature = link.getReference().getSignature();
                if (signature.contains("#")) {
                    // report at the precise link reference node within the block tag
                    DocTreePath rootPath = new DocTreePath(docTrees.getPath(contextField), docComment);
                    DocTreePath linkRefPath = DocTreePath.getPath(rootPath, link.getReference());
                    throw new DocTreePathAwareRuntimeException(
                            linkRefPath != null ? linkRefPath : tagPath,
                            "Expected a class link in @" + tagName + ", but got a member reference: " + signature);
                }
                return resolveReferencedType(contextField, docComment, link, signature);
            }
        }
        throw new DocTreePathAwareRuntimeException(tagPath, "No valid {@link ...} reference found in @" + tagName);
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
        return element instanceof TypeElement typeElement
                ? typeElement.getQualifiedName().toString()
                : signature;
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
            if (tag instanceof SinceTree sinceTree) {
                return renderContent(sinceTree.getBody());
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
