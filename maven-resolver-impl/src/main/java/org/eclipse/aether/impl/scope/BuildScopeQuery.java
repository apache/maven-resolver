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
package org.eclipse.aether.impl.scope;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;

import static java.util.Objects.requireNonNull;

/**
 * Build scope query.
 *
 * @since 2.0.0
 */
public final class BuildScopeQuery {
    public enum Mode {
        ALL,
        BY_PROJECT_PATH,
        BY_BUILD_PATH,
        SELECT,
        SINGLETON;

        private void validate(ProjectPath projectPath, BuildPath buildPath) {
            if ((this == ALL) && (projectPath != null || buildPath != null)) {
                throw new IllegalArgumentException(this.name() + " requires no parameter");
            } else if (this == BY_PROJECT_PATH && (projectPath == null || buildPath != null)) {
                throw new IllegalArgumentException(this.name() + " requires project path parameter only");
            } else if (this == BY_BUILD_PATH && (projectPath != null || buildPath == null)) {
                throw new IllegalArgumentException(this.name() + " requires build path parameter only");
            } else if ((this == SELECT || this == SINGLETON) && (projectPath == null || buildPath == null)) {
                throw new IllegalArgumentException(this.name() + " requires both parameters");
            }
        }
    }

    private final Mode mode;
    private final ProjectPath projectPath;
    private final BuildPath buildPath;

    private BuildScopeQuery(Mode mode, ProjectPath projectPath, BuildPath buildPath) {
        this.mode = requireNonNull(mode, "mode");
        mode.validate(projectPath, buildPath);
        this.projectPath = projectPath;
        this.buildPath = buildPath;
    }

    public Mode getMode() {
        return mode;
    }

    public ProjectPath getProjectPath() {
        return projectPath;
    }

    public BuildPath getBuildPath() {
        return buildPath;
    }

    @Override
    public String toString() {
        if ((mode == Mode.ALL)) {
            return mode.name();
        } else if (mode == Mode.BY_PROJECT_PATH) {
            return mode.name() + "(" + projectPath.getId() + ")";
        } else if (mode == Mode.BY_BUILD_PATH) {
            return mode.name() + "(" + buildPath.getId() + ")";
        } else {
            return mode.name() + "(" + projectPath.getId() + ", " + buildPath.getId() + ")";
        }
    }

    public static Collection<BuildScopeQuery> all() {
        return Collections.singleton(new BuildScopeQuery(Mode.ALL, null, null));
    }

    public static Collection<BuildScopeQuery> byProjectPath(ProjectPath projectPath) {
        return Collections.singleton(new BuildScopeQuery(Mode.BY_PROJECT_PATH, projectPath, null));
    }

    public static Collection<BuildScopeQuery> byBuildPath(BuildPath buildPath) {
        return Collections.singleton(new BuildScopeQuery(Mode.BY_BUILD_PATH, null, buildPath));
    }

    public static Collection<BuildScopeQuery> select(ProjectPath projectPath, BuildPath buildPath) {
        return Collections.singleton(new BuildScopeQuery(Mode.SELECT, projectPath, buildPath));
    }

    public static Collection<BuildScopeQuery> singleton(ProjectPath projectPath, BuildPath buildPath) {
        return Collections.singleton(new BuildScopeQuery(Mode.SINGLETON, projectPath, buildPath));
    }

    @SafeVarargs
    public static Collection<BuildScopeQuery> union(Collection<BuildScopeQuery>... buildScopeQueries) {
        HashSet<BuildScopeQuery> result = new HashSet<>();
        Arrays.asList(buildScopeQueries).forEach(result::addAll);
        return result;
    }
}
