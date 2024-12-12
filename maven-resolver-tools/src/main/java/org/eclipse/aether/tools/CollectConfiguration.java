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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.codehaus.plexus.util.io.CachingWriter;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.AST;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.ASTNode;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.Javadoc;
import org.jboss.forge.roaster.model.JavaDoc;
import org.jboss.forge.roaster.model.JavaDocCapable;
import org.jboss.forge.roaster.model.JavaDocTag;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.impl.JavaDocImpl;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaDocSource;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;
import picocli.CommandLine;

@CommandLine.Command(name = "docgen", description = "Maven Documentation Generator")
public class CollectConfiguration implements Callable<Integer> {
    public static void main(String[] args) {
        new CommandLine(new CollectConfiguration()).execute(args);
    }

    protected static final String KEY = "key";

    public enum Mode {
        maven,
        resolver
    }

    @CommandLine.Option(
            names = {"-m", "--mode"},
            arity = "1",
            paramLabel = "mode",
            description = "The mode of generator (what is being scanned?), supported modes are 'maven', 'resolver'")
    protected Mode mode;

    @CommandLine.Option(
            names = {"-t", "--templates"},
            arity = "1",
            split = ",",
            paramLabel = "template",
            description = "The template names to write content out without '.vm' extension")
    protected List<String> templates;

    @CommandLine.Parameters(index = "0", description = "The root directory to process sources from")
    protected Path rootDirectory;

    @CommandLine.Parameters(index = "1", description = "The directory to generate output(s) to")
    protected Path outputDirectory;

