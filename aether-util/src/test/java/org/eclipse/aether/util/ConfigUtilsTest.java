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
package org.eclipse.aether.util;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class ConfigUtilsTest
{

    Map<Object, Object> config = new HashMap<Object, Object>();

    @Test
    public void testGetObject_Default()
    {
        Object val = new Object();
        assertSame( val, ConfigUtils.getObject( config, val, "no-value" ) );
    }

    @Test
    public void testGetObject_AlternativeKeys()
    {
        Object val = new Object();
        config.put( "some-object", val );
        assertSame( val, ConfigUtils.getObject( config, null, "no-object", "some-object" ) );
    }

    @Test
    public void testGetMap_Default()
    {
        Map<?, ?> val = new HashMap<Object, Object>();
        assertSame( val, ConfigUtils.getMap( config, val, "no-value" ) );
    }

    @Test
    public void testGetMap_AlternativeKeys()
    {
        Map<?, ?> val = new HashMap<Object, Object>();
        config.put( "some-map", val );
        assertSame( val, ConfigUtils.getMap( config, null, "no-object", "some-map" ) );
    }

    @Test
    public void testGetList_Default()
    {
        List<?> val = new ArrayList<Object>();
        assertSame( val, ConfigUtils.getList( config, val, "no-value" ) );
    }

    @Test
    public void testGetList_AlternativeKeys()
    {
        List<?> val = new ArrayList<Object>();
        config.put( "some-list", val );
        assertSame( val, ConfigUtils.getList( config, null, "no-object", "some-list" ) );
    }

    @Test
    public void testGetList_CollectionConversion()
    {
        Collection<?> val = Collections.singleton( "item" );
        config.put( "some-collection", val );
        assertEquals( Arrays.asList( "item" ), ConfigUtils.getList( config, null, "some-collection" ) );
    }

    @Test
    public void testGetString_Default()
    {
        config.put( "no-string", new Object() );
        assertEquals( "default", ConfigUtils.getString( config, "default", "no-value" ) );
        assertEquals( "default", ConfigUtils.getString( config, "default", "no-string" ) );
    }

    @Test
    public void testGetString_AlternativeKeys()
    {
        config.put( "no-string", new Object() );
        config.put( "some-string", "passed" );
        assertEquals( "passed", ConfigUtils.getString( config, "default", "no-string", "some-string" ) );
    }

    @Test
    public void testGetString_BooleanConversion()
    {
        config.put( "some-string", Boolean.TRUE );
        assertEquals( "true", ConfigUtils.getString( config, "default", "some-string" ) );
        config.put( "some-string", Boolean.FALSE );
        assertEquals( "false", ConfigUtils.getString( config, "default", "some-string" ) );
    }

    @Test
    public void testGetString_NumberConversion()
    {
        config.put( "some-string", Integer.valueOf( -7 ) );
        assertEquals( "-7", ConfigUtils.getString( config, "default", "some-string" ) );
        config.put( "some-string", new Float( -1.5f ) );
        assertEquals( "-1.5", ConfigUtils.getString( config, "default", "some-string" ) );
    }

    @Test
    public void testGetBoolean_Default()
    {
        config.put( "no-boolean", new Object() );
        assertEquals( true, ConfigUtils.getBoolean( config, true, "no-value" ) );
        assertEquals( false, ConfigUtils.getBoolean( config, false, "no-value" ) );
        assertEquals( true, ConfigUtils.getBoolean( config, true, "no-boolean" ) );
        assertEquals( false, ConfigUtils.getBoolean( config, false, "no-boolean" ) );
    }

    @Test
    public void testGetBoolean_AlternativeKeys()
    {
        config.put( "no-boolean", new Object() );
        config.put( "some-boolean", true );
        assertEquals( true, ConfigUtils.getBoolean( config, false, "no-boolean", "some-boolean" ) );
        config.put( "some-boolean", false );
        assertEquals( false, ConfigUtils.getBoolean( config, true, "no-boolean", "some-boolean" ) );
    }

    @Test
    public void testGetBoolean_StringConversion()
    {
        config.put( "some-boolean", "true" );
        assertEquals( true, ConfigUtils.getBoolean( config, false, "some-boolean" ) );
        config.put( "some-boolean", "false" );
        assertEquals( false, ConfigUtils.getBoolean( config, true, "some-boolean" ) );
    }

    @Test
    public void testGetInteger_Default()
    {
        config.put( "no-integer", new Object() );
        assertEquals( -17, ConfigUtils.getInteger( config, -17, "no-value" ) );
        assertEquals( 43, ConfigUtils.getInteger( config, 43, "no-integer" ) );
    }

    @Test
    public void testGetInteger_AlternativeKeys()
    {
        config.put( "no-integer", "text" );
        config.put( "some-integer", 23 );
        assertEquals( 23, ConfigUtils.getInteger( config, 0, "no-integer", "some-integer" ) );
    }

    @Test
    public void testGetInteger_StringConversion()
    {
        config.put( "some-integer", "-123456" );
        assertEquals( -123456, ConfigUtils.getInteger( config, 0, "some-integer" ) );
    }

    @Test
    public void testGetInteger_NumberConversion()
    {
        config.put( "some-number", -123456.789 );
        assertEquals( -123456, ConfigUtils.getInteger( config, 0, "some-number" ) );
    }

    @Test
    public void testGetLong_Default()
    {
        config.put( "no-long", new Object() );
        assertEquals( -17, ConfigUtils.getLong( config, -17L, "no-value" ) );
        assertEquals( 43, ConfigUtils.getLong( config, 43L, "no-long" ) );
    }

    @Test
    public void testGetLong_AlternativeKeys()
    {
        config.put( "no-long", "text" );
        config.put( "some-long", 23 );
        assertEquals( 23, ConfigUtils.getLong( config, 0, "no-long", "some-long" ) );
    }

    @Test
    public void testGetLong_StringConversion()
    {
        config.put( "some-long", "-123456789012" );
        assertEquals( -123456789012L, ConfigUtils.getLong( config, 0, "some-long" ) );
    }

    @Test
    public void testGetLong_NumberConversion()
    {
        config.put( "some-number", -123456789012.789 );
        assertEquals( -123456789012L, ConfigUtils.getLong( config, 0, "some-number" ) );
    }

    @Test
    public void testGetFloat_Default()
    {
        config.put( "no-float", new Object() );
        assertEquals( -17.1f, ConfigUtils.getFloat( config, -17.1f, "no-value" ), 0.01f );
        assertEquals( 43.2f, ConfigUtils.getFloat( config, 43.2f, "no-float" ), 0.01f );
    }

    @Test
    public void testGetFloat_AlternativeKeys()
    {
        config.put( "no-float", "text" );
        config.put( "some-float", 12.3f );
        assertEquals( 12.3f, ConfigUtils.getFloat( config, 0, "no-float", "some-float" ), 0.01f );
    }

    @Test
    public void testGetFloat_StringConversion()
    {
        config.put( "some-float", "-12.3" );
        assertEquals( -12.3f, ConfigUtils.getFloat( config, 0, "some-float" ), 0.01f );
        config.put( "some-float", "NaN" );
        assertEquals( true, Float.isNaN( ConfigUtils.getFloat( config, 0, "some-float" ) ) );
    }

    @Test
    public void testGetFloat_NumberConversion()
    {
        config.put( "some-number", -1234 );
        assertEquals( -1234f, ConfigUtils.getFloat( config, 0, "some-number" ), 0.1f );
    }

}
