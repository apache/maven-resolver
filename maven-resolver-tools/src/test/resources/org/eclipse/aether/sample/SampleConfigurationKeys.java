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
package org.eclipse.aether.sample;

/**
 * Sample source declaring configuration keys of type {@link Boolean}, {@link String} and a custom enum, using the same
 * Javadoc block tags that {@code ConfigurationCollectorDoclet} extracts. Used as a fixture by the doclet test.
 */
public final class SampleConfigurationKeys {

    /**
     * A boolean flag.
     *
     * @since 1.2.3
     * @configurationSource {@link System#getProperty(String,String)}
     * @configurationType {@link java.lang.Boolean}
     * @configurationDefaultValue {@link #DEFAULT_BOOL}
     * @configurationRepoIdSuffix No
     */
    public static final String BOOL_KEY = "sample.bool";

    public static final boolean DEFAULT_BOOL = true;

    /**
     * A string value.
     *
     * @configurationSource {@link System#getProperty(String,String)}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_STRING}
     * @configurationRepoIdSuffix Yes
     */
    public static final String STRING_KEY = "sample.string";

    public static final String DEFAULT_STRING = "hello";

    /**
     * An enum value. The type is a custom enum with a default value declared as a variable referencing an enum value.
     *
     * @configurationSource {@link System#getProperty(String,String)}
     * @configurationType {@link SampleEnum}
     * @configurationDefaultValue {@link #DEFAULT_ENUM}
     */
    public static final String ENUM_KEY = "sample.enum";

    public static final SampleEnum DEFAULT_ENUM = SampleEnum.VALUE_A;

    public enum SampleEnum {
        VALUE_A,
        VALUE_B
    }

    /**
     * An enum value. The type is a custom enum with a default value referencing the enum value directly.
     *
     * @configurationSource {@link System#getProperty(String,String)}
     * @configurationType {@link SampleEnum}
     * @configurationDefaultValue {@link SampleEnum#VALUE_B}
     */
    public static final String ENUM2_KEY = "sample.enum2";

    private SampleConfigurationKeys() {}
}
