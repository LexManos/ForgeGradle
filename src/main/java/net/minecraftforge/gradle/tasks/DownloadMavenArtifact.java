package net.minecraftforge.gradle.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.gradle.api.DefaultTask;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.util.DownloadUtils;

public abstract class DownloadMavenArtifact extends DefaultTask implements SingleFileOutput {
    public DownloadMavenArtifact() {
        var tmp = getProject().getLayout().getBuildDirectory().dir(getName());
        getOutput().convention(tmp.map(d -> d.file("output.jar")));
    }

    @Input
    public abstract Property<String> getArtifact();

    @TaskAction
    public void run() throws IOException {
        var file = DownloadUtils.downloadSingleFile(getProject(), getArtifact().get());
        var output = getOutputFile();
        if (!output.getParentFile().exists())
            output.getParentFile().mkdirs();
        Files.copy(file.toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
