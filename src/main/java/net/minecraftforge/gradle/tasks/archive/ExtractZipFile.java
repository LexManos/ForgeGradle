package net.minecraftforge.gradle.tasks.archive;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.tasks.SingleFileOutput;

@ApiStatus.Internal
public abstract class ExtractZipFile extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getZip();

    @Input
    public abstract Property<String> getInternalPath();

    @TaskAction
    public void exec() throws IOException {
        var archive = getZip().getAsFile().get();
        try (var zip = new ZipFile(archive)){
            var data = getInternalPath().get();
            var entry = zip.getEntry(data);
            if (entry == null)
                throw new IllegalArgumentException("Invalid archive `" + archive + "` - Missing data file `" + data + "`");

            var target = getOutputFile();
            if (!target.getParentFile().exists())
                target.getParentFile().mkdirs();

            try (var os = new FileOutputStream(target)) {
                zip.getInputStream(entry).transferTo(os);
            }
        }
    }
}
