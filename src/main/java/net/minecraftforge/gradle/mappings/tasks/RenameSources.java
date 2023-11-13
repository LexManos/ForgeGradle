package net.minecraftforge.gradle.mappings.tasks;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.mappings.McpNames;
import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.util.Util;

@ApiStatus.Internal
public abstract class RenameSources extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getInput();

    @InputFile
    public abstract RegularFileProperty getNames();

    @Input
    public abstract Property<Boolean> getJavadocs();

    @Input
    public abstract Property<Boolean> getLambdas();

    @Input
    public abstract Property<String> getEncoding();

    public RenameSources() {
        getJavadocs().convention(true);
        getLambdas().convention(true);
        getEncoding().convention("UTF-8");
        getOutput().convention(buildDir("renamed-sources.jar"));
    }

    @TaskAction
    public void exec() throws IOException {
        var names = McpNames.load(getNames().getAsFile().get());
        var output = getOutputFile();
        if (!output.getParentFile().exists())
            output.getParentFile().mkdirs();
        var encoding = Charset.forName(getEncoding().get());

        try (var zin = new ZipInputStream(new FileInputStream(getInput().getAsFile().get()));
             var zos = new ZipOutputStream(new FileOutputStream(output))) {

            for (var entry = zin.getNextEntry(); entry != null; entry = zin.getNextEntry()) {
                zos.putNextEntry(Util.getStableEntry(entry.getName()));
                if (entry.getName().endsWith(".java")) {
                    var renamed = names.rename(zin, getJavadocs().get(), getLambdas().get(), encoding);
                    zos.write(renamed.getBytes(encoding));
                } else
                    zin.transferTo(zos);
                zos.closeEntry();
            }
        }
    }
}
