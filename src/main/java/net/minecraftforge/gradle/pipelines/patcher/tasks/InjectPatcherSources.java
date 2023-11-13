package net.minecraftforge.gradle.pipelines.patcher.tasks;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.util.Util;

// This isn't a Copy task because when using Zip it decompresses all the files into a flat directory. Which is dumb
public abstract class InjectPatcherSources extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getInput();

    @InputFile
    public abstract RegularFileProperty getArchive();

    @TaskAction
    public void exec() throws IOException {
        try (var zout = new ZipOutputStream(new FileOutputStream(getOutputFile()))) {
            var added = new HashSet<String>();
            try (var zin = new ZipInputStream(new FileInputStream(getArchive().getAsFile().get()))) {
                copyEntries(zin, zout, added, true);
            }
            try (var zin = new ZipInputStream(new FileInputStream(getInput().getAsFile().get()))) {
                copyEntries(zin, zout, added, false);
            }
        }
    }

    private void copyEntries(ZipInputStream zin, ZipOutputStream zout, Set<String> blacklist, boolean filterPatches) throws IOException {
        ZipEntry entry;
        while ((entry = zin.getNextEntry()) != null) {
            if (!blacklist.add(entry.getName())) continue;
            if (filterPatches && entry.getName().startsWith("patches/")) continue;

            zout.putNextEntry(Util.getStableEntry(entry.getName()));
            zin.transferTo(zout);
            zout.closeEntry();
            blacklist.add(entry.getName());
        }
    }
}