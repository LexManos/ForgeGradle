package net.minecraftforge.gradle.util;

import java.util.HashMap;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.tasks.DownloadLauncherManifest;
import net.minecraftforge.gradle.tasks.DownloadVersionFile;
import net.minecraftforge.gradle.tasks.DownloadVersionJson;
import net.minecraftforge.gradle.tasks.SingleFileOutput;

@ApiStatus.Internal
public class MinecraftTaskHelper {
    private final Project project;
    private final FileHelper local;
    private SingleFileOutput dlManifest;
    private Map<String, DownloadVersionJson> dlVersionJson = new HashMap<>();
    private Map<Key, DownloadVersionFile> dlVersionFile = new HashMap<>();
    private record Key(String version, String id) {}

    public MinecraftTaskHelper(Project project) {
        this.project = project;
        this.local = new FileHelper(project, "versions");
    }

    private <T extends Task> T task(String name, Class<T> type) {
        var ret = project.getTasks().create(name, type);
        ret.setGroup("Forge Gradle Internal - Minecraft Related");
        return ret;
    }

    private String sanitize(String str) {
        return Util.sanitizeTaskName(str);
    }

    public SingleFileOutput getLauncherManifest() {
        if (dlManifest == null) {
            dlManifest = task("downloadLauncherManifest", DownloadLauncherManifest.class);
            dlManifest.getOutput().convention(local.file("launcher_manifest.json"));
        }

        return dlManifest;
    }

    public SingleFileOutput getVersionJson(String version) {
        var ret = dlVersionJson.get(version);
        if (ret != null)
            return ret;

        var task = task("downloadVersionJson_" + sanitize(version), DownloadVersionJson.class);
        task.getVersion().convention(version);
        task.getManifest().convention(getLauncherManifest().getOutput());
        task.getOutput().convention(local.file(version, "version.json"));

        dlVersionJson.put(version, task);
        return task;
    }

    public SingleFileOutput getVersionFile(String version, String file, String ext) {
        var key = new Key(version, file);
        var ret = dlVersionFile.get(key);
        if (ret != null)
            return ret;

        var task = task("download" + sanitize(Util.capitalize(file)) + '_' + sanitize(version), DownloadVersionFile.class);
        task.getType().convention(file);
        task.getManifest().convention(getVersionJson(version).getOutput());
        task.getOutput().convention(local.file(version, file + '.' + ext));

        dlVersionFile.put(key, task);
        return task;
    }

}
