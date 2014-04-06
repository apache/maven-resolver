/*******************************************************************************
 * Copyright (c) 2011, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.impl;

/**
 * A ready-made Guice module that sets up bindings for all components from this library. To acquire a complete
 * repository system, clients need to bind an artifact descriptor reader, a version resolver, a version range resolver,
 * zero or more metadata generator factories, some repository connector and transporter factories to access remote
 * repositories.
 * 
 * @deprecated Use {@link org.eclipse.aether.impl.guice.AetherModule} instead.
 */
@Deprecated
public final class AetherModule
    extends org.eclipse.aether.impl.guice.AetherModule
{

}
