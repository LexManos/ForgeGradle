/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mappings;

import com.google.common.collect.ImmutableSet;

import net.minecraftforge.gradle.tasks.DownloadMavenArtifact;
import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.util.FileHelper;
import net.minecraftforge.gradle.util.Util;

import org.gradle.api.Project;
import org.jetbrains.annotations.ApiStatus;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@ApiStatus.Internal
class MCPMappings implements MappingsProvider {
    private record Key(String channel, String version) {}
    private final Project project;
    private final FileHelper local;
    private final Map<Key, DownloadMavenArtifact> tasks = new HashMap<>();

    public MCPMappings(Project project) {
        this.project = project;
        this.local = new FileHelper(project, "_mappings", "mcp");
    }

    @Override
    public Set<String> getChannels() {
        return ImmutableSet.of("snapshot", "snapshot_nodoc", "stable", "stable_nodoc");
    }

    @Override
    public SingleFileOutput getMappings(String channel, String version, SingleFileOutput obfToIntermediate) {
        var key = new Key(channel, version);
        var ret = tasks.get(key);
        if (ret != null)
            return ret;

        var task = project.getTasks().create("_mcp_" + channel + "_" + Util.sanitizeTaskName(version) + "_mappings", DownloadMavenArtifact.class);
        task.setGroup("Forge Gradle Internal - MCP Mappings");
        task.getArtifact().convention("de.oceanlabs.mcp:mcp_" + channel + ":" + version + "@zip");
        task.getOutput().convention(local.file(channel + "_" + version + ".zip"));
        tasks.put(key, task);
        return task;
    }
}
