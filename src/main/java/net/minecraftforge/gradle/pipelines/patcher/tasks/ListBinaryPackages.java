package net.minecraftforge.gradle.pipelines.patcher.tasks;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.ZipInputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IMappingFile.IClass;

public abstract class ListBinaryPackages extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getInput();

    @InputFile
    public abstract RegularFileProperty getMappings();

    @TaskAction
    public void exec() throws IOException {
        var map = IMappingFile.load(getMappings().getAsFile().get())
            .getClasses()
            .stream()
            .collect(Collectors.toMap(IClass::getOriginal, IClass::getMapped));

        var packages = new TreeSet<String>();
        try (var zis = new ZipInputStream(new FileInputStream(getInput().getAsFile().get()))) {
            for (var entry = zis.getNextEntry(); entry != null; entry = zis.getNextEntry()) {
                var name = entry.getName();
                if (!name.endsWith(".class"))
                    continue;
                var cls = name.substring(0, name.length() - 6);
                cls = map.getOrDefault(cls, cls);
                int idx = cls.lastIndexOf('/');

                if (idx != -1) {
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
