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
package org.eclipse.aether.util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.metadata.Metadata;

import static java.util.Objects.requireNonNull;

/**
 * A reusable utility class for file paths.
 *
 * @since 2.0.13
 */
public final class PathUtils {
    private PathUtils() {
        // hide constructor
    }

    private static final Map<String, String> ILLEGAL_PATH_SEGMENT_REPLACEMENTS;

    static {
        HashMap<String, String> illegalPathSegmentReplacements = new HashMap<>();
        illegalPathSegmentReplacements.put("\\", "-BACKSLASH-");
        illegalPathSegmentReplacements.put("/", "-SLASH-");
        illegalPathSegmentReplacements.put(":", "-COLON-");
        illegalPathSegmentReplacements.put("\"", "-QUOTE-");
        illegalPathSegmentReplacements.put("<", "-LT-");
        illegalPathSegmentReplacements.put(">", "-GT-");
        illegalPathSegmentReplacements.put("|", "-PIPE-");
        illegalPathSegmentReplacements.put("?", "-QMARK-");
        illegalPathSegmentReplacements.put("*", "-ASTERISK-");
        ILLEGAL_PATH_SEGMENT_REPLACEMENTS = Collections.unmodifiableMap(illegalPathSegmentReplacements);
    }

    /**
     * Method that makes sure that passed in string is valid "path segment" string. It achieves it by potentially
     * changing it, replacing illegal characters in it with legal ones.
     * <p>
     * Note: this method considers empty string as "valid path segment", it is caller duty to ensure empty string
     * is not used as path segment alone.
     * <p>
     * This method is simplistic on purpose, and if frequently used, best if results are cached (per session)
     */
    public static String stringToPathSegment(String string) {
        requireNonNull(string);
        StringBuilder result = new StringBuilder(string);
        for (Map.Entry<String, String> entry : ILLEGAL_PATH_SEGMENT_REPLACEMENTS.entrySet()) {
            String illegal = entry.getKey();
            int pos = result.indexOf(illegal);
            while (pos >= 0) {
                result.replace(pos, pos + illegal.length(), entry.getValue());
                pos = result.indexOf(illegal);
            }
        }
        return result.toString();
    }

    /**
     * Validates that a coordinate component does not contain path traversal sequences
     * or path separator characters that could cause the composed path to escape
     * the local repository directory.
     *
     * @since 2.0.21
     */
    public static void validatePathComponent(String value, String label) {
        if (value != null && !value.isEmpty()) {
            // Important: "equals .." and not "contains ..", as if escape attempted, it will contain path separators
            // OTOH: version "1.." is valid version string!
            if (value.equals("..") || value.contains("/") || value.contains("\\")) {
                throw new IllegalArgumentException(
                        "Invalid " + label + ": must not contain '..', '/' or '\\': " + value);
            }
        }
    }

    /**
     * Validates all coordinate components of an {@link Artifact}.
     *
     * @see #validatePathComponent(String, String)
     * @since 2.0.21
     */
    public static void validateArtifactComponents(Artifact artifact) {
        validatePathComponent(artifact.getGroupId(), "groupId");
        validatePathComponent(artifact.getArtifactId(), "artifactId");
        validatePathComponent(artifact.getVersion(), "version");
        validatePathComponent(artifact.getBaseVersion(), "baseVersion");
        validatePathComponent(artifact.getClassifier(), "classifier");
        validatePathComponent(artifact.getExtension(), "extension");
    }

    /**
     * Validates all coordinate components of a {@link Metadata}.
     *
     * @see #validatePathComponent(String, String)
     * @since 2.0.21
     */
    public static void validateMetadataComponents(Metadata metadata) {
        validatePathComponent(metadata.getGroupId(), "groupId");
        validatePathComponent(metadata.getArtifactId(), "artifactId");
        validatePathComponent(metadata.getVersion(), "version");
        // note: type may contain string like ".meta/prefixes.txt"!
    }
}
