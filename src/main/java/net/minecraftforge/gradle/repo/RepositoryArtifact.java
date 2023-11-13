package net.minecraftforge.gradle.repo;

import org.gradle.api.artifacts.Dependency;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.tasks.SingleFileOutput;

@ApiStatus.Internal
public record RepositoryArtifact(
    Dependency dep,
    SingleFileOutput metadata,
    SingleFileOutput binary,
    SingleFileOutput source
) {
}
