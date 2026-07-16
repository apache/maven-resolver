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

import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationCollectorDocletTest {

    /**
     * Classpath location of the fixture source declaring configuration keys of type {@link Boolean}, {@link String}
     * and a custom enum, using the same Javadoc block tags that the doclet extracts.
     */
    private static final String FIXTURE = "/org/eclipse/aether/sample/SampleConfigurationKeys.java";

    @Test
    void extractsBooleanStringAndEnumConfigurations(@TempDir Path tempDir) throws Exception {
        Path sourceDir = Files.createDirectories(tempDir.resolve("org/eclipse/aether/sample"));
        Path sourceFile = sourceDir.resolve("SampleConfigurationKeys.java");
        try (InputStream in = ConfigurationCollectorDocletTest.class.getResourceAsStream(FIXTURE)) {
            assertNotNull(in, "fixture source not found on classpath: " + FIXTURE);
            Files.copy(in, sourceFile);
        }
        Path output = tempDir.resolve("configuration-keys.properties");

        runDoclet(sourceFile, output);

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

    private static void runDoclet(Path sourceFile, Path output) throws Exception {
        DocumentationTool documentationTool = ToolProvider.getSystemDocumentationTool();
        try (StandardJavaFileManager fileManager =
                documentationTool.getStandardFileManager(null, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units =
                    fileManager.getJavaFileObjectsFromFiles(List.of(sourceFile.toFile()));
            List<String> options = List.of("--output", output.toString(), "-encoding", "UTF-8");
            StringWriter out = new StringWriter();
            DocumentationTool.DocumentationTask task = documentationTool.getTask(
                    out, fileManager, null, ConfigurationCollectorDoclet.class, options, units);
            assertTrue(task.call(), "doclet run should succeed, output:\n" + out);
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
