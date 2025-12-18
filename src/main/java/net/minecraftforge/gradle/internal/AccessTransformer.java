/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.internal;

import groovy.lang.GroovyObject;
import groovy.lang.GroovyObjectSupport;
import net.minecraftforge.gradle.MinecraftExtensionForProjectWithAccessTransformers;
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.jspecify.annotations.Nullable;

import javax.inject.Inject;

class AccessTransformer extends KnownPlugins.KnownPlugin {
    static final String NAME = "net.minecraftforge.accesstransformers";
    static final AccessTransformer INSTANCE = new AccessTransformer();

    private AccessTransformer() {
        super(NAME,
            "accesstransformers-gradle",
            "5.0.2"
            //MinecraftExtensionForProjectWithAccessTransformers.class
            //MinecraftDependencyWithAccessTransformers.class
        );
    }

    @Override
    @Nullable
    GroovyObject getMissingExtension(Project project, @Nullable GroovyObject parent) {
        return new MissingExtension(project, parent);
    }

    @Override
    @Nullable
    GroovyObject getAppliedExtension(Project project, @Nullable GroovyObject parent) {
        return project.getObjects().newInstance(AppliedExtension.class, project);
    }

    static class MissingExtension extends GroovyObjectWithDelegate.Impl {
        private final Property<String> accessTransformers;
        private final ForgeGradleProblems problems;

        MissingExtension(Project project, @Nullable GroovyObject parent) {
            super(parent);
            this.accessTransformers = project.getObjects().property(String.class);
            this.problems = project.getObjects().newInstance(ForgeGradleProblems.class);
        }

        public Property<String> getAccessTransformers() {
            problems.reportAccessTransformersNotApplied(null);
            return accessTransformers;
        }

        public void setAccessTransformers(String accessTransformers) {
            problems.reportAccessTransformersNotApplied(null);
        }
    }

    public static class AppliedExtension extends GroovyObjectWithDelegate.Impl implements MinecraftExtensionForProjectWithAccessTransformers {
        private final Property<String> accessTransformers;

        @Inject
        public AppliedExtension(Project project) {
            super(null);
            this.accessTransformers = project.getObjects().property(String.class);
        }

        @Override
        public Property<String> getAccessTransformers() {
            return this.accessTransformers;
        }
    }
}
