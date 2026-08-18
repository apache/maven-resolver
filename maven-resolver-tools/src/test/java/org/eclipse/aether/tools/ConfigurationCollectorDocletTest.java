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

import javax.tools.Diagnostic;
import javax.tools.DiagnosticListener;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationCollectorDocletTest {

    /** Classpath location of the fixture source declaring configuration keys of type {@link Boolean}, {@link String} and a custom enum,
     * using the same Javadoc block tags that the doclet extracts. */
    private static final String FIXTURE = "/org/eclipse/aether/sample/SampleConfigurationKeys.java";

    private static final String FIXTURE_PACKAGE_INFO = "/org/eclipse/aether/sample/package-info.java";

    private static final String MODULE_FIXTURE = "/org/eclipse/aether/modulefixture/ModuleConfigurationKeys.java";

    private static final String MODULE_INFO_FIXTURE = "/org/eclipse/aether/modulefixture/module-info.java";

    /** Classpath location of the fixture with invalid javadoc (missing/invalid elements). */
    private static final String INVALID_FIXTURE = "/org/eclipse/aether/sample/InvalidSampleConfigurationKeys.java";

    private Path output;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        output = tempDir.resolve("configuration-keys.properties");
    }

    private Path getSourceFile(String resourcePath, Path tempDir) throws Exception {
        if (!resourcePath.startsWith("/")) {
            throw new IllegalArgumentException("resource path must start with '/': " + resourcePath);
        }
        Path sourceDir =
                Files.createDirectories(tempDir.resolve(resourcePath.substring(1, resourcePath.lastIndexOf('/'))));
        Path sourceFile = sourceDir.resolve(resourcePath.substring(resourcePath.lastIndexOf('/') + 1));
        try (InputStream in = ConfigurationCollectorDocletTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(in, "resource path not found on classpath: " + resourcePath);
            Files.copy(in, sourceFile);
        }
        return sourceFile;
    }

    @Test
    void extractsBooleanStringAndEnumConfigurations(@TempDir Path tempDir) throws Exception {
        StringWriter out = new StringWriter();
        assertTrue(
                runDoclet(
                        out,
                        List.of(getSourceFile(FIXTURE, tempDir), getSourceFile(FIXTURE_PACKAGE_INFO, tempDir)),
                        output),
                "doclet run should succeed, output:\n" + out);

        Map<String, Map<String, String>> keys = readKeys(output);
        assertEquals(4, keys.size(), "expected four configuration keys");

        Map<String, String> bool = keys.get("sample.bool");
        assertNotNull(bool, "boolean key missing");
        assertEquals("Boolean", bool.get("configurationType"));
        assertEquals("", bool.get("configurationTypeJavadocUrl"));
        assertEquals("true", bool.get("defaultValue"));
        assertEquals("1.2.3", bool.get("since"));
        assertEquals("No", bool.get("supportRepoIdSuffix"));
        assertEquals("Java System Properties", bool.get("configurationSource"));
        assertEquals("A boolean flag.", bool.get("description"));
        assertEquals("", bool.get("deprecated"));

        Map<String, String> string = keys.get("sample.string");
        assertNotNull(string, "string key missing");
        assertEquals("String", string.get("configurationType"));
        assertEquals("\"hello\"", string.get("defaultValue"));
        assertEquals("2.0", string.get("since")); // from package-info.java
        assertEquals("Yes", string.get("supportRepoIdSuffix"));
        assertEquals(
                "A string value with some inline tags. Value <code>\"hello\"</code> is the default. <code>some.property</code> is used. <code>This text is code.</code> This text is literal. <code>java.lang.String</code> is the type. <code>String#valueOf(int)</code> is an external method. <code>java.base</code> is an external module. <code>java.util.List</code> is not available in the configured external Javadoc. See JDK bug <a href=\"https://bugs.openjdk.org/browse/JDK-8225647\">JDK-8225647</a> for details.",
                string.get("description"));

        Map<String, String> enumKey = keys.get("sample.enum");
        assertNotNull(enumKey, "enum key missing");
        assertEquals("org.eclipse.aether.sample.SampleConfigurationKeys.SampleEnum", enumKey.get("configurationType"));
        assertEquals(
                "apidocs/org/eclipse/aether/sample/SampleConfigurationKeys.SampleEnum.html",
                enumKey.get("configurationTypeJavadocUrl"));
        assertEquals("VALUE_A", enumKey.get("defaultValue"));
        String enumDescription = enumKey.get("description");
        assertTrue(enumDescription.contains("<a href=\"#sample.string\"><code>#STRING_KEY</code></a>"));
        assertTrue(
                enumDescription.contains(
                        "<a href=\"apidocs/org/eclipse/aether/sample/SampleConfigurationKeys.SampleType.html#VALUE\"><code>SampleType#VALUE</code></a>"));
        assertTrue(
                enumDescription.contains(
                        "<a href=\"apidocs/org/eclipse/aether/sample/SampleConfigurationKeys.SampleType.html#convert(java.lang.String)\"><code>SampleType#convert(String)</code></a>"));
        assertTrue(
                enumDescription.contains(
                        "<a href=\"apidocs/org/eclipse/aether/sample/SampleConfigurationKeys.SampleType.html#&lt;init&gt;(java.lang.String)\"><code>SampleType#SampleType(String)</code></a>"));
        assertTrue(
                enumDescription.contains(
                        "<a href=\"apidocs/org/eclipse/aether/sample/SampleConfigurationKeys.SampleType.html\"><code>SampleType</code></a>"));
        assertTrue(
                enumDescription.contains(
                        "<a href=\"apidocs/org/eclipse/aether/sample/package-summary.html\"><code>org.eclipse.aether.sample</code></a>"));
        assertTrue(enumDescription.contains("<code>SampleType#hidden()</code>"));
        assertFalse(enumDescription.contains(
                "href=\"apidocs/" + "org/eclipse/aether/sample/SampleConfigurationKeys.SampleType.html#hidden()\""));
        // no @configurationRepoIdSuffix -> defaults to "No"
        assertEquals("No", enumKey.get("supportRepoIdSuffix"));
        assertEquals("Use <a href=\"#sample.enum2\"><code>#ENUM2_KEY</code></a> instead", enumKey.get("deprecated"));

        Map<String, String> enum2Key = keys.get("sample.enum2");
        assertNotNull(enum2Key, "enum key missing");
        assertEquals("org.eclipse.aether.sample.SampleConfigurationKeys.SampleEnum", enum2Key.get("configurationType"));
        assertEquals("VALUE_B", enum2Key.get("defaultValue"));
        // no @configurationRepoIdSuffix -> defaults to "No"
        assertEquals("No", enum2Key.get("supportRepoIdSuffix"));
    }

    @Test
    void rendersConfigurationAnchorsAndTypeJavadocLinks(@TempDir Path tempDir) throws Exception {
        StringWriter out = new StringWriter();
        assertTrue(
                runDoclet(
                        out,
                        List.of(getSourceFile(FIXTURE, tempDir), getSourceFile(FIXTURE_PACKAGE_INFO, tempDir)),
                        output),
                "doclet run should succeed, output:\n" + out);

        CollectConfiguration collector = new CollectConfiguration();
        collector.templates = List.of("configuration.md");
        collector.outputDirectory = tempDir.resolve("generated");
        collector.render(CollectConfiguration.readDiscoveredKeys(output));

        String markdown = Files.readString(collector.outputDirectory.resolve("configuration.md"));
        assertTrue(markdown.contains("<a id=\"sample.enum\"></a>`sample.enum`"));
        assertTrue(
                markdown.contains(
                        "[`org.eclipse.aether.sample.SampleConfigurationKeys.SampleEnum`](apidocs/org/eclipse/aether/sample/SampleConfigurationKeys.SampleEnum.html)"));
        assertTrue(markdown.contains("<a href=\"#sample.string\"><code>#STRING_KEY</code></a>"));
        assertTrue(markdown.contains("**Deprecated**. *Use <a href=\"#sample.enum2\">"));
    }

    @Test
    void usesConfiguredInternalJavadocUrlAndVersion(@TempDir Path tempDir) throws Exception {
        StringWriter out = new StringWriter();
        assertTrue(
                runDoclet(
                        out,
                        List.of(getSourceFile(FIXTURE, tempDir), getSourceFile(FIXTURE_PACKAGE_INFO, tempDir)),
                        output,
                        "--internal-javadoc-url",
                        "../reference",
                        "--internal-javadoc-version",
                        "8"),
                "doclet run should succeed, output:\n" + out);

        String description = readKeys(output).get("sample.enum").get("description");
        assertTrue(
                description.contains(
                        "href=\"../reference/org/eclipse/aether/sample/SampleConfigurationKeys.SampleType.html#convert-java.lang.String-\""));
        assertTrue(
                description.contains(
                        "href=\"../reference/org/eclipse/aether/sample/SampleConfigurationKeys.SampleType.html#SampleType-java.lang.String-\""));
    }

    @Test
    void linksPackagesFoundInInternalJavadocSourceRoots(@TempDir Path tempDir) throws Exception {
        StringWriter out = new StringWriter();
        getSourceFile(FIXTURE_PACKAGE_INFO, tempDir);
        assertTrue(
                runDoclet(
                        out,
                        List.of(getSourceFile(FIXTURE, tempDir)),
                        output,
                        "--internal-javadoc-source-root",
                        tempDir.toString()),
                "doclet run should succeed, output:\n" + out);

        assertTrue(
                readKeys(output)
                        .get("sample.enum")
                        .get("description")
                        .contains(
                                "<a href=\"apidocs/org/eclipse/aether/sample/package-summary.html\"><code>org.eclipse.aether.sample</code></a>"));
    }

    @Test
    void linksOnlyExternalElementsPresentInConfiguredJavadoc(@TempDir Path tempDir) throws Exception {
        Path externalJavadoc = Files.createDirectories(tempDir.resolve("external-javadoc"));
        Files.writeString(externalJavadoc.resolve("element-list"), "module:java.base\njava.lang\n");
        Path moduleDirectory = Files.createDirectories(externalJavadoc.resolve("java.base"));
        Files.writeString(moduleDirectory.resolve("module-summary.html"), "<html></html>");
        Path stringJavadoc =
                Files.createDirectories(moduleDirectory.resolve("java/lang")).resolve("String.html");
        Files.writeString(stringJavadoc, "<section id=\"valueOf(int)\"></section>");

        StringWriter out = new StringWriter();
        assertTrue(
                runDoclet(
                        out,
                        List.of(getSourceFile(FIXTURE, tempDir), getSourceFile(FIXTURE_PACKAGE_INFO, tempDir)),
                        output,
                        "--external-javadoc-url",
                        tempDir.resolve("unavailable-javadoc").toUri().toString(),
                        "--external-javadoc-url",
                        externalJavadoc.toUri().toString()),
                "doclet run should succeed, output:\n" + out);

        Map<String, Map<String, String>> keys = readKeys(output);
        String externalStringUrl = externalJavadoc
                .toUri()
                .resolve("java.base/java/lang/String.html")
                .toString();
        assertEquals(externalStringUrl, keys.get("sample.string").get("configurationTypeJavadocUrl"));
        assertTrue(keys.get("sample.string")
                .get("description")
                .contains("<a href=\"" + externalStringUrl + "\"><code>java.lang.String</code></a>"));
        String description = keys.get("sample.string").get("description");
        assertTrue(description.contains(
                "<a href=\"" + externalStringUrl + "#valueOf(int)\"><code>String#valueOf(int)</code></a>"));
        assertTrue(description.contains("<a href=\""
                + externalJavadoc.toUri().resolve("java.base/module-summary.html")
                + "\"><code>java.base</code></a>"));
        assertEquals(
                externalJavadoc
                        .toUri()
                        .resolve("java.base/java/lang/Boolean.html")
                        .toString(),
                keys.get("sample.bool").get("configurationTypeJavadocUrl"));
        assertTrue(description.contains("<code>java.util.List</code>"));
        assertFalse(description.contains("external-javadoc/java.base/java/util/List.html"));
    }

    @Test
    void linksModuleElements(@TempDir Path tempDir) throws Exception {
        StringWriter out = new StringWriter();
        assertTrue(
                runDoclet(
                        out,
                        List.of(getSourceFile(MODULE_INFO_FIXTURE, tempDir), getSourceFile(MODULE_FIXTURE, tempDir)),
                        output),
                "doclet run should succeed, output:\n" + out);

        assertTrue(
                readKeys(output)
                        .get("sample.module")
                        .get("description")
                        .contains(
                                "<a href=\"apidocs/org.eclipse.aether.sample.module/module-summary.html\"><code>org.eclipse.aether.sample.module</code></a>"));
    }

    static final class CapturingDiagnosticsListener<T extends JavaFileObject>
            implements javax.tools.DiagnosticListener<T> {
        private final javax.tools.Diagnostic.Kind threshold;
        private final Collection<Diagnostic<? extends JavaFileObject>> diagnostics = new ArrayList<>();

        public CapturingDiagnosticsListener(javax.tools.Diagnostic.Kind threshold) {
            this.threshold = threshold;
        }

        @Override
        public void report(javax.tools.Diagnostic<? extends T> diagnostic) {
            if (diagnostic.getKind().compareTo(threshold) <= 0) {
                diagnostics.add(diagnostic);
            }
        }

        public Collection<Diagnostic<? extends JavaFileObject>> getDiagnostics() {
            return diagnostics;
        }
    }

    static final class LoggingDiagnosticsListener<T extends JavaFileObject>
            implements javax.tools.DiagnosticListener<T> {
        private final Logger logger;

        public LoggingDiagnosticsListener(Logger logger) {
            this.logger = logger;
        }

        @Override
        public void report(javax.tools.Diagnostic<? extends T> diagnostic) {
            switch (diagnostic.getKind()) {
                case ERROR:
                    logger.error(diagnostic.getMessage(null));
                    break;
                case WARNING:
                    logger.warn(diagnostic.getMessage(null));
                    break;
                case MANDATORY_WARNING:
                    logger.warn(diagnostic.getMessage(null));
                    break;
                case NOTE:
                    logger.info(diagnostic.getMessage(null));
                    break;
                case OTHER:
                    logger.debug(diagnostic.getMessage(null));
                    break;
            }
        }
    }

    @Test
    void invalidMode() throws Exception {
        CapturingDiagnosticsListener<JavaFileObject> listener =
                new CapturingDiagnosticsListener<>(javax.tools.Diagnostic.Kind.ERROR);
        StringWriter out = new StringWriter();
        assertFalse(
                runDoclet(out, List.of(getSourceFile(FIXTURE, output.getParent())), output, "invalid-mode", listener));
        // check that the diagnostics contain an error message about the invalid mode
        Diagnostic<? extends JavaFileObject> diagnostic =
                listener.getDiagnostics().iterator().next();
        assertEquals(javax.tools.Diagnostic.Kind.ERROR, diagnostic.getKind());
        // IAE thrown via Doclet.Option#parseOptions, which is caught and reported as a diagnostic
        String substring = "java.lang.IllegalArgumentException: Invalid mode: invalid-mode";
        assertTrue(
                diagnostic.getMessage(null).contains(substring),
                "expected diagnostic message to contain: " + substring + " but was: " + diagnostic.getMessage(null));
    }

    @Test
    void invalidTaglets() throws Exception {
        CapturingDiagnosticsListener<JavaFileObject> listener =
                new CapturingDiagnosticsListener<>(javax.tools.Diagnostic.Kind.ERROR);
        StringWriter out = new StringWriter();
        Path sourceFile = getSourceFile(INVALID_FIXTURE, output.getParent());
        assertFalse(runDoclet(out, List.of(sourceFile), output, "resolver", listener));
        // check that the diagnostics contain two error messages
        Iterator<Diagnostic<? extends JavaFileObject>> iterator =
                listener.getDiagnostics().iterator();
        Diagnostic<? extends JavaFileObject> diagnostic = iterator.next();
        assertEquals(javax.tools.Diagnostic.Kind.ERROR, diagnostic.getKind());
        assertEquals("Missing block tag @configurationType", diagnostic.getMessage(null));
        assertEquals(sourceFile.toString(), diagnostic.getSource().getName());
        assertEquals(30, diagnostic.getLineNumber());
        assertEquals(2, listener.getDiagnostics().size(), "expected two error diagnostics");
        diagnostic = iterator.next();
        assertEquals(javax.tools.Diagnostic.Kind.ERROR, diagnostic.getKind());
        assertEquals("No valid {@link ...} reference found in @configurationType", diagnostic.getMessage(null));
        assertEquals(sourceFile.toString(), diagnostic.getSource().getName());
        assertEquals(47, diagnostic.getLineNumber());
    }

    private static Boolean runDoclet(
            Writer writer, Collection<Path> sourceFiles, Path output, String... additionalOptions) throws Exception {
        return runDoclet(
                writer,
                sourceFiles,
                output,
                null,
                new LoggingDiagnosticsListener<>(
                        org.slf4j.LoggerFactory.getLogger(ConfigurationCollectorDocletTest.class)),
                additionalOptions);
    }

    private static Boolean runDoclet(
            Writer writer,
            Collection<Path> sourceFiles,
            Path output,
            String mode,
            DiagnosticListener<JavaFileObject> listener,
            String... additionalOptions)
            throws Exception {
        DocumentationTool documentationTool = ToolProvider.getSystemDocumentationTool();
        try (StandardJavaFileManager fileManager =
                documentationTool.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(sourceFiles);
            final List<String> options = new ArrayList<>();
            if (mode != null) {
                options.addAll(List.of("--mode", mode));
            }
            options.addAll(List.of("--output", output.toString(), "-encoding", "UTF-8"));
            options.addAll(List.of(additionalOptions));
            DocumentationTool.DocumentationTask task = documentationTool.getTask(
                    writer, fileManager, listener, ConfigurationCollectorDoclet.class, options, units);
            return task.call();
        }
    }

    private static Map<String, Map<String, String>> readKeys(Path output) throws Exception {
        Collection<Map<String, String>> keys = CollectConfiguration.readDiscoveredKeys(output);
        return keys.stream().collect(Collectors.toMap(key -> key.get(CollectConfiguration.KEY), key -> key));
    }
}
