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
package org.apache.maven.buildcache;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.Strings;
import org.apache.maven.buildcache.xml.CacheConfig;
import org.apache.maven.buildcache.xml.build.CompletedExecution;
import org.apache.maven.buildcache.xml.config.PropertyName;
import org.apache.maven.buildcache.xml.config.TrackedProperty;
import org.apache.maven.execution.MojoExecutionEvent;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.descriptor.Parameter;
import org.codehaus.plexus.util.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Captures configured mojo parameters before the mojo can mutate them during execution. */
final class MojoPropertySnapshot {

    private static final Logger LOGGER = LoggerFactory.getLogger(MojoPropertySnapshot.class);

    private MojoPropertySnapshot() {}

    static CompletedExecution capture(CacheConfig cacheConfig, MojoExecutionEvent event) {
        MojoExecution mojoExecution = event.getExecution();
        CompletedExecution execution = new CompletedExecution();
        execution.setExecutionKey(CacheUtils.mojoExecutionKey(mojoExecution));
        execution.setMojoClassName(mojoExecution.getMojoDescriptor().getImplementation());

        boolean logAll = cacheConfig.isLogAllProperties(mojoExecution);
        List<TrackedProperty> trackedProperties = cacheConfig.getTrackedProperties(mojoExecution);
        List<PropertyName> noLogProperties = cacheConfig.getNologProperties(mojoExecution);
        List<PropertyName> forceLogProperties = cacheConfig.getLoggedProperties(mojoExecution);
        Object mojo = event.getMojo();
        Path baseDirPath = event.getProject().getBasedir().toPath();

        for (Parameter parameter : mojoExecution.getMojoDescriptor().getParameters()) {
            if (!parameter.isEditable()) {
                continue;
            }
            String propertyName = parameter.getName();
            boolean tracked = isTracked(propertyName, trackedProperties);
            if (!tracked && isExcluded(propertyName, logAll, noLogProperties, forceLogProperties)) {
                continue;
            }

            try {
                Object value = readProperty(propertyName, mojo);
                if (value != null || hasProperty(propertyName, mojo)) {
                    // addProperty converts values to strings now, before mutable lists can be changed by the mojo.
                    CacheUtils.addProperty(execution, propertyName, value, baseDirPath, tracked);
                } else {
                    LOGGER.warn("Cannot find a Mojo parameter '{}' to read for Mojo {}.", propertyName, mojoExecution);
                }
            } catch (IllegalAccessException | InvocationTargetException e) {
                LOGGER.info("Cannot get property {} value from {}: {}", propertyName, mojo, e.getMessage());
                if (tracked) {
                    throw new IllegalArgumentException(
                            "Property configured in cache introspection config for " + mojo + " is not accessible: "
                                    + propertyName,
                            e);
                }
            }
        }

        for (TrackedProperty trackedProperty : trackedProperties) {
            if (trackedProperty.getExpression() != null) {
                String propertyName = trackedProperty.getPropertyName();
                if (!isExcluded(propertyName, logAll, noLogProperties, forceLogProperties)) {
                    Object value = CacheUtils.interpolateExpression(
                            trackedProperty.getExpression(), event.getSession(), mojoExecution);
                    CacheUtils.addProperty(execution, propertyName, value, baseDirPath, true);
                }
            }
        }
        return execution;
    }

    static Object readProperty(String propertyName, Object mojo)
            throws IllegalAccessException, InvocationTargetException {
        if (ReflectionUtils.getFieldByNameIncludingSuperclasses(propertyName, mojo.getClass()) != null) {
            return ReflectionUtils.getValueIncludingSuperclasses(propertyName, mojo);
        }
        Method getter = getGetter(propertyName, mojo.getClass());
        return getter != null ? getter.invoke(mojo) : null;
    }

    private static boolean hasProperty(String propertyName, Object mojo) {
        return ReflectionUtils.getFieldByNameIncludingSuperclasses(propertyName, mojo.getClass()) != null
                || getGetter(propertyName, mojo.getClass()) != null;
    }

    private static Method getGetter(String fieldName, Class<?> clazz) {
        String getterMethodName = "get" + org.codehaus.plexus.util.StringUtils.capitalizeFirstLetter(fieldName);
        for (Method method : clazz.getMethods()) {
            if (method.getName().equals(getterMethodName)
                    && !method.getReturnType().equals(Void.TYPE)
                    && method.getParameterCount() == 0) {
                return method;
            }
        }
        return null;
    }

    private static boolean isExcluded(
            String propertyName,
            boolean logAll,
            List<PropertyName> excludedProperties,
            List<PropertyName> forceLogProperties) {
        if (!forceLogProperties.isEmpty()) {
            for (PropertyName logProperty : forceLogProperties) {
                if (Strings.CS.equals(propertyName, logProperty.getPropertyName())) {
                    return false;
                }
            }
            return true;
        }
        if (!excludedProperties.isEmpty()) {
            for (PropertyName excludedProperty : excludedProperties) {
                if (Strings.CS.equals(propertyName, excludedProperty.getPropertyName())) {
                    return true;
                }
            }
            return false;
        }
        return !logAll;
    }

    private static boolean isTracked(String propertyName, List<TrackedProperty> trackedProperties) {
        for (TrackedProperty trackedProperty : trackedProperties) {
            if (Strings.CS.equals(propertyName, trackedProperty.getPropertyName())) {
                return true;
            }
        }
        return false;
    }
}
