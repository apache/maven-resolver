/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.eclipse.aether.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class ConfigUtilsTest {

    Map<Object, Object> config = new HashMap<>();

    @Test
    public void testGetObjectDefault() {
        Object val = new Object();
        assertSame(val, ConfigUtils.getObject(config, val, "no-value"));
    }

    @Test
    public void testGetObjectAlternativeKeys() {
        Object val = new Object();
        config.put("some-object", val);
        assertSame(val, ConfigUtils.getObject(config, null, "no-object", "some-object"));
    }

    @Test
    public void testGetMapDefault() {
        Map<?, ?> val = new HashMap<Object, Object>();
        assertSame(val, ConfigUtils.getMap(config, val, "no-value"));
    }

    @Test
    public void testGetMapAlternativeKeys() {
        Map<?, ?> val = new HashMap<Object, Object>();
        config.put("some-map", val);
        assertSame(val, ConfigUtils.getMap(config, null, "no-object", "some-map"));
    }

    @Test
    public void testGetListDefault() {
        List<?> val = new ArrayList<Object>();
        assertSame(val, ConfigUtils.getList(config, val, "no-value"));
    }

    @Test
    public void testGetListAlternativeKeys() {
        List<?> val = new ArrayList<Object>();
        config.put("some-list", val);
        assertSame(val, ConfigUtils.getList(config, null, "no-object", "some-list"));
    }

    @Test
    public void testGetListCollectionConversion() {
        Collection<?> val = Collections.singleton("item");
        config.put("some-collection", val);
        assertEquals(Arrays.asList("item"), ConfigUtils.getList(config, null, "some-collection"));
    }

    @Test
    public void testGetStringDefault() {
        config.put("no-string", new Object());
        assertEquals("default", ConfigUtils.getString(config, "default", "no-value"));
        assertEquals("default", ConfigUtils.getString(config, "default", "no-string"));
    }

    @Test
    public void testGetStringAlternativeKeys() {
        config.put("no-string", new Object());
        config.put("some-string", "passed");
        assertEquals("passed", ConfigUtils.getString(config, "default", "no-string", "some-string"));
    }

    @Test
    public void testGetBooleanDefault() {
        config.put("no-boolean", new Object());
        assertTrue(ConfigUtils.getBoolean(config, true, "no-value"));
        assertFalse(ConfigUtils.getBoolean(config, false, "no-value"));
        assertTrue(ConfigUtils.getBoolean(config, true, "no-boolean"));
        assertFalse(ConfigUtils.getBoolean(config, false, "no-boolean"));
    }

    @Test
    public void testGetBooleanAlternativeKeys() {
        config.put("no-boolean", new Object());
        config.put("some-boolean", true);
        assertTrue(ConfigUtils.getBoolean(config, false, "no-boolean", "some-boolean"));
        config.put("some-boolean", false);
        assertFalse(ConfigUtils.getBoolean(config, true, "no-boolean", "some-boolean"));
    }

    @Test
    public void testGetBooleanStringConversion() {
        config.put("some-boolean", "true");
        assertTrue(ConfigUtils.getBoolean(config, false, "some-boolean"));
        config.put("some-boolean", "false");
        assertFalse(ConfigUtils.getBoolean(config, true, "some-boolean"));
    }

    @Test
    public void testGetIntegerDefault() {
        config.put("no-integer", new Object());
        assertEquals(-17, ConfigUtils.getInteger(config, -17, "no-value"));
        assertEquals(43, ConfigUtils.getInteger(config, 43, "no-integer"));
    }

    @Test
    public void testGetIntegerAlternativeKeys() {
        config.put("no-integer", "text");
        config.put("some-integer", 23);
        assertEquals(23, ConfigUtils.getInteger(config, 0, "no-integer", "some-integer"));
    }

    @Test
    public void testGetIntegerStringConversion() {
        config.put("some-integer", "-123456");
        assertEquals(-123456, ConfigUtils.getInteger(config, 0, "some-integer"));
    }

    @Test
    public void testGetIntegerNumberConversion() {
        config.put("some-number", -123456.789);
        assertEquals(-123456, ConfigUtils.getInteger(config, 0, "some-number"));
    }

    @Test
    public void testGetLongDefault() {
        config.put("no-long", new Object());
        assertEquals(-17L, ConfigUtils.getLong(config, -17L, "no-value"));
        assertEquals(43L, ConfigUtils.getLong(config, 43L, "no-long"));
    }

    @Test
    public void testGetLongAlternativeKeys() {
        config.put("no-long", "text");
        config.put("some-long", 23L);
        assertEquals(23L, ConfigUtils.getLong(config, 0, "no-long", "some-long"));
    }

    @Test
    public void testGetLongStringConversion() {
        config.put("some-long", "-123456789012");
        assertEquals(-123456789012L, ConfigUtils.getLong(config, 0, "some-long"));
    }

    @Test
    public void testGetLongNumberConversion() {
        config.put("some-number", -123456789012.789);
        assertEquals(-123456789012L, ConfigUtils.getLong(config, 0, "some-number"));
    }

    @Test
    public void testGetFloatDefault() {
        config.put("no-float", new Object());
        assertEquals(-17.1f, ConfigUtils.getFloat(config, -17.1f, "no-value"), 0.01f);
        assertEquals(43.2f, ConfigUtils.getFloat(config, 43.2f, "no-float"), 0.01f);
    }

    @Test
    public void testGetFloatAlternativeKeys() {
        config.put("no-float", "text");
        config.put("some-float", 12.3f);
        assertEquals(12.3f, ConfigUtils.getFloat(config, 0, "no-float", "some-float"), 0.01f);
    }

    @Test
    public void testGetFloatStringConversion() {
        config.put("some-float", "-12.3");
        assertEquals(-12.3f, ConfigUtils.getFloat(config, 0, "some-float"), 0.01f);
        config.put("some-float", "NaN");
        assertTrue(Float.isNaN(ConfigUtils.getFloat(config, 0, "some-float")));
    }

    @Test
    public void testGetFloatNumberConversion() {
        config.put("some-number", -1234f);
        assertEquals(-1234f, ConfigUtils.getFloat(config, 0, "some-number"), 0.1f);
    }
}
