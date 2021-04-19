package org.eclipse.aether.named.redisson;

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

import org.eclipse.aether.named.support.NamedLockFactorySupport;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Support class for factories using {@link RedissonClient}.
 */
public abstract class RedissonNamedLockFactorySupport
    extends NamedLockFactorySupport
{
    protected static final String NAME_PREFIX = "maven:resolver:";

    private static final String DEFAULT_CONFIG_FILE_NAME = "maven-resolver-redisson.yaml";

    private static final String DEFAULT_REDIS_ADDRESS = "redis://localhost:6379";

    private static final String DEFAULT_CLIENT_NAME = "maven-resolver";

    private static final String CONFIG_PROP_CONFIG_FILE = "aether.syncContext.named.redisson.configFile";

    protected final RedissonClient redissonClient;

    public RedissonNamedLockFactorySupport()
    {
        this.redissonClient = createRedissonClient();
    }

    @Override
    public void shutdown()
    {
        redissonClient.shutdown();
    }

    private RedissonClient createRedissonClient()
    {
        Path configFilePath = null;

        String configFile = System.getProperty( CONFIG_PROP_CONFIG_FILE );
        if ( configFile != null && !configFile.isEmpty() )
        {
            configFilePath = Paths.get( configFile );
            if ( Files.notExists( configFilePath ) )
            {
                throw new IllegalArgumentException( "The specified Redisson config file does not exist: "
                        + configFilePath );
            }
        }

        if ( configFilePath == null )
        {
            String mavenConf = System.getProperty( "maven.conf" );
            if ( mavenConf != null && !mavenConf.isEmpty() )
            {
                configFilePath = Paths.get( mavenConf, DEFAULT_CONFIG_FILE_NAME );
                if ( Files.notExists( configFilePath ) )
                {
                    configFilePath = null;
                }
            }
        }

        Config config;

        if ( configFilePath != null )
        {
            logger.trace( "Reading Redisson config file from '{}'", configFilePath );
            try ( InputStream is = Files.newInputStream( configFilePath ) )
            {
                config = Config.fromYAML( is );
            }
            catch ( IOException e )
            {
                throw new IllegalStateException( "Failed to read Redisson config file: " + configFilePath, e );
            }
        }
        else
        {
            config = new Config();
            config.useSingleServer()
                    .setAddress( DEFAULT_REDIS_ADDRESS )
                    .setClientName( DEFAULT_CLIENT_NAME );
        }

        RedissonClient redissonClient = Redisson.create( config );
        logger.trace( "Created Redisson client with id '{}'", redissonClient.getId() );

        return redissonClient;
    }
}
