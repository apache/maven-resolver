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

import javax.tools.DiagnosticCollector;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.codehaus.plexus.util.io.CachingWriter;
import picocli.CommandLine;

@CommandLine.Command(name = "docgen", description = "Maven Documentation Generator")
public class CollectConfiguration implements Callable<Integer> {
    public static void main(String[] args) {
        new CommandLine(new CollectConfiguration()).execute(args);
    }

    protected static final String KEY = "key";

    /**
     * The metadata fields collected per configuration key and written to / read from the intermediate properties file.
     */
    protected static final List<String> FIELDS = List.of(
            KEY,
            "defaultValue",
            "fqName",
            "description",
            "since",
            "configurationSource",
            "configurationType",
            "supportRepoIdSuffix");

    /**
     * Javadoc block tag marking a constant field as a configuration key.
     */
    protected static final String CONFIGURATION_MARKER = "@configurationSource";

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

            System.out.println("Processing sources from " + rootDirectory);
            Path intermediateFile = Files.createTempFile("configuration-keys", ".properties");
            try {
                runDoclet(intermediateFile);
                List<Map<String, String>> discoveredKeys = readDiscoveredKeys(intermediateFile);
                discoveredKeys.sort(Comparator.comparing(e -> e.get(KEY)));
                render(discoveredKeys);
            } finally {
                Files.deleteIfExists(intermediateFile);
            }
            return 0;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            return 1;
        }
    }

    /**
     * Collects the source files under {@link #rootDirectory} and runs {@link ConfigurationCollectorDoclet} against them,
     * having it write the discovered configuration keys into the given intermediate properties file.
     */
    protected void runDoclet(Path intermediateFile) throws Exception {
        // Only feed javadoc the files that actually declare configuration keys. This keeps the set of types that
        // javadoc must resolve small, avoiding failures caused by unrelated sources referencing dependencies that
        // are not on this module's classpath (e.g. gson, jetty).
        List<File> sourceFiles;
        try (Stream<Path> stream = Files.walk(rootDirectory)) {
            sourceFiles = stream.map(Path::toAbsolutePath)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> p.toString().contains("/src/main/java/"))
                    .filter(p -> !p.toString().endsWith("/module-info.java"))
                    .filter(p -> !p.toString().contains("/maven-resolver-tools/"))
                    .filter(p -> fileContains(p, CONFIGURATION_MARKER))
                    .map(Path::toFile)
                    .collect(Collectors.toList());
        }
        if (sourceFiles.isEmpty()) {
            throw new IllegalStateException(
                    "No Java sources declaring configuration keys found under " + rootDirectory);
        }

        DocumentationTool documentationTool = ToolProvider.getSystemDocumentationTool();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager =
                documentationTool.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            // Configure the classpath on the file manager (the -classpath option is not honored when a file manager
            // is supplied to getTask()). Note that under exec:java the project dependencies are on the context
            // classloader, not on the JVM's java.class.path.
            fileManager.setLocation(StandardLocation.CLASS_PATH, resolveClasspath());

            Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(sourceFiles);

            List<String> options =
                    new ArrayList<>(Arrays.asList("--output", intermediateFile.toString(), "-encoding", "UTF-8"));

            Writer out = new PrintWriter(System.err);
            DocumentationTool.DocumentationTask task = documentationTool.getTask(
                    out, fileManager, diagnostics, ConfigurationCollectorDoclet.class, options, compilationUnits);
            boolean ok = task.call();
            out.flush();
            if (!ok) {
                diagnostics.getDiagnostics().forEach(d -> System.err.println(d));
                throw new IllegalStateException("Javadoc doclet execution failed");
            }
        }
    }

    private static boolean fileContains(Path path, String marker) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).contains(marker);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Resolves the classpath to use for symbol resolution during the javadoc run. Under {@code exec:java} the project
     * dependencies live on the context classloader (a {@link URLClassLoader}), not on the JVM's
     * {@code java.class.path}, so both sources are combined.
     */
    private static List<File> resolveClasspath() {
        List<File> classpath = new ArrayList<>();
        for (ClassLoader cl = Thread.currentThread().getContextClassLoader(); cl != null; cl = cl.getParent()) {
            if (cl instanceof URLClassLoader) {
                for (URL url : ((URLClassLoader) cl).getURLs()) {
                    if ("file".equals(url.getProtocol())) {
                        try {
                            classpath.add(new File(url.toURI()));
                        } catch (URISyntaxException e) {
                            classpath.add(new File(url.getPath()));
                        }
                    }
                }
            }
        }
        for (String element : System.getProperty("java.class.path").split(File.pathSeparator)) {
            classpath.add(new File(element));
        }
        return classpath;
    }

    /**
     * Reads back the intermediate properties file produced by {@link ConfigurationCollectorDoclet} into the list of
     * maps consumed by the Velocity templates.
     */
    protected List<Map<String, String>> readDiscoveredKeys(Path intermediateFile) throws Exception {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(intermediateFile, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        int count = Integer.parseInt(properties.getProperty("keys.count", "0"));
        List<Map<String, String>> discoveredKeys = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            Map<String, String> entry = new LinkedHashMap<>();
            for (String field : FIELDS) {
                entry.put(field, properties.getProperty("keys." + i + "." + field, ""));
            }
            discoveredKeys.add(entry);
        }
        return discoveredKeys;
    }

    protected void render(List<Map<String, String>> discoveredKeys) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("resource.loaders", "classpath");
        properties.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
        VelocityEngine velocityEngine = new VelocityEngine();
        velocityEngine.init(properties);

        VelocityContext context = new VelocityContext();
        context.put("keys", discoveredKeys);

        for (String template : templates) {
            Path output = outputDirectory.resolve(template);
            Files.createDirectories(output.getParent());
            System.out.println("Writing out to " + output);
            try (Writer fileWriter = new CachingWriter(output, StandardCharsets.UTF_8)) {
                velocityEngine.getTemplate(template + ".vm").merge(context, fileWriter);
            }
        }
    }
}
