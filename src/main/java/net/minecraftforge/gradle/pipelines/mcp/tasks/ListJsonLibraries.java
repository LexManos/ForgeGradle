package net.minecraftforge.gradle.pipelines.mcp.tasks;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.data.JsonData;
import net.minecraftforge.gradle.data.MinecraftVersion.LibraryDownload;
import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.util.DownloadUtils;

@ApiStatus.Internal
public abstract class ListJsonLibraries extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getJson();

    @OutputDirectory
    public abstract DirectoryProperty getLibraryDir();

    @TaskAction
    public void exec() throws IOException {
        var json = JsonData.minecraftVersion(getJson().getAsFile().get());

        var buf = new StringBuilder();

        record Lib(String coord, LibraryDownload dl) {}
        var libs = new ArrayList<Lib>();

        //TODO: [FG][MinecraftVersion] Add function to filter libraries based on current operating systems
        for (var lib : json.libraries) {
            libs.add(new Lib(lib.name, lib.downloads.artifact));
            if (lib.downloads.classifiers != null)
                lib.downloads.classifiers.forEach((k, v) -> libs.add(new Lib(lib.name + ':' + k, v)));
        }

        for (var lib : libs) {
            var target = getLibraryDir().file(lib.dl.path).get().getAsFile().getAbsoluteFile();
            if (!target.exists()) {
                var file = DownloadUtils.downloadSingleFile(getProject(), lib.coord);
                if (!target.getParentFile().exists())
                    target.getParentFile().mkdirs();
                Files.copy(file.toPath(), target.toPath());
            }
            buf.append("-e=").append(target.getAbsolutePath()).append('\n');

        }

        try (var os = new FileOutputStream(getOutputFile())) {
            os.write(buf.toString().getBytes(StandardCharsets.UTF_8));
        }
    }
}
