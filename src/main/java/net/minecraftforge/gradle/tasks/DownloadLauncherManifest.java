package net.minecraftforge.gradle.tasks;

import java.io.IOException;

import net.minecraftforge.gradle.util.Tools;

public abstract class DownloadLauncherManifest extends SimpleDownloadTask {
    public DownloadLauncherManifest() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().file("versions/version_manifest.json"));
    }

    @Override
    public void run() throws IOException {
        download.src(Tools.LAUNCHER_MANIFEST);
        super.run();
    }
}
