package org.apache.maven.resolver.examples.sisu;

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

import javax.inject.Inject;

import com.google.inject.Binder;
import com.google.inject.Module;
import org.eclipse.sisu.bean.LifecycleModule;
import org.eclipse.sisu.inject.MutableBeanLocator;
import org.eclipse.sisu.wire.ParameterKeys;

/**
 * Sisu module for demo snippets.
 */
public class SisuRepositorySystemDemoModule implements Module
{
    @Override
    public void configure( final Binder binder )
    {
        binder.install( new LifecycleModule() );
        // NOTE: this module below is needed due following:, while maven-resolver-provider:3.5.4 DOES have SISU index,
        // the also needed module maven-model-builder:3.5.4 DOES NOT have SISU index (has plexus/components.xml).
        // To keep things simple, and not bring in deprecated Plexus, we rather install custom Guicemodule
        // (MavenDemoModule) to provide the "missing components" (while resolver components are discovered by SISU)
        // making resolver complete.
        binder.install( new MavenDemoModule() );
        binder.bind( ParameterKeys.PROPERTIES ).toInstance( System.getProperties() );
        binder.bind( ShutdownThread.class ).asEagerSingleton();
    }

    static final class ShutdownThread
        extends Thread
    {
        private final MutableBeanLocator locator;

        @Inject
        ShutdownThread( final MutableBeanLocator locator )
        {
            this.locator = locator;
            Runtime.getRuntime().addShutdownHook( this );
        }

        @Override
        public void run()
        {
            locator.clear();
        }
    }
}
