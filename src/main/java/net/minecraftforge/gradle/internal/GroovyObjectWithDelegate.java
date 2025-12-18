/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.lang.GroovyObject;
import groovy.lang.GroovyObjectSupport;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.jspecify.annotations.Nullable;

abstract class GroovyObjectWithDelegate extends GroovyObjectSupport implements GroovyObject {
    static final Logger LOGGER = Logging.getLogger(MinecraftExtensionImpl.class);
    abstract @Nullable Object getDelegate();

    @Override
    public Object invokeMethod(String name, Object args) {
        LOGGER.lifecycle("invoke method " + name);
        try {
            return super.invokeMethod(name, args);
        } catch (Exception e) {
            if (this.getDelegate() == null)
                throw e;

            try {
                return InvokerHelper.invokeMethod(this.getDelegate(), name, args);
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
                throw e;
            }
        }
    }

    @Override
    public Object getProperty(String propertyName) {
        LOGGER.lifecycle("get property " + propertyName);
        try {
            return super.getProperty(propertyName);
        } catch (Exception e) {
            if (this.getDelegate() == null)
                throw e;

            try {
                return InvokerHelper.getProperty(this.getDelegate(), propertyName);
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
                throw e;
            }
        }
    }

    @Override
    public void setProperty(String propertyName, Object newValue) {
        LOGGER.lifecycle("set property " + propertyName);
        try {
            super.setProperty(propertyName, newValue);
        } catch (Exception e) {
            if (this.getDelegate() == null)
                throw e;

            try {
                InvokerHelper.setProperty(this.getDelegate(), propertyName, newValue);
            } catch (Exception suppressed) {
                e.addSuppressed(suppressed);
                throw e;
            }
        }
    }

    static class Impl extends GroovyObjectWithDelegate {
        private final @Nullable Object delegate;

        protected Impl(@Nullable Object delegate) {
            this.delegate = delegate;
        }

        @Override
        public @Nullable Object getDelegate() {
            return delegate;
        }
    }
}
