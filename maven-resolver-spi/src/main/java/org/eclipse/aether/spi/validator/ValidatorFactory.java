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
package org.eclipse.aether.spi.validator;

import org.eclipse.aether.RepositorySystemSession;

/**
 * A factory to create validators.
 *
 * @since 2.0.8
 */
public interface ValidatorFactory {

    /**
     * The no-op (does not validate anything) instance of validator.
     *
     * @since 2.0.22
     */
    Validator NOOP = new Validator() {};

    /**
     * Creates a new validator for the session, or {@link #NOOP} to abstain from validation. Factory is consulted
     * once per session (if cache present in session) and returned instances are cached and reused
     * across single session.
     *
     * @param session the repository system session from which to configure the validator, must not be {@code null}.
     * @return the validator to be used for the session, or {@link #NOOP} to abstain from validation, never {@code null}.
     */
    Validator newInstance(RepositorySystemSession session);
}
