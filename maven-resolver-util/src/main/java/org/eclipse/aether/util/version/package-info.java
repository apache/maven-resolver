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
 * Version scheme for parsing/comparing versions and utility classes.
 * <p>
 * Contains the "generic" scheme {@link org.eclipse.aether.util.version.GenericVersionScheme}
 * that serves the purpose of "factory" (and/or parser) for all corresponding elements (all those are package private).
 * <p>
 * On the other hand, the {@link org.eclipse.aether.util.version.UnionVersionRange} is universal implementation of
 * "unions" of various {@link org.eclipse.aether.version.VersionRange} instances.
 * <p>
 * Below is the <em>Generic Version Spec</em> described:
 * <p>
 * Version string is parsed into version according to these rules:
 * <ul>
 *     <li>The version string is parsed into segments, from left to right.</li>
 *     <li>Segments are explicitly delimited by a single {@code "." (dot)}, {@code "-" (hyphen)}, or {@code "_" (underscore)} character.</li>
 *     <li>Segments are implicitly delimited by a transition between ASCII digits and non-digits.</li>
 *     <li>Segments are classified as numeric, string, qualifiers (special case of string), and min/max.</li>
 *     <li>Numeric segments are composed of the ASCII digits 0-9. Non-ASCII digits are treated as letters.
 *     <li>Numeric segments are sorted numerically, ascending.</li>
 *     <li>Non-numeric segments may be qualifiers (predefined) or strings (non-empty letter sequence). All of them are interpreted as being case-insensitive in terms of the ROOT locale.</li>
 *     <li>Qualifier segments (strings listed below) and their sort order (ascending) are:
 *         <ul>
 *             <li>"alpha" (== "a" when immediately followed by number)</li>
 *             <li>"beta" (== "b" when immediately followed by number)</li>
 *             <li>"milestone" (== "m" when immediately followed by number)</li>
 *             <li>"pr" = "pre" = "preview" (use is discouraged)</li>
 *             <li>"rc" == "cr" (use of "cr" is discouraged, use rc instead)</li>
 *             <li>"dev" (use is discouraged)</li>
 *             <li>"snapshot"</li>
 *             <li>"final" == "ga" == "release" (use is discouraged, use no qualifier instead)</li>
 *             <li>"sp" (use of "sp" is discouraged, increment patch version instead)</li>
 *         </ul>
 *     </li>
 *     <li>String segments are sorted lexicographically and case-insensitively per ROOT locale, ascending.</li>
 *     <li>There are two special segments, {@code "min"} and {@code "max"} that represent absolute minimum and absolute maximum in comparisons. They can be used only as the trailing segment.</li>
 *     <li>As last step, trailing "zero segments" are trimmed. Similarly, "zero segments" positioned before numeric and non-numeric transitions (either explicitly or implicitly delimited) are trimmed.</li>
 *     <li>When trimming, "zero segments" are qualifiers {@code "final"}, {@code "ga"}, {@code "release"} only if being last (right-most) segment, empty string and "0" always.</li>
 *     <li>In comparison of same kind segments, the given type of segment determines comparison rules.</li>
 *     <li>In comparison of different kind of segments, following applies: {@code min < qualifier < string < numeric < max}.</li>
 *     <li>Any version can be considered to have an infinite number of invisible trailing "zero segments", for the purposes of comparison (in other words, "1" == "1.0.0.0.0.0.0.0.0....")</li>
 *     <li>It is common that a version identifier starts with numeric segment (consider this "best practice").</li>
 * </ul>
 * <p>
 * Note: this version spec does not document (or cover) many corner cases that we believe are "atypical" or not
 * used commonly. None of these are enforced, but in future implementations they probably will be. Some known examples are:
 * <ul>
 *     <li>Using "min" or "max" special segments as a non-trailing segment. This yields in "undefined" behaviour and should be avoided.</li>
 *     <li>Having a non-number as the first segment of a version. Versions are expected (but not enforced) to start with numbers.</li>
 * </ul>
 */
package org.eclipse.aether.util.version;
