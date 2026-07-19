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
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationCollectorDocletTest {

    /**
     * Classpath location of the fixture source declaring configuration keys of type {@link Boolean}, {@link String}
     * and a custom enum, using the same Javadoc block tags that the doclet extracts.
     */
    private static final String FIXTURE = "/org/eclipse/aether/sample/SampleConfigurationKeys.java";

    /**
     * Classpath location of the a fixture with invalid javadoc (missing/invalid elements).
     */
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
                runDoclet(out, getSourceFile(FIXTURE, tempDir), output), "doclet run should succeed, output:\n" + out);

        Map<String, Map<String, String>> keys = readKeys(output);
        assertEquals(4, keys.size(), "expected four configuration keys");

        Map<String, String> bool = keys.get("sample.bool");
        assertNotNull(bool, "boolean key missing");
        assertEquals("Boolean", bool.get("configurationType"));
        assertEquals("true", bool.get("defaultValue"));
        assertEquals("1.2.3", bool.get("since"));
        assertEquals("No", bool.get("supportRepoIdSuffix"));
        assertEquals("Java System Properties", bool.get("configurationSource"));
        assertEquals("A boolean flag.", bool.get("description"));

        Map<String, String> string = keys.get("sample.string");
        assertNotNull(string, "string key missing");
        assertEquals("String", string.get("configurationType"));
        assertEquals("\"hello\"", string.get("defaultValue"));
        assertEquals("Yes", string.get("supportRepoIdSuffix"));
        assertEquals("", string.get("since"), "no @since expected");

        Map<String, String> enumKey = keys.get("sample.enum");
        assertNotNull(enumKey, "enum key missing");
        assertEquals("org.eclipse.aether.sample.SampleConfigurationKeys.SampleEnum", enumKey.get("configurationType"));
        assertEquals("VALUE_A", enumKey.get("defaultValue"));
        // no @configurationRepoIdSuffix -> defaults to "No"
        assertEquals("No", enumKey.get("supportRepoIdSuffix"));

        Map<String, String> enum2Key = keys.get("sample.enum2");
        assertNotNull(enum2Key, "enum key missing");
        assertEquals("org.eclipse.aether.sample.SampleConfigurationKeys.SampleEnum", enum2Key.get("configurationType"));
        assertEquals("VALUE_B", enum2Key.get("defaultValue"));
        // no @configurationRepoIdSuffix -> defaults to "No"
        assertEquals("No", enum2Key.get("supportRepoIdSuffix"));
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

    @Test
    void invalidMode() throws Exception {
        CapturingDiagnosticsListener<JavaFileObject> listener =
                new CapturingDiagnosticsListener<>(javax.tools.Diagnostic.Kind.ERROR);
        StringWriter out = new StringWriter();
        assertFalse(runDoclet(out, getSourceFile(FIXTURE, output.getParent()), output, "invalid-mode", listener));
        // check that the diagnostics contain an error message about the invalid mode
        Diagnostic<? extends JavaFileObject> diagnostic =
                listener.getDiagnostics().iterator().next();
        assertEquals(javax.tools.Diagnostic.Kind.ERROR, diagnostic.getKind());
        assertEquals("Unknown mode: invalid-mode", diagnostic.getMessage(null));
        assertEquals(1, listener.getDiagnostics().size(), "expected one error diagnostic");
    }

    @Test
    void invalidTaglets() throws Exception {
        CapturingDiagnosticsListener<JavaFileObject> listener =
                new CapturingDiagnosticsListener<>(javax.tools.Diagnostic.Kind.ERROR);
        StringWriter out = new StringWriter();
        Path sourceFile = getSourceFile(INVALID_FIXTURE, output.getParent());
        assertFalse(runDoclet(out, sourceFile, output, "resolver", listener));
        // check that the diagnostics contain two error messages
        Iterator<Diagnostic<? extends JavaFileObject>> iterator =
                listener.getDiagnostics().iterator();
        Diagnostic<? extends JavaFileObject> diagnostic = iterator.next();
        assertEquals(javax.tools.Diagnostic.Kind.ERROR, diagnostic.getKind());
        assertEquals("Missing content for @configurationType", diagnostic.getMessage(null));
        assertEquals(sourceFile.toString(), diagnostic.getSource().getName());
        assertEquals(30, diagnostic.getLineNumber());
        assertEquals(2, listener.getDiagnostics().size(), "expected two error diagnostics");
        diagnostic = iterator.next();
        assertEquals(javax.tools.Diagnostic.Kind.ERROR, diagnostic.getKind());
        assertEquals("No valid {@link ...} reference found in @configurationType", diagnostic.getMessage(null));
        assertEquals(sourceFile.toString(), diagnostic.getSource().getName());
        assertEquals(46, diagnostic.getLineNumber());
    }

    private static Boolean runDoclet(Writer writer, Path sourceFile, Path output) throws Exception {
        return runDoclet(writer, sourceFile, output, null, null);
    }

    private static Boolean runDoclet(
            Writer writer, Path sourceFile, Path output, String mode, DiagnosticListener<JavaFileObject> listener)
            throws Exception {
        DocumentationTool documentationTool = ToolProvider.getSystemDocumentationTool();
        try (StandardJavaFileManager fileManager =
                documentationTool.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile.toFile()));
            final List<String> options;
            if (mode != null) {
                options = List.of("--output", output.toString(), "--mode", mode, "-encoding", "UTF-8");
            } else {
                options = List.of("--output", output.toString(), "-encoding", "UTF-8");
            }
            DocumentationTool.DocumentationTask task = documentationTool.getTask(
                    writer, fileManager, listener, ConfigurationCollectorDoclet.class, options, units);
            return task.call();
        }
    }

    private static Map<String, Map<String, String>> readKeys(Path output) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(output, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        int count = Integer.parseInt(properties.getProperty("keys.count", "0"));
        List<String> fields = new ArrayList<>(List.of(
                "key",
                "defaultValue",
                "fqName",
                "description",
                "since",
                "configurationSource",
                "configurationType",
                "supportRepoIdSuffix"));
        Map<String, Map<String, String>> result = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            Map<String, String> entry = new LinkedHashMap<>();
            for (String field : fields) {
                entry.put(field, properties.getProperty("keys." + i + "." + field, ""));
            }
            result.put(entry.get("key"), entry);
        }
        return result;
    }
}
