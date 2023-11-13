package net.minecraftforge.gradle.tasks;

import java.io.IOException;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;

import net.minecraftforge.gradle.data.JsonData;

public abstract class DownloadVersionFile extends SimpleDownloadTask {
    @InputFile
    public abstract RegularFileProperty getManifest();

    @Input
    public abstract Property<String> getType();

    @Override
    public void run() throws IOException {
        var manifest = JsonData.minecraftVersion(getManifest().get().getAsFile());
        var dl = manifest.downloads.get(getType().get());
        if (dl == null)
            throw new IllegalStateException("Missing \"" + getType().get() + "\" from " + getManifest().get().getAsFile());
        download.src(dl.url);
        super.run();
    }
}
