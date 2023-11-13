package net.minecraftforge.gradle.tasks.archive;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public abstract class ExtractZipDir extends DefaultTask {
    @InputFile
    public abstract RegularFileProperty getZip();

    @Input
    public abstract Property<String> getPrefix();

    @OutputDirectory
    public abstract DirectoryProperty getOutput();

    @TaskAction
    public void exec() throws IOException {
        var archive = getZip().getAsFile().get();
        try (var zip = new ZipFile(archive)){
            var count = 0;
            var prefix = getPrefix().get();

            for (var itr = zip.entries(); itr.hasMoreElements(); ) {
                var e = itr.nextElement();
                if (e.isDirectory() || !e.getName().startsWith(prefix))
                    continue;

                var relative = e.getName().substring(prefix.length());
                var target = getOutput().map(d -> d.file(relative)).get().getAsFile();
                if (!target.getParentFile().exists())
                    target.getParentFile().mkdirs();

                try (var os = new FileOutputStream(target)) {
                    zip.getInputStream(e).transferTo(os);
                }
            }

            if (count == 0)
                throw new IllegalArgumentException("Invalid Archive `" + archive + "` - Missing data files `" + prefix + "`");
        }
    }
}
