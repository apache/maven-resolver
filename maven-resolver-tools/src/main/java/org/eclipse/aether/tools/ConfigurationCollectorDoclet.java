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
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.util.ElementFilter;

import java.io.BufferedWriter;
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

    private Path output;
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
        return Set.of(new SimpleOption(
                List.of("--output", "-o"),
                1,
                "The intermediate properties file to write discovered keys to",
                "<file>",
                args -> output = Paths.get(args.get(0))));
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
                processField(type, field, docComment, discoveredKeys);
            }
        }

        writeProperties(discoveredKeys);
        return true;
    }

    private void processField(
            TypeElement type, VariableElement field, DocCommentTree docComment, List<Map<String, String>> discovered) {
        if (docComment == null) {
            return;
        }
        Map<String, List<? extends DocTree>> blockTags = collectBlockTags(docComment);
        if (!blockTags.containsKey("configurationSource")) {
            return;
        }

        String configurationType = getConfigurationType(renderContent(blockTags.get("configurationType")));
        String defValue = resolveDefaultValue(type, blockTags.get("configurationDefaultValue"));

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
            try (Writer writer = new BufferedWriter(Files.newBufferedWriter(output, StandardCharsets.UTF_8))) {
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

    private String resolveDefaultValue(TypeElement type, List<? extends DocTree> content) {
        if (content == null || content.isEmpty()) {
            return null;
        }
        for (DocTree tree : content) {
            if (tree instanceof LinkTree) {
                LinkTree link = (LinkTree) tree;
                if (link.getReference() != null) {
                    String signature = link.getReference().getSignature();
                    String constantName = signature.substring(signature.indexOf('#') + 1);
                    String value = lookupConstant(type, constantName);
                    if (value == null) {
                        // hard fail as in the original implementation: default value constants must be resolvable
                        throw new IllegalArgumentException("Could not look up {@link #" + constantName
                                + "} for configuration " + type.getQualifiedName());
                    }
                    return value;
                }
            }
        }
        return renderContent(content);
    }

    private String lookupConstant(TypeElement type, String constantName) {
        for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
            if (field.getSimpleName().contentEquals(constantName) && field.getConstantValue() != null) {
                return String.valueOf(field.getConstantValue());
            }
        }
        return null;
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
