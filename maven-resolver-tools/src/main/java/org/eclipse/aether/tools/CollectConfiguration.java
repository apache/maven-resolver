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

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaDocCapable;
import org.jboss.forge.roaster.model.JavaDocTag;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;

public class CollectConfiguration {
    public static void main(String[] args) throws Exception {
        Path start = Paths.get(args.length > 0 ? args[0] : ".");
        Path output = Paths.get(args.length > 1 ? args[1] : "output");
        Path props = Paths.get(args.length > 2 ? args[2] : "props");
        Path yaml = Paths.get(args.length > 3 ? args[3] : "yaml");

        TreeMap<String, ConfigurationKey> discoveredKeys = new TreeMap<>();
        Files.walk(start)
                .map(Path::toAbsolutePath)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .filter(p -> p.toString().contains("/src/main/java/"))
                .filter(p -> !p.toString().endsWith("/module-info.java"))
                .forEach(p -> {
                    JavaType<?> type = parse(p);
                    if (type instanceof JavaClassSource javaClassSource) {
                        javaClassSource.getFields().stream()
                                .filter(CollectConfiguration::hasConfigurationSource)
                                .forEach(f -> {
                                    Map<String, String> constants = extractConstants(Paths.get(p.toString()
                                            .replace("/src/main/java/", "/target/classes/")
                                            .replace(".java", ".class")));

                                    String name = f.getName();
                                    String key = constants.get(name);
                                    String fqName = f.getOrigin().getCanonicalName() + "." + name;
                                    String configurationType = getConfigurationType(f);
                                    String defValue = getTag(f, "@configurationDefaultValue");
                                    if (defValue != null && defValue.startsWith("{@link #") && defValue.endsWith("}")) {
                                        // constant "lookup"
                                        String lookupValue =
                                                constants.get(defValue.substring(8, defValue.length() - 1));
                                        if (lookupValue == null) {
                                            // currently we hard fail if javadoc cannot be looked up
                                            // workaround: at cost of redundancy, but declare constants in situ for now
                                            // (in same class)
                                            throw new IllegalArgumentException(
                                                    "Could not look up " + defValue + " for configuration " + fqName);
                                        }
                                        defValue = lookupValue;
                                    }
                                    if ("java.lang.Long".equals(configurationType)
                                            && (defValue.endsWith("l") || defValue.endsWith("L"))) {
                                        defValue = defValue.substring(0, defValue.length() - 1);
                                    }
                                    discoveredKeys.put(
                                            key,
                                            new ConfigurationKey(
                                                    key,
                                                    defValue,
                                                    fqName,
                                                    f.getJavaDoc().getText(),
                                                    nvl(getSince(f), ""),
                                                    getConfigurationSource(f),
                                                    configurationType,
                                                    toBoolean(getTag(f, "@configurationRepoIdSuffix"))));
                                });
                    }
                });

        VelocityEngine velocityEngine = new VelocityEngine();
        Properties properties = new Properties();
        properties.setProperty("resource.loaders", "classpath");
        properties.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
        velocityEngine.init(properties);

        VelocityContext context = new VelocityContext();
        context.put("keys", discoveredKeys.values());

        try (BufferedWriter fileWriter = Files.newBufferedWriter(output)) {
            velocityEngine.getTemplate("page.vm").merge(context, fileWriter);
        }
        try (BufferedWriter fileWriter = Files.newBufferedWriter(props)) {
            velocityEngine.getTemplate("props.vm").merge(context, fileWriter);
        }
        try (BufferedWriter fileWriter = Files.newBufferedWriter(yaml)) {
            velocityEngine.getTemplate("yaml.vm").merge(context, fileWriter);
        }
    }

    private static JavaType<?> parse(Path path) {
        try {
            return Roaster.parse(path.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean toBoolean(String value) {
        return ("yes".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value));
    }

    /**
     * Would be record, but... Velocity have no idea what it is nor how to handle it.
     */
    public static class ConfigurationKey {
        private final String key;
        private final String defaultValue;
        private final String fqName;
        private final String description;
        private final String since;
        private final String configurationSource;
        private final String configurationType;
        private final boolean supportRepoIdSuffix;

        @SuppressWarnings("checkstyle:parameternumber")
        public ConfigurationKey(
                String key,
                String defaultValue,
                String fqName,
                String description,
                String since,
                String configurationSource,
                String configurationType,
                boolean supportRepoIdSuffix) {
            this.key = key;
            this.defaultValue = defaultValue;
            this.fqName = fqName;
            this.description = description;
            this.since = since;
            this.configurationSource = configurationSource;
            this.configurationType = configurationType;
            this.supportRepoIdSuffix = supportRepoIdSuffix;
        }

        public String getKey() {
            return key;
        }

        public String getDefaultValue() {
            return defaultValue;
        }

        public String getFqName() {
            return fqName;
        }

        public String getDescription() {
            return description;
        }

        public String getSince() {
            return since;
        }

        public String getConfigurationSource() {
            return configurationSource;
        }

        public String getConfigurationType() {
            return configurationType;
        }

        public boolean isSupportRepoIdSuffix() {
            return supportRepoIdSuffix;
        }
    }

    private static String nvl(String string, String def) {
        return string == null ? def : string;
    }

    private static boolean hasConfigurationSource(JavaDocCapable<?> javaDocCapable) {
        return getTag(javaDocCapable, "@configurationSource") != null;
    }

    private static String getConfigurationType(JavaDocCapable<?> javaDocCapable) {
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

    private static String getConfigurationSource(JavaDocCapable<?> javaDocCapable) {
        String source = getTag(javaDocCapable, "@configurationSource");
        if ("{@link RepositorySystemSession#getConfigProperties()}".equals(source)) {
            return "Session Configuration";
        } else if ("{@link System#getProperty(String,String)}".equals(source)) {
            return "Java System Properties";
        } else {
            return source;
        }
    }

    private static String getSince(JavaDocCapable<?> javaDocCapable) {
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

    private static String getTag(JavaDocCapable<?> javaDocCapable, String tagName) {
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

    private static final Pattern CONSTANT_PATTERN = Pattern.compile(".*static final.* ([A-Z_]+) = (.*);");

    private static final ToolProvider JAVAP = ToolProvider.findFirst("javap").orElseThrow();

    /**
     * Builds "constant table" for one single class.
     *
     * Limitations:
     * - works only for single class (no inherited constants)
     * - does not work for fields that are Enum.name()
     * - more to come
     */
    private static Map<String, String> extractConstants(Path file) {
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
