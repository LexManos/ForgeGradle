package net.minecraftforge.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

import net.minecraftforge.gradle.mappings.MappingsExtension;

public class ForgeGradlePlugin implements Plugin<Project> {
    @Override
    public void apply(Project target) {
        var mappings = target.getExtensions().create(MappingsExtension.NAME, MappingsExtension.class, target);
        target.getExtensions().create(ForgeGradleExtension.NAME, ForgeGradleExtension.class, target, mappings);
    }
}
