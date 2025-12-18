/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.lang.GroovyObject;
import net.minecraftforge.gradle.MinecraftDependency;
import net.minecraftforge.gradle.MinecraftDependencyWithAccessTransformers;
import net.minecraftforge.gradle.MinecraftExtensionForProject;
import net.minecraftforge.gradle.MinecraftExtensionForProjectWithAccessTransformers;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.reflect.TypeOf;
import org.gradle.internal.metaobject.DynamicObjectUtil;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class KnownPlugins {
    private static final Logger LOGGER = Logging.getLogger(KnownPlugins.class);

    static final List<KnownPlugin> KNOWN = List.of(
        AccessTransformer.INSTANCE
    );

    // This needs to be all permutations of the extensions
    // I wanted to generate these dynamically at runtime (which would allow plugins to register additional things) but IntelliJ seems to require it to be built ahead of time
    interface Extension<T> extends MinecraftExtensionForProject<T> {
        TypeOf<?> TYPE = new TypeOf<Extension<?>>(){};
        interface WithAt<T> extends Extension<T>, MinecraftExtensionForProjectWithAccessTransformers {
            TypeOf<?> TYPE = new TypeOf<Extension.WithAt<?>>(){};
        }
    }
    interface Dependency extends MinecraftDependency, ExternalModuleDependency {
        interface WithAt extends Dependency, MinecraftDependencyWithAccessTransformers {}
    }

    record State(
        Project project,
        Map<String, PluginState> states
    ) {
        boolean has(String plugin) {
            var state =  states.get(plugin);
            return state != null && state.applied();
        }

        TypeOf<?> getExtensionType() {
            return TypeOf.parameterizedTypeOf(getRawExtensionType(), getDependencyType());
        }
        private TypeOf<?> getRawExtensionType() {
            if (has(AccessTransformer.NAME))
                return Extension.WithAt.TYPE;
            return Extension.TYPE;
        }

        TypeOf<?> getDependencyType() {
            if (has(AccessTransformer.NAME))
                return TypeOf.typeOf(Dependency.WithAt.class);
            return TypeOf.typeOf(Dependency.class);
        }

        Object getExtensionDelegate() {
            GroovyObject ret = null;
            for (var plugin : states.values()) {
                if (plugin.applied())
                    ret = plugin.plugin().getAppliedExtension(project, ret);
                else
                    ret = plugin.plugin().getMissingExtension(project, ret);
            }
            return ret;
        }
    }

    record PluginState(KnownPlugin plugin, boolean applied) {}

    static abstract class KnownPlugin {
        final String name;
        final @Nullable String artifact;
        final @Nullable String version;

        protected KnownPlugin(String name, @Nullable String artifact, @Nullable String version) {
            this.name = name;
            this.artifact = artifact;
            this.version = version;
        }

        @Nullable
        GroovyObject getMissingExtension(Project project, @Nullable GroovyObject parent) {
            return parent;
        }

        @Nullable
        GroovyObject getAppliedExtension(Project project, @Nullable GroovyObject parent) {
            return parent;
        }
    }

    static State find(Project project) {
        var ret = new LinkedHashMap<String, PluginState>();
        var problems = project.getObjects().newInstance(ForgeGradleProblems.class);
        for (var known : KNOWN) {
            var applied = find(known, problems, project) != null;
            ret.put(known.name, new PluginState(known, applied));
        }
        return new State(project, ret);
    }

    @Nullable
    private static Plugin<Project> find(KnownPlugin info, ForgeGradleProblems problems, Project project) {
        var plugins = project.getPlugins();
        var plugin = plugins.findPlugin(info.name);
        LOGGER.lifecycle("Plugin {} found: {}", info.name, plugin);
        if (plugin == null) {
            plugins.withId(info.name, p -> problems.warnPluginOrder(info.name));
            return null;
        }

        @SuppressWarnings("unchecked")
        var typed = (Plugin<@NonNull Project>) plugin;
        checkVersion(info, problems, typed);
        return typed;
    }

    private static void checkVersion(KnownPlugin info, ForgeGradleProblems problems, Plugin<Project> plugin) {
        if (info.version == null || info.artifact == null)
            return;

        var url = plugin.getClass().getProtectionDomain().getCodeSource().getLocation();
        if (!url.getProtocol().equals("file")) {
            LOGGER.warn("Unsupported location for plugin {}, assuming valid: {}", info.name, url);
            return;
        }

        var file = new File(url.getPath());
        if (file.isDirectory() || !file.getName().startsWith(info.artifact) || !file.getName().endsWith(".jar")) {
            LOGGER.warn("Unsupported location for plugin {}, assuming valid: {}", info.name, file.getAbsolutePath());
            return;
        }

        var detected = file.getName().substring(info.artifact.length() + 1, file.getName().length() - 4);
        var expected = new DefaultArtifactVersion(info.version);
        var actual = new DefaultArtifactVersion(detected);
        if (actual.compareTo(expected) < 0)
            throw problems.incorrectPluginVersion(info.name, detected, info.version);
    }
}
