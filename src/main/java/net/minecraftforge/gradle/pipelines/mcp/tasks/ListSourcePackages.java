package net.minecraftforge.gradle.pipelines.mcp.tasks;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.TreeSet;
import java.util.zip.ZipInputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.tasks.SingleFileOutput;

public abstract class ListSourcePackages extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getInput();

    @TaskAction
    public void exec() throws IOException {
        var packages = new TreeSet<String>();
        try (var zis = new ZipInputStream(new FileInputStream(getInput().getAsFile().get()))) {
            for (var entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                var name = entry.getName();
                var idx = name.lastIndexOf('/');
                if (idx != -1 && name.endsWith(".java")) {
                    var pkg = name.substring(0, idx);
                    if (pkg.startsWith("net/minecraft/") || pkg.startsWith("com/mojang/"))
                        packages.add(pkg);
                }
            }
        }

        try (var os = new FileOutputStream(getOutputFile())) {
            var data = String.join("\n", packages);
            os.write(data.getBytes(StandardCharsets.UTF_8));
        }
    }
}
