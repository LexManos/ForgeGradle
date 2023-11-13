package net.minecraftforge.gradle.tasks;

import java.io.IOException;

import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.TaskAction;

import de.undercouch.gradle.tasks.download.DownloadAction;

public abstract class SimpleDownloadTask extends DefaultTask implements SingleFileOutput {
    protected final DownloadAction download;

    public SimpleDownloadTask() {
        final boolean isOffline = getProject().getGradle().getStartParameter().isOffline();
        download = new DownloadAction(getProject(), this);
        download.useETag("all");
        download.quiet(true);
        download.onlyIfModified(true);

        onlyIf(task -> {
            if (isOffline) {
                var output = getOutput().get().getAsFile();
                if (!output.exists())
                    throw new IllegalStateException("Can not download '" + output + "' in offline mode");
                return false;
            }
            return true;
        });
    }

    @TaskAction
    public void run() throws IOException {
        download.dest(getOutput());
        download.execute().thenRun(() -> {
            if (download.isUpToDate())
                getState().setDidWork(false);
        });
    }
}
