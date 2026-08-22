/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
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
     * A string value with some inline tags.
     * Value {@value #DEFAULT_STRING} is the default.
     * {@systemProperty some.property} is used.
     * {@code This text is code.}
     * {@literal This text is literal.}
     * {@link java.lang.String} is the type.
     * {@link String#valueOf(int)} is an external method.
     * {@link java.base} is an external module.
     * {@link java.util.List} is not available in the configured external Javadoc.
     * See JDK bug
     * <a href="https://bugs.openjdk.org/browse/JDK-8225647">JDK-8225647</a> for details.
     *
     * @configurationSource {@link System#getProperty(String,String)}
     * @configurationType {@link java.lang.String}
     * @configurationDefaultValue {@link #DEFAULT_STRING}
     * @configurationRepoIdSuffix Yes
     */
    public static final String STRING_KEY = "sample.string";

    public static final String DEFAULT_STRING = "hello";

    /**
     * An enum value. See {@link #STRING_KEY}. Also see field {@link SampleType#VALUE}, method
     * {@link SampleType#convert(String)}, constructor {@link SampleType#SampleType(String)}, type {@link SampleType},
     * package {@link org.eclipse.aether.sample}, and unavailable member {@link SampleType#hidden()}.
     * The type is a custom enum with a default value declared as a variable referencing an enum value.
     *
     * @configurationSource {@link System#getProperty(String,String)}
     * @configurationType {@link SampleEnum}
     * @configurationDefaultValue {@link #DEFAULT_ENUM}
     * @deprecated Use {@link #ENUM2_KEY} instead
     */
    @Deprecated()
    public static final String ENUM_KEY = "sample.enum";

    public static final SampleEnum DEFAULT_ENUM = SampleEnum.VALUE_A;

    public enum SampleEnum {
        VALUE_A,
        VALUE_B
    }

    public static final class SampleType {
        public static final String VALUE = "value";

        public SampleType(String value) {}

        public String convert(String value) {
            return value;
        }

        private void hidden() {}
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
