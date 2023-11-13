package net.minecraftforge.gradle.repo;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.IvyArtifactRepository;
import org.gradle.api.tasks.compile.AbstractCompile;
import org.gradle.api.tasks.diagnostics.DependencyReportTask;
import org.gradle.plugins.ide.eclipse.GenerateEclipseClasspath;
import org.gradle.plugins.ide.idea.GenerateIdeaModule;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.tasks.CopySingleFile;
import net.minecraftforge.gradle.tasks.CreateIvyMetadata;
import net.minecraftforge.gradle.util.Artifact;
import net.minecraftforge.gradle.util.Util;

/**
 * This is the main class that manages the fake repositories that we will generate our
 * artifacts into. Right now I am focused on just using a local repository that is stored
 * in the local build folder. However in the future to help caching and reuse I
 * want to introduce a global repository. For things that do not take per-project configurations
 * into account.
 *
 * There are two main tasks that are added by this, one to build the binary dependencies needed for
 * compiling, and one to build the source dependencies that will make life easier when debugger.
 * This way we can skip a lot of work when using CI servers as they will never give a crap about
 * sources.
 *
 */
@ApiStatus.Internal
public class RepositoryManager {
    private final List<RepositoryArtifact> artifacts = new ArrayList<>();
    private final Project target;
    private final Task buildBinaryDependencies;
    private final Task buildSourceDependencies;
    private final File baseFolder;
    private final Set<Task> blacklist = new HashSet<>();
    //private final IvyArtifactRepository repo;

    public RepositoryManager(Project target) {
        this.target = target;
        this.buildBinaryDependencies = target.getTasks().create("buildBinaryDependencies");
        this.buildSourceDependencies = target.getTasks().create("buildSourceDependencies");
        this.buildSourceDependencies.dependsOn(this.buildBinaryDependencies);
        addDepResolutionWarning();
        addKnownTaskDependencies();

        this.baseFolder = target.getLayout().getBuildDirectory().dir("generated_repo").get().getAsFile();
        this.baseFolder.mkdirs();
        this.target.afterEvaluate(p -> {
            target.getRepositories().ivy(ivy -> {
                ivy.setName("Forge Generatating Repo");
                ivy.setUrl(baseFolder.toURI());
                ivy.patternLayout(layout -> {
                    layout.artifact(IvyArtifactRepository.MAVEN_ARTIFACT_PATTERN);
                    layout.ivy(IvyArtifactRepository.MAVEN_IVY_PATTERN);
                    layout.setM2compatible(true);
                });
                ivy.metadataSources(meta -> meta.ivyDescriptor());
                ivy.content(content -> {
                    for (var art : this.artifacts)
                        content.includeVersion(art.dep().getGroup(), art.dep().getName(), art.dep().getVersion());
                });
            });
        });
    }

    // TODO: Make Public API
    public Task getBuildBinaryDependencies() {
        return this.buildBinaryDependencies;
    }

    // TODO: Make Public API
    public Task getBuildSourceDependencies() {
        return this.buildSourceDependencies;
    }

    private void addDepResolutionWarning() {
        target.getConfigurations().all(cfg -> {
            cfg.getIncoming().beforeResolve(rd -> {
                // TODO: Filter configurations to only ones that we know we have to create dependencies for.
                var task = getBuildBinaryDependencies();
                if (task.getState().getExecuted())
                    return;
                target.getLogger().warn("WARNING: Resolving configuration `" + cfg.getName() + "` before task `" + task.getName() + "` was executed.\r\n" +
                    "This could cause issues with generated/transformed dependencies.\r\n" +
                    "If this was done during the configuration phase, consider making your task inputs lazy evaludated.\r\n" +
                    "If this was done during the execution phase, make the nessasary task depend on `" + task.getName() + "`");
            });
        });
    }

    /*
     * Do a best effort to make sure we resolve before most of he know tasks.
     * This isn't fool proof, but we should warn users if they resolve things too early above.
     */
    private void addKnownTaskDependencies() {
        target.afterEvaluate(prj -> {
            // Any compile task, typically JavaCompile but other languages exist.
            addDepends(AbstractCompile.class);
            // Intellij `ideadModule` task
            addDepends(GenerateIdeaModule.class);
            // Eclipse `eclipseClasspath` task
            addDepends(GenerateEclipseClasspath.class);
            // Gradle's `dependencies' task
            addDepends(DependencyReportTask.class);
        });
    }

    public void blacklist(Task task) {
        this.blacklist.add(task);
    }
    private void addDepends(Class<? extends Task> type) {
        target.getTasks().withType(type, t -> {
            if (!blacklist.contains(t))
                t.dependsOn(getBuildSourceDependencies());
        });
    }

    // TODO: Duplication protection
    public void addArtifact(RepositoryArtifact artifact) {
        var dep = artifact.dep();
        var group = dep.getGroup();
        var name = dep.getName();
        var ver = dep.getVersion();

        var art = Artifact.from(group, name, ver);
        var ivyXml = new File(baseFolder, String.join("/", group.replace('.', '/'), name, ver, "ivy-" + ver + ".xml"));

        if (artifact.metadata() != null) {
            var copy = target.getTasks().create(artifact.metadata().getName() + "_copy", CopySingleFile.class);
            copy.getInput().convention(artifact.metadata().getOutput());
            copy.getOutput().set(ivyXml);
            this.getBuildBinaryDependencies().dependsOn(copy);
            this.getBuildSourceDependencies().dependsOn(copy);
        } else {
            var meta = target.getTasks().create("_repo_dep_" + Util.sanitizeTaskName(group) + '_' + Util.sanitizeTaskName(name) + '_' + Util.sanitizeTaskName(ver), CreateIvyMetadata.class);
            meta.getOrganization().convention(dep.getGroup());
            meta.getArtifact().convention(dep.getName());
            meta.getVersion().convention(dep.getVersion());
            meta.getOutput().set(ivyXml);
            this.getBuildBinaryDependencies().dependsOn(meta);
            this.getBuildSourceDependencies().dependsOn(meta);
        }

        if (artifact.binary() != null) {
            var copy = target.getTasks().create(artifact.binary().getName() + "_copy", CopySingleFile.class);
            copy.getInput().convention(artifact.binary().getOutput());
            copy.getOutput().set(new File(baseFolder, art.getPath()));
            this.getBuildBinaryDependencies().dependsOn(copy);
        }

        if (artifact.source() != null) {
            var copy = target.getTasks().create(artifact.source().getName() + "_copy", CopySingleFile.class);
            copy.getInput().convention(artifact.source().getOutput());
            var src = Artifact.from(group, name, ver, "sources", null);
            copy.getOutput().set(new File(baseFolder, src.getPath()));
            this.getBuildSourceDependencies().dependsOn(copy);
        }

        this.artifacts.add(artifact);
    }
}
