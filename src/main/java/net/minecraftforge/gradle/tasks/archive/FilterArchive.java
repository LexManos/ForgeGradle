package net.minecraftforge.gradle.tasks.archive;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.util.Util;

public abstract class FilterArchive extends DefaultTask implements SingleFileOutput {
    public FilterArchive() {
        getWhitelist().convention(true);
    }

    @InputFile
    public abstract RegularFileProperty getInput();

    @Input
    public abstract ListProperty<String> getFilters();

    @Input
    public abstract Property<Boolean> getWhitelist();

    @TaskAction
    public void exec() throws IOException {
        var output = this.getOutputFile().getAbsoluteFile();
        if (!output.getParentFile().exists())
            output.getParentFile().mkdirs();

        var filters = new ArrayList<Pattern>();
        for (var filter : getFilters().get())
            filters.add(Pattern.compile(filter));

        var whitelist = getWhitelist().get();

        try (var zin = new ZipInputStream(new FileInputStream(getInput().getAsFile().get()));
             var zos = new ZipOutputStream(new FileOutputStream(output))) {

            for (var entry = zin.getNextEntry(); entry != null;) {
                var name = entry.getName();
                if (whitelist) {
                    if (filters.stream().anyMatch(p -> !p.matcher(name).matches()))
                        continue;
                } else {
                    if (filters.stream().anyMatch(p -> p.matcher(name).matches()))
                        continue;
                }

                zos.putNextEntry(Util.getStableEntry(name));
                zin.transferTo(zos);
                zos.close();
            }
         }
    }
}
