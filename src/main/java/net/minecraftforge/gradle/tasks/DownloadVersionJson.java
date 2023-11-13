package net.minecraftforge.gradle.tasks;

import java.io.IOException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;

import net.minecraftforge.gradle.data.JsonData;

public abstract class DownloadVersionJson extends SimpleDownloadTask {
    public DownloadVersionJson() {
        var buildDir = getProject().getLayout().getBuildDirectory();
        var out = getProject().provider(() -> buildDir.file("versions/" + getVersion().get() + "/version.json").get());
        getOutput().convention(out);
    }

    @InputFile
    public abstract RegularFileProperty getManifest();

    @Input
    public abstract Property<String> getVersion();

    @Override
    public void run() throws IOException {
        var manifest = JsonData.launcherManifest(getManifest().get().getAsFile());
        download.src(manifest.getUrl(getVersion().get()));
        super.run();
    }
}
