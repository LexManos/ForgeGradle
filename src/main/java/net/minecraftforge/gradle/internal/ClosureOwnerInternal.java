/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.lang.Closure;
import groovy.transform.Generated;
import groovy.transform.NamedParam;
import groovy.transform.NamedParams;
import net.minecraftforge.gradle.ClosureOwner;
import net.minecraftforge.gradle.MinecraftMappings;
import org.gradle.api.Action;
import org.gradle.api.artifacts.DependencyArtifact;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ModuleDependencyCapabilitiesHandler;
import org.gradle.api.artifacts.ModuleIdentifier;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.MutableVersionConstraint;
import org.gradle.api.artifacts.VersionConstraint;
import org.gradle.api.artifacts.capability.CapabilitySelector;
import org.gradle.api.attributes.AttributeContainer;
import org.gradle.api.capabilities.Capability;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.reflect.HasPublicType;
import org.gradle.api.reflect.TypeOf;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

interface ClosureOwnerInternal extends ClosureOwner {
    /// Gets the owner delegate for this closure owner.
    ///
    /// The owner delegate sits on top of the [Closure][groovy.lang.Closure]'s original
    /// {@linkplain groovy.lang.Closure#getOwner() owner}, and is used primarily on top of it when the closure owner is
    /// invoked. If a member can't be found in the owner delegate, the original owner is queried instead.
    ///
    /// @return The owner delegate
    Object getOwnerDelegate();

    private static RuntimeException stub() {
        return new UnsupportedOperationException();
    }

    /*
    interface MinecraftDependency extends net.minecraftforge.gradle.MinecraftDependency {
        @Override
        default @Nullable MinecraftMappings getMappings() {
            return this.getOwnerDelegate().getMappings();
        }

        @Override
        default void mappings(String channel, String version) {
            this.getOwnerDelegate().mappings(channel, version);
        }

        @Override
        @Generated
        @SuppressWarnings("rawtypes")
        default void mappings(
            @NamedParams({
                @NamedParam(
                    type = String.class,
                    value = "channel",
                    required = true
                ),
                @NamedParam(
                    type = String.class,
                    value = "version",
                    required = true
                )
            }) Map namedArgs
        ) {
            this.getOwnerDelegate().mappings(namedArgs);
        }
    }

    interface WithAccessTransformers extends net.minecraftforge.gradle.MinecraftDependencyWithAccessTransformers {
        @Override
        default RegularFileProperty getAccessTransformer() {
            return this.getOwnerDelegate().getAccessTransformer();
        }

        @Override
        default void setAccessTransformer(String accessTransformer) {
            this.getOwnerDelegate().setAccessTransformer(accessTransformer);
        }

        @Override
        default void setAccessTransformer(boolean accessTransformer) {
            this.getOwnerDelegate().setAccessTransformer(accessTransformer);
        }
    }
    */
}
