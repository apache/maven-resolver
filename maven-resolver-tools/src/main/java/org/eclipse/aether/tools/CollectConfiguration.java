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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.spi.ToolProvider;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.JavaDocCapable;
import org.jboss.forge.roaster.model.JavaDocTag;
import org.jboss.forge.roaster.model.JavaType;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;

public class CollectConfiguration {
    public static void main(String[] args) throws Exception {
        Path start = Paths.get(args.length > 0 ? args[0] : ".");
        System.out.println("|Key|Type|Description|Default value|Since|Supports Repo ID suffix|Source|");
        System.out.println("|--|--|--|--|--|--|--|");

        Files.walk(start)
                .map(Path::toAbsolutePath)
                .filter(p -> p.getFileName().toString().endsWith(".java"))
                .filter(p -> p.toString().contains("/src/main/java/"))
                .forEach(p -> {
                    try {
                        JavaType<?> type = Roaster.parse(p.toFile());
                        if (type instanceof JavaClassSource javaClassSource) {
                            javaClassSource.getFields().stream()
                                    .filter(CollectConfiguration::hasConfigurationSource)
                                    .forEach(f -> {
                                        Map<String, String> constants = extractConstants(Paths.get(p.toString()
                                                .replace("/src/main/java/", "/target/classes/")
                                                .replace(".java", ".class")));

                                        String name = f.getName();
                                        String key = constants.get(name);
                                        String defValue = getTag(f, "@configurationDefaultValue");
                                        if (defValue != null
                                                && defValue.startsWith("{@link #")
                                                && defValue.endsWith("}")) {
                                            defValue = constants.get(defValue.substring(8, defValue.length() - 1));
                                        } else if (defValue == null) {
                                            defValue = "n/a";
                                        }
                                        String fqName = f.getOrigin().getCanonicalName() + "." + name;
                                        String description = f.getJavaDoc().getText();
                                        String since = nvl(getSince(f), "");
                                        String source = getConfigurationSource(f);
                                        String configurationType = getConfigurationType(f);
                                        String repoIdSuffix = nvl(getTag(f, "@configurationRepoIdSuffix"), "No");

                                        System.out.printf(
                                                "|`%s`|`%s`|%s|`%s`|%s|%s|%s|%n",
                                                key,
                                                configurationType,
                                                description,
                                                defValue,
                                                since,
                                                repoIdSuffix,
                                                source);
                                    });
                        }
                    } catch (Exception e) {
                        System.err.println(p + ": " + e.getMessage());
                    }
                });
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
            if (type.startsWith("{@link ") && type.endsWith("}")) {
                type = type.substring(7, type.length() - 1);
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
