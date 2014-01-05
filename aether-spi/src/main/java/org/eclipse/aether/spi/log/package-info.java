/*******************************************************************************
 * Copyright (c) 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
/**
 * A simple logging infrastructure for diagnostic messages. The primary purpose of the
 * {@link org.eclipse.aether.spi.log.LoggerFactory} defined here is to avoid a mandatory dependency on a 3rd party
 * logging system/facade. Some applications might find the events fired by the repository system sufficient and prefer
 * a small footprint. Components that do not share this concern are free to ignore this package and directly employ
 * whatever logging system they desire. 
 */
package org.eclipse.aether.spi.log;

