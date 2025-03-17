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

/**
 * Validator SPI.
 * <p>
 * This package provides callback extension points, that are invoked
 * from Repository System, that is "main entry point" to Resolver.
 * Validator is invoked before artifact would be resolved, installed or deployed. Given
 * Resolver treats all coordinate elements as opaque strings, this extension
 * provides ability for integrating application for early detection of any
 * unwanted operation or bug, like "leaks" of un-interpolated artifacts
 * asked to be resolved/installed/deployed. Resolver itself have no
 * notion of "interpolation" nor "placeholders", again, it handles
 * all received coordinates as opaque string and uses them to build
 * resource URIs according to layout, but still, it is 100% that
 * un-interpolated value will result in "no artifact found" error
 * in case of resolution, but it may be and usually is due user
 * error like having a typo in some property in POM for example.
 *
 * @since 2.0.8
 */
package org.eclipse.aether.spi.validator;