    @Override
    public Integer call() {
        try {
            rootDirectory = rootDirectory.toAbsolutePath().normalize();
            outputDirectory = outputDirectory.toAbsolutePath().normalize();

            ArrayList<Map<String, String>> discoveredKeys = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(rootDirectory)) {
                if (mode == Mode.maven) {
                    System.out.println("Processing Maven sources from " + rootDirectory);
                    stream.map(Path::toAbsolutePath)
                            .filter(p -> p.getFileName().toString().endsWith(".class"))
                            .filter(p -> p.toString().contains("/target/classes/"))
                            .forEach(p -> {
                                processMavenClass(p, discoveredKeys);
                            });
                } else if (mode == Mode.resolver) {
                    System.out.println("Processing Resolver sources from " + rootDirectory);
                    stream.map(Path::toAbsolutePath)
                            .filter(p -> p.getFileName().toString().endsWith(".java"))
                            .filter(p -> p.toString().contains("/src/main/java/"))
                            .filter(p -> !p.toString().endsWith("/module-info.java"))
                            .forEach(p -> processResolverClass(p, discoveredKeys));
                } else {
                    throw new IllegalStateException("Unsupported mode " + mode);
                }
            }

            discoveredKeys.sort(Comparator.comparing(e -> e.get(KEY)));

            Properties properties = new Properties();
            properties.setProperty("resource.loaders", "classpath");
            properties.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
            VelocityEngine velocityEngine = new VelocityEngine();
            velocityEngine.init(properties);

            VelocityContext context = new VelocityContext();
            context.put("keys", discoveredKeys);

            for (String template : templates) {
                Path output = outputDirectory.resolve(template);
                System.out.println("Writing out to " + output);
                try (Writer fileWriter = new CachingWriter(output, StandardCharsets.UTF_8)) {
                    velocityEngine.getTemplate(template + ".vm").merge(context, fileWriter);
                }
            }
            return 0;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return 1;
        }
    }

    protected void processMavenClass(Path path, List<Map<String, String>> discoveredKeys) {
        try {
            ClassReader classReader = new ClassReader(Files.newInputStream(path));
            classReader.accept(
                    new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public FieldVisitor visitField(
                                int fieldAccess,
                                String fieldName,
                                String fieldDescriptor,
                                String fieldSignature,
                                Object fieldValue) {
                            return new FieldVisitor(Opcodes.ASM9) {
                                @Override
                                public AnnotationVisitor visitAnnotation(
                                        String annotationDescriptor, boolean annotationVisible) {
                                    if (annotationDescriptor.equals("Lorg/apache/maven/api/annotations/Config;")) {
                                        return new AnnotationVisitor(Opcodes.ASM9) {
                                            final Map<String, Object> values = new HashMap<>();

                                            @Override
                                            public void visit(String name, Object value) {
                                                values.put(name, value);
                                            }

                                            @Override
                                            public void visitEnum(String name, String descriptor, String value) {
                                                values.put(name, value);
                                            }

                                            @Override
                                            public void visitEnd() {
                                                JavaType<?> jtype = parse(Paths.get(path.toString()
                                                        .replace("/target/classes/", "/src/main/java/")
                                                        .replace(".class", ".java")));
                                                FieldSource<JavaClassSource> f =
                                                        ((JavaClassSource) jtype).getField(fieldName);

                                                String fqName = null;
                                                String desc = cloneJavadoc(f.getJavaDoc())
                                                        .removeAllTags()
                                                        .getFullText()
                                                        .replace("*", "\\*");
                                                String since = getSince(f);
                                                String source = (values.get("source") != null
                                                                ? (String) values.get("source")
                                                                : "USER_PROPERTIES") // TODO: enum
                                                        .toLowerCase();
                                                source = switch (source) {
                                                    case "model" -> "Model properties";
                                                    case "user_properties" -> "User properties";
                                                    default -> source;};
                                                String type = (values.get("type") != null
                                                        ? (String) values.get("type")
                                                        : "java.lang.String");
                                                if (type.startsWith("java.lang.")) {
                                                    type = type.substring("java.lang.".length());
                                                } else if (type.startsWith("java.util.")) {
                                                    type = type.substring("java.util.".length());
                                                }
                                                discoveredKeys.add(Map.of(
                                                        KEY,
                                                        fieldValue.toString(),
                                                        "defaultValue",
                                                        values.get("defaultValue") != null
                                                                ? values.get("defaultValue")
                                                                        .toString()
                                                                : "",
                                                        "fqName",
                                                        nvl(fqName, ""),
                                                        "description",
                                                        desc,
                                                        "since",
                                                        nvl(since, ""),
                                                        "configurationSource",
                                                        source,
                                                        "configurationType",
                                                        type));
                                            }
                                        };
                                    }
                                    return null;
                                }
                            };
                        }
                    },
                    0);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void processResolverClass(Path path, List<Map<String, String>> discoveredKeys) {
        JavaType<?> type = parse(path);
        if (type instanceof JavaClassSource javaClassSource) {
            javaClassSource.getFields().stream()
                    .filter(this::hasConfigurationSource)
                    .forEach(f -> {
                        Map<String, String> constants = extractConstants(Paths.get(path.toString()
                                .replace("/src/main/java/", "/target/classes/")
                                .replace(".java", ".class")));

                        String name = f.getName();
                        String key = constants.get(name);
                        String fqName = f.getOrigin().getCanonicalName() + "." + name;
                        String configurationType = getConfigurationType(f);
                        String defValue = getTag(f, "@configurationDefaultValue");
                        if (defValue != null && defValue.startsWith("{@link #") && defValue.endsWith("}")) {
                            // constant "lookup"
                            String lookupValue = constants.get(defValue.substring(8, defValue.length() - 1));
                            if (lookupValue == null) {
                                // currently we hard fail if javadoc cannot be looked up
                                // workaround: at cost of redundancy, but declare constants in situ for now
                                // (in same class)
                                throw new IllegalArgumentException(
                                        "Could not look up " + defValue + " for configuration " + fqName);
                            }
                            defValue = lookupValue;
                            if ("java.lang.Long".equals(configurationType)
                                    && (defValue.endsWith("l") || defValue.endsWith("L"))) {
                                defValue = defValue.substring(0, defValue.length() - 1);
                            }
                        }
                        discoveredKeys.add(Map.of(
                                KEY,
                                key,
                                "defaultValue",
                                nvl(defValue, ""),
                                "fqName",
                                fqName,
                                "description",
                                cleanseJavadoc(f),
                                "since",
                                nvl(getSince(f), ""),
                                "configurationSource",
                                getConfigurationSource(f),
                                "configurationType",
                                configurationType,
                                "supportRepoIdSuffix",
                                toYesNo(getTag(f, "@configurationRepoIdSuffix"))));
                    });
        }
    }

    protected JavaDocSource<Object> cloneJavadoc(JavaDocSource<?> javaDoc) {
        Javadoc jd = (Javadoc) javaDoc.getInternal();
        return new JavaDocImpl<>(javaDoc.getOrigin(), (Javadoc)
                ASTNode.copySubtree(AST.newAST(jd.getAST().apiLevel(), false), jd));
    }

    protected String cleanseJavadoc(FieldSource<JavaClassSource> javaClassSource) {
        JavaDoc<FieldSource<JavaClassSource>> javaDoc = javaClassSource.getJavaDoc();
        String[] text = javaDoc.getFullText().split("\n");
        StringBuilder result = new StringBuilder();
        for (String line : text) {
            if (!line.startsWith("@") && !line.trim().isEmpty()) {
                result.append(line);
            }
        }
        return cleanseTags(result.toString());
    }

    protected String cleanseTags(String text) {
        // {@code XXX} -> <pre>XXX</pre>
        // {@link XXX} -> ??? pre for now
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

    protected JavaType<?> parse(Path path) {
        try {
            return Roaster.parse(path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    protected String toYesNo(String value) {
        return "yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) ? "Yes" : "No";
    }

    protected String nvl(String string, String def) {
        return string == null ? def : string;
    }

    protected boolean hasConfigurationSource(JavaDocCapable<?> javaDocCapable) {
        return getTag(javaDocCapable, "@configurationSource") != null;
    }

    protected String getConfigurationType(JavaDocCapable<?> javaDocCapable) {
        String type = getTag(javaDocCapable, "@configurationType");
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

    protected String getConfigurationSource(JavaDocCapable<?> javaDocCapable) {
        String source = getTag(javaDocCapable, "@configurationSource");
        if ("{@link RepositorySystemSession#getConfigProperties()}".equals(source)) {
            return "Session Configuration";
        } else if ("{@link System#getProperty(String,String)}".equals(source)) {
            return "Java System Properties";
        } else {
            return source;
        }
    }

    protected String getSince(JavaDocCapable<?> javaDocCapable) {
        List<JavaDocTag> tags;
        if (javaDocCapable != null) {
            if (javaDocCapable instanceof FieldSource<?> fieldSource) {
                tags = fieldSource.getJavaDoc().getTags("@since");
                if (tags.isEmpty()) {
                    return getSince(fieldSource.getOrigin());
                } else {
                    return tags.get(0).getValue();
                }
            } else if (javaDocCapable instanceof JavaClassSource classSource) {
                tags = classSource.getJavaDoc().getTags("@since");
                if (!tags.isEmpty()) {
                    return tags.get(0).getValue();
                }
            }
        }
        return null;
    }

    protected String getTag(JavaDocCapable<?> javaDocCapable, String tagName) {
        List<JavaDocTag> tags;
        if (javaDocCapable != null) {
            if (javaDocCapable instanceof FieldSource<?> fieldSource) {
                tags = fieldSource.getJavaDoc().getTags(tagName);
                if (tags.isEmpty()) {
                    return getTag(fieldSource.getOrigin(), tagName);
                } else {
                    return tags.get(0).getValue();
                }
            }
        }
        return null;
    }

    protected static final Pattern CONSTANT_PATTERN = Pattern.compile(".*static final.* ([A-Z_]+) = (.*);");

    protected static final ToolProvider JAVAP = ToolProvider.findFirst("javap").orElseThrow();

    /**
     * Builds "constant table" for one single class.
     * <p>
     * Limitations:
     * - works only for single class (no inherited constants)
     * - does not work for fields that are Enum.name()
     * - more to come
     */
    protected static Map<String, String> extractConstants(Path file) {
        StringWriter out = new StringWriter();
        JAVAP.run(new PrintWriter(out), new PrintWriter(System.err), "-constants", file.toString());
        Map<String, String> result = new HashMap<>();
        out.getBuffer().toString().lines().forEach(l -> {
            Matcher matcher = CONSTANT_PATTERN.matcher(l);
            if (matcher.matches()) {
                result.put(matcher.group(1), matcher.group(2));
            }
        });
        return result;
    }
}
