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
 * A simple logging infrastructure for diagnostic messages. The primary purpose of the
 * {@link org.eclipse.aether.spi.log.LoggerFactory} defined here is to avoid a mandatory dependency on a 3rd party
 * logging system/facade. Some applications might find the events fired by the repository system sufficient and prefer
 * a small footprint. Components that do not share this concern are free to ignore this package and directly employ
 * whatever logging system they desire.
 */
package org.eclipse.aether.spi.log;
