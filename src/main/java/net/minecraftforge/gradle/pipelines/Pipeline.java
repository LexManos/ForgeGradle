package net.minecraftforge.gradle.pipelines;

import org.gradle.api.artifacts.Dependency;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.repo.RepositoryManager;

@ApiStatus.Internal
public interface Pipeline {
    Dependency handleDependency(RepositoryManager manager, Dependency dependency);
}
