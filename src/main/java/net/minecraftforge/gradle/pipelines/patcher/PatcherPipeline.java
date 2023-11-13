package net.minecraftforge.gradle.pipelines.patcher;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.ForgeGradleExtension;
import net.minecraftforge.gradle.pipelines.Pipeline;
import net.minecraftforge.gradle.repo.RepositoryArtifact;
import net.minecraftforge.gradle.repo.RepositoryManager;
import net.minecraftforge.gradle.tasks.DownloadMavenArtifact;
import net.minecraftforge.gradle.util.DownloadUtils;
import net.minecraftforge.gradle.util.FileHelper;
import net.minecraftforge.gradle.util.Tools;
import net.minecraftforge.gradle.util.Util;
import net.minecraftforge.srgutils.MinecraftVersion;

@ApiStatus.Internal
public class PatcherPipeline implements Pipeline {
    final Project project;
    final ForgeGradleExtension fg;
    final FileHelper global;
    DownloadMavenArtifact dlAt;
    DownloadMavenArtifact dlMergeTool;

    private final Map<String, List<RepositoryArtifact>> cache = new HashMap<>();
    private final Map<String, Patcher> patchers = new HashMap<>();

    public PatcherPipeline(Project target, ForgeGradleExtension fg) {
        this.project = target;
        this.fg = fg;
        this.global = new FileHelper(target, "_patcher");
    }

    @Override
    public Dependency handleDependency(RepositoryManager repo, Dependency dep) {
        var key = getKey(dep);
        var artifacts = cache.get(key);
        // Gradle picks one maven repository and caches everything related to the artifact there.
        // So we make a fake 'fg.patcher' prefixed dependency to unlink from everything.
        // It'll break if someone makes a group with 'fg.patcher' but screw it.
        // This also means that the generated POM has this invalid artifact. But it does for all other generated artifacts anyways.
        var relocated = this.project.getDependencies().create("fg.patcher." + dep.getGroup() + ':' + dep.getName() + ':' + dep.getVersion());

        if (artifacts == null) {
            artifacts = buildArtifacts(relocated, key);
            cache.put(key, artifacts);
        }

        for (var art : artifacts)
            repo.addArtifact(art);

        return relocated;
    }

    private String getKey(Dependency dep) {
        String classifier = "userdev";
        if ("net.minecraftforge".equals(dep.getGroup()) && "forge".equals(dep.getName())) {
            var mcver = MinecraftVersion.from(dep.getVersion().split("-")[0]);
            if (mcver.compareTo(MinecraftVersion.from("1.13")) < 0)
                classifier = "userdev3";
        }

        return dep.getGroup() + ':' + dep.getName() + ':' + dep.getVersion() + ':' + classifier;
    }

    private List<RepositoryArtifact> buildArtifacts(Dependency dep, String key) {
        var patcher = getPatcher(key);

        setupCommonTasks();

        return patcher.buildArtifacts(dep, !Util.isSourceDisabled());
    }

    Patcher getPatcher(String coord) {
        var ret = patchers.get(coord);
        if (ret != null)
            return ret;

        var file = DownloadUtils.downloadSingleFile(project, coord);
        ret = new Patcher(this, coord, file);

        patchers.put(coord, ret);
        return ret;
    }

    private void setupCommonTasks() {
        if (dlAt == null) {
            dlAt = task("downloadAccessTransformer", DownloadMavenArtifact.class);
            dlAt.getArtifact().convention(Tools.AT);
            dlAt.getOutput().convention(global.file("accesstransformer.jar"));
        }

        if (dlMergeTool == null) {
            dlMergeTool = task("downloadMergeTool", DownloadMavenArtifact.class);
            dlMergeTool.getArtifact().convention(Tools.SIDESTRIPPER);
            dlMergeTool.getOutput().convention(global.file("mergetool.jar"));
        }
    }

    private <T extends Task> T task(String name, Class<T> type) {
        name = "_patcher_" + name;
        var ret = project.getTasks().create(name, type);
        ret.setGroup("Forge Gradle Internal - Patcher Pipeline");
        return ret;
    }
}
