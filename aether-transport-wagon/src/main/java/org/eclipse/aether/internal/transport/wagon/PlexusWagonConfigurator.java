/*******************************************************************************
 * Copyright (c) 2010, 2014 Sonatype, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype, Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.aether.internal.transport.wagon;

import org.apache.maven.wagon.Wagon;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.configurator.AbstractComponentConfigurator;
import org.codehaus.plexus.component.configurator.ComponentConfigurationException;
import org.codehaus.plexus.component.configurator.ConfigurationListener;
import org.codehaus.plexus.component.configurator.converters.composite.ObjectWithFieldsConverter;
import org.codehaus.plexus.component.configurator.expression.ExpressionEvaluator;
import org.codehaus.plexus.configuration.PlexusConfiguration;
import org.codehaus.plexus.configuration.xml.XmlPlexusConfiguration;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.eclipse.aether.transport.wagon.WagonConfigurator;

/**
 * A wagon configurator based on the Plexus component configuration framework.
 */
@Component( role = WagonConfigurator.class, hint = "plexus" )
public class PlexusWagonConfigurator
    implements WagonConfigurator
{

    @Requirement
    private PlexusContainer container;

    /**
     * Creates an uninitialized wagon configurator.
     * 
     * @noreference This constructor only supports the Plexus IoC container and should not be called directly by
     *              clients.
     */
    public PlexusWagonConfigurator()
    {
        // enables no-arg constructor
    }

    /**
     * Creates a wagon configurator using the specified Plexus container.
     * 
     * @param container The Plexus container instance to use, must not be {@code null}.
     */
    public PlexusWagonConfigurator( PlexusContainer container )
    {
        if ( container == null )
        {
            throw new IllegalArgumentException( "plexus container has not been specified" );
        }
        this.container = container;
    }

    public void configure( Wagon wagon, Object configuration )
        throws Exception
    {
        PlexusConfiguration config = null;
        if ( configuration instanceof PlexusConfiguration )
        {
            config = (PlexusConfiguration) configuration;
        }
        else if ( configuration instanceof Xpp3Dom )
        {
            config = new XmlPlexusConfiguration( (Xpp3Dom) configuration );
        }
        else if ( configuration == null )
        {
            return;
        }
        else
        {
            throw new IllegalArgumentException( "Unexpected configuration type: " + configuration.getClass().getName() );
        }

        WagonComponentConfigurator configurator = new WagonComponentConfigurator();

        configurator.configureComponent( wagon, config, container.getContainerRealm() );
    }

    static class WagonComponentConfigurator
        extends AbstractComponentConfigurator
    {

        @Override
        public void configureComponent( Object component, PlexusConfiguration configuration,
                                        ExpressionEvaluator expressionEvaluator, ClassRealm containerRealm,
                                        ConfigurationListener listener )
            throws ComponentConfigurationException
        {
            ObjectWithFieldsConverter converter = new ObjectWithFieldsConverter();

            converter.processConfiguration( converterLookup, component, containerRealm, configuration,
                                            expressionEvaluator, listener );
        }

    }

}
