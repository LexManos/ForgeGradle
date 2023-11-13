package net.minecraftforge.gradle.pipelines.mcp.tasks;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.util.Util;

@ApiStatus.Internal
public abstract class Inject extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getPackages();

    @InputFile
    public abstract RegularFileProperty getMcp();

    @Input
    public abstract Property<String> getPrefix();

    @Input
    public abstract Property<String> getSide();

    @TaskAction
    public void exec() throws IOException {
        var output = this.getOutputFile();
        if (output.exists())
            output.delete();

        if (!output.getParentFile().exists())
            output.getParentFile().mkdirs();

        var prefix = getPrefix().get();

        try (var zos = new ZipOutputStream(new FileOutputStream(output));
             var zis = new ZipInputStream(new FileInputStream(getMcp().getAsFile().get()))) {

            for (var entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                if (entry.isDirectory() || !entry.getName().startsWith(prefix))
                    continue;

                var name = entry.getName().substring(prefix.length());
                var filter = "server".equals(getSide().get()) ? name.contains("/client/") : name.contains("/server/");

                if ("package-info-template.java".equals(name)) {
                    var template = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                    var packages = Files.lines(getPackages().getAsFile().get().toPath()).toList();
                    for (var pkg : packages) {
                        zos.putNextEntry(Util.getStableEntry(pkg + "/package-info.java"));
                        zos.write(template.replace("{PACKAGE}", pkg.replaceAll("/", ".")).getBytes(StandardCharsets.UTF_8));
                        zos.closeEntry();
                    }
                } else if (!filter) {
                    zos.putNextEntry(Util.getStableEntry(name));
                    zis.transferTo(zos);
                    zos.closeEntry();
                }
            }
        }
    }
}
