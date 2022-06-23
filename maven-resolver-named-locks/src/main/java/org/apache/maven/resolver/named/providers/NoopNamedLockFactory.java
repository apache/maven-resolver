package org.apache.maven.resolver.named.providers;

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

import java.util.concurrent.TimeUnit;

import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.resolver.named.support.NamedLockFactorySupport;
import org.apache.maven.resolver.named.support.NamedLockSupport;

/**
 * A no-op lock factory, that creates no-op locks.
 */
@Singleton
@Named( NoopNamedLockFactory.NAME )
public class NoopNamedLockFactory
    extends NamedLockFactorySupport
{
  public static final String NAME = "noop";

  @Override
  protected NoopNamedLock createLock( final String name )
  {
    return new NoopNamedLock( name, this );
  }

  private static final class NoopNamedLock extends NamedLockSupport
  {
    private NoopNamedLock( final String name, final NamedLockFactorySupport factory )
    {
      super( name, factory );
    }

    @Override
    public boolean lockShared( final long time, final TimeUnit unit )
    {
      return true;
    }

    @Override
    public boolean lockExclusively( final long time, final TimeUnit unit )
    {
      return true;
    }

    @Override
    public void unlock()
    {
      // no-op
    }
  }
}
