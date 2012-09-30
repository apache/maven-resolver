/*******************************************************************************
 * Copyright (c) 2010, 2012 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.connector.async;

import org.eclipse.aether.repository.RemoteRepository;

/* *
 */
public class DavUrlGetTest
    extends GetTest
{

    @Override
    protected RemoteRepository repository()
    {
        RemoteRepository repo = super.repository();
        return new RemoteRepository.Builder( repo ).setUrl( "dav:" + repo.getUrl() ).build();
    }

}
