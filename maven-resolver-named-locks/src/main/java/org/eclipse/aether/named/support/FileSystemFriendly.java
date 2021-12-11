package org.eclipse.aether.named.support;

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
 * A marker interface that mark component "file system friendly". In case of lock factory, it would mean that
 * passed in lock names MUST ADHERE to file path naming convention (and not use some special, non FS friendly
 * characters in it). Essentially, component marked with this interface expects (or uses) that "name" is an absolute
 * and valid file path.
 *
 * @apiNote Experimental interface, is not meant to be used outside of Maven Resolver codebase. May change or be
 * removed completely without any further notice.
 */
public interface FileSystemFriendly
{
}
