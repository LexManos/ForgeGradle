/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mappings;

import org.gradle.api.Project;
import org.gradle.api.plugins.ExtensionContainer;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An extension object that holds all registered {@link MappingsProvider}s.
 * Retrieve an instance of this using {@link ExtensionContainer#getByType(Class)} from the {@link Project} instance.
 *
 */
public class MappingsExtension {
    public static final String NAME = "mappings";
    private final Map<String, MappingsProvider> providers = new ConcurrentHashMap<>();

    public MappingsExtension(Project project) {
        // Add the default providers
        addProvider(new OfficialMappings(project));
        addProvider(new MCPMappings(project));
    }

    private String channel = "official";
    private String version;

    public void setChannel(String value) {
        this.channel = value;
    }

    /**
     * Returns the channel to use, defaults to "official"
     */
    public String getCannel() {
        return this.channel;
    }

    /**
     * Sets the mapping version to use.
     *
     * @param value version
     */
    public void setVersion(String value) {
        this.version = value;
    }

    /**
     * Returns the mapping version to use, if null then we will attempt
     * to detect the Minecraft version and use that.
     */
    @Nullable
    public String getVersion() {
        return this.version;
    }

    /**
     * Returns the configured mapping version, or the default value if null.
     */
    public String getVersion(String _default) {
        return this.version == null ? _default : this.version;
    }


    /**
     * Returns an unmodifiable view of the provider map.
     *
     * @return an unmodifiable view of the provider map
     */
    public Map<String, MappingsProvider> getProviderMap() {
        return Collections.unmodifiableMap(providers);
    }

    /**
     * Retrieves a possibly-null {@link MappingsProvider} from the provider map using the {@code channel} key.
     *
     * @param channel the channel key to use for querying
     * @return a possibly-null {@link MappingsProvider} from the provider map
     */
    @Nullable
    public MappingsProvider getProvider(String channel) {
        return providers.get(channel);
    }

    /**
     * Adds a {@link MappingsProvider} to the provider map using all of its defined channels in {@link MappingsProvider#getChannels()}.
     *
     * @param provider The provider to add to the channel provider map
     * @throws IllegalArgumentException if a channel name from the provider is already registered to another provider
     */
    public void addProvider(MappingsProvider provider) {
        for (String channel : provider.getChannels()) {
            MappingsProvider previous = providers.get(channel);
            if (previous != null)
                throw new IllegalArgumentException(channel + " is already registered to " + previous + " containing channels " + previous.getChannels());
            providers.put(channel, provider);
        }
    }
}
