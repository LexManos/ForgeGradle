/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import net.minecraftforge.gradle.MinecraftExtension;
import net.minecraftforge.gradle.MinecraftExtensionForProject;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.attributes.Attribute;

import java.util.List;

interface MinecraftExtensionInternal extends MinecraftExtension, MinecraftMappingsContainerInternal {
    @Override
    default Attributes getAttributes() {
        return AttributesInternal.INSTANCE;
    }

    record AttributesInternal() implements Attributes {
        static AttributesInternal INSTANCE = new AttributesInternal();

        static final Attribute<String> OS = Attribute.of("net.minecraftforge.native.operatingSystem", String.class);
        static final Attribute<String> MAPPINGS_CHANNEL = Attribute.of("net.minecraftforge.mappings.channel", String.class);
        static final Attribute<String> MAPPINGS_VERSION = Attribute.of("net.minecraftforge.mappings.version", String.class);

        @Override
        public Attribute<String> getOs() {
            return OS;
        }

        @Override
        public Attribute<String> getMappingsChannel() {
            return MAPPINGS_CHANNEL;
        }

        @Override
        public Attribute<String> getMappingsVersion() {
            return MAPPINGS_VERSION;
        }
    }

    interface ForProject<T> extends MinecraftExtensionForProject<T>, MinecraftExtensionInternal {
        List<? extends MavenArtifactRepository> getRepositories();
    }
}
