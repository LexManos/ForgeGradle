package net.minecraftforge.gradle.mappings;

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import net.minecraftforge.gradle.tasks.SingleFileOutput;

public interface MappingsProvider {
    @NotNull
    Set<String> getChannels();

    @Nullable
    SingleFileOutput getMappings(String channel, String version, SingleFileOutput obfToIntermediate);
}
