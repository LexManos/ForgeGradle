package net.minecraftforge.gradle.tasks.archive;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.util.Util;

public abstract class SubArchive extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getInput();

    @Input
    public abstract Property<String> getPrefix();

    @TaskAction
    public void exec() throws IOException {
        var output = this.getOutputFile().getAbsoluteFile();
        if (!output.getParentFile().exists())
            output.getParentFile().mkdirs();

        var prefix = getPrefix().get();
        try (var zin = new ZipInputStream(new FileInputStream(getInput().getAsFile().get()));
             var zos = new ZipOutputStream(new FileOutputStream(output))) {

            for (var entry = zin.getNextEntry(); entry != null;) {
                var name = entry.getName();
                if (name.length() <= prefix.length() || !name.startsWith(prefix))
                    continue;

                zos.putNextEntry(Util.getStableEntry(name.substring(prefix.length())));
                zin.transferTo(zos);
                zos.close();
            }
         }
    }
}
