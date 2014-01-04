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
 * The provisional interfaces defining the various sub components that implement the repository system. Aether Core
 * provides stock implementations for most of these components but not all. To obtain a complete/runnable repository
 * system, the application needs to provide implementations of the following component contracts:
 * {@link org.eclipse.aether.impl.ArtifactDescriptorReader}, {@link org.eclipse.aether.impl.VersionResolver},
 * {@link org.eclipse.aether.impl.VersionRangeResolver} and potentially
 * {@link org.eclipse.aether.impl.MetadataGeneratorFactory}. Said components basically define the file format of the
 * metadata that is used to reason about an artifact's dependencies and available versions.
 */
package org.eclipse.aether.impl;

