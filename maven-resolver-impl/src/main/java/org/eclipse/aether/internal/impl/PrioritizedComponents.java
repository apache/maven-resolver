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
package org.eclipse.aether.internal.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.eclipse.aether.ConfigurationProperties;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.ConfigUtils;

/**
 * Helps to sort pluggable components by their priority.
 */
final class PrioritizedComponents<T> {
    /**
     * Reuses or creates and stores (if session data does not contain yet) prioritized components under this key into
     * given session. Same session is used to configure prioritized components.
     *
     * @since TBD
     */
    @SuppressWarnings( "unchecked" )
    public static <C> PrioritizedComponents<C> reuseOrCreate(RepositorySystemSession session, String key, Collection<C> components, Function<C,Float> priorityFunction) {
        return (PrioritizedComponents<C>) session.getData().computeIfAbsent(key, () -> {
            PrioritizedComponents<C> newInstance = new PrioritizedComponents<>(session);
            components.forEach(c -> newInstance.add(c, priorityFunction.apply(c)));
            return components;
        });
    }

    private static final String FACTORY_SUFFIX = "Factory";

    private final Map<?, ?> configProps;

    private final boolean useInsertionOrder;

    private final List<PrioritizedComponent<T>> components;

    private int firstDisabled;

    PrioritizedComponents(RepositorySystemSession session) {
        this(session.getConfigProperties());
    }

    PrioritizedComponents(Map<?, ?> configurationProperties) {
        configProps = configurationProperties;
        useInsertionOrder = ConfigUtils.getBoolean(
                configProps,
                ConfigurationProperties.DEFAULT_IMPLICIT_PRIORITIES,
                ConfigurationProperties.IMPLICIT_PRIORITIES);
        components = new ArrayList<>();
        firstDisabled = 0;
    }

    public void add(T component, float priority) {
        Class<?> type = getImplClass(component);
        int index = components.size();
        priority = useInsertionOrder ? -index : ConfigUtils.getFloat(configProps, priority, getConfigKeys(type));
        PrioritizedComponent<T> pc = new PrioritizedComponent<>(component, type, priority, index);

        if (!useInsertionOrder) {
            index = Collections.binarySearch(components, pc);
            if (index < 0) {
                index = -index - 1;
            } else {
                index++;
            }
        }
        components.add(index, pc);

        if (index <= firstDisabled && !pc.isDisabled()) {
            firstDisabled++;
        }
    }

    private static Class<?> getImplClass(Object component) {
        Class<?> type = component.getClass();
        // detect and ignore CGLIB-based proxy classes employed by Guice for AOP (cf. BytecodeGen.newEnhancer)
        int idx = type.getName().indexOf("$$");
        if (idx >= 0) {
            Class<?> base = type.getSuperclass();
            if (base != null && idx == base.getName().length() && type.getName().startsWith(base.getName())) {
                type = base;
            }
        }
        return type;
    }

    static String[] getConfigKeys(Class<?> type) {
        List<String> keys = new ArrayList<>();
        keys.add(ConfigurationProperties.PREFIX_PRIORITY + type.getName());
        String sn = type.getSimpleName();
        keys.add(ConfigurationProperties.PREFIX_PRIORITY + sn);
        if (sn.endsWith(FACTORY_SUFFIX)) {
            keys.add(ConfigurationProperties.PREFIX_PRIORITY + sn.substring(0, sn.length() - FACTORY_SUFFIX.length()));
        }
        return keys.toArray(new String[0]);
    }

    public boolean isEmpty() {
        return components.isEmpty();
    }

    public List<PrioritizedComponent<T>> getAll() {
        return components;
    }

    public List<PrioritizedComponent<T>> getEnabled() {
        return components.subList(0, firstDisabled);
    }

    public void list(StringBuilder buffer) {
        int i = 0;
        for (PrioritizedComponent<?> component : components) {
            if (i++ > 0) {
                buffer.append(", ");
            }
            buffer.append(component.getType().getSimpleName());
            if (component.isDisabled()) {
                buffer.append(" (disabled)");
            }
        }
    }

    @Override
    public String toString() {
        return components.toString();
    }
}
