// CHECKSTYLE_OFF: RegexpHeader
/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/**
 * Ready-to-use version scheme for parsing/comparing versions and utility classes.
 * <p>
 * Contains the "generic" scheme {@link org.eclipse.aether.util.version.GenericVersionScheme}
 * that serves the purpose of "factory" (and/or parser) for all corresponding elements (all those are package private).
 * <p>
 * On the other hand, the {@link org.eclipse.aether.util.version.UnionVersionRange} is universal implementation of
 * "unions" of various {@link org.eclipse.aether.version.VersionRange} instances.
 *
 * <h1>Generic Version Spec</h1>
 * Version string is parsed into version according to these rules below:
 * <ul>
 *     <li>The version string is parsed into segments, from left to right. As last step, trailing "zero segments" are trimmed.</li>
 *     <li>Segments are explicitly delimited by single {@code "." (dot)}, {@code "-" (hyphen)} or {@code "_" (underscore)} character.</li>
 *     <li>Segments are implicitly delimited by transition between digits and non-digits.</li>
 *     <li>Numeric segments are sorted numerically, ascending.</li>
 *     <li>Non-numeric segments may be qualifiers (predefined) or "strings" (non-empty letter sequence). All of them are interpreted as being case-insensitive in terms of the ROOT locale.</li>
 *     <li>String segments (strings that are not recognized qualifiers) are sorted lexicographically, per ROOT locale, ascending.</li>
 *     <li>Qualifier segments (strings that are listed below) and their sort order (ascending) are:
 *         <ul>
 *             <li>"alpha" (== "a" when immediately followed by number)</li>
 *             <li>"beta" (== "b" when immediately followed by number)</li>
 *             <li>"milestone" (== "m" when immediately followed by number)</li>
 *             <li>"rc" == "cr" (use of "cr" is discouraged)</li>
 *             <li>"snapshot"</li>
 *             <li>"ga" == "final" == "release" (== empty string == "0") are "zero segments"</li>
 *             <li>"sp"</li>
 *         </ul>
 *     </li>
 *     <li>A "zero segment" separator also exists between any two segments when one of them is numeric segment (including 0) and the other is non-numeric segment (including qualifiers).</li>
 *     <li>It is common that a version identifier starts with numeric segment (consider this "best practice").</li>
 *     <li>Any version can be considered to have an infinite number of invisible trailing "zero segments", for the purposes of comparison (in other words, "1" == "1.0.0.0.0.0.0.0.0....")</li>
 * </ul>
 * <p>
 *
 */
package org.eclipse.aether.util.version;
