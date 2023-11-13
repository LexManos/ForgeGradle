package net.minecraftforge.gradle.pipelines.mcp.tasks;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.srgutils.IMappingFile;

@ApiStatus.Internal
public abstract class StripJar extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getInput();

    @InputFile
    public abstract RegularFileProperty getMcp();

    @Input
    public abstract Property<Boolean> getWhitelist();

    @Input
    public abstract Property<String> getMappings();

    @TaskAction
    public void run() throws IOException {
        var classes = new HashSet<String>();
        try (var zip = new ZipFile(getMcp().getAsFile().get())) {
            var entry = zip.getEntry(getMappings().get());
            if (entry == null)
                throw new IllegalStateException("Invalid MCP Config archive, missing " + getMappings().get());

            var map = IMappingFile.load(zip.getInputStream(entry));
            map.getClasses().forEach(c -> classes.add(c.getOriginal() + ".class"));
        }

        var output = getOutputFile().getAbsoluteFile();
        if (output.exists())
            output.delete();

        if (!output.getParentFile().exists())
            output.getParentFile().mkdirs();

        var input = getInput().getAsFile().get();
        var whitelist = getWhitelist().get().booleanValue();

        try (var is = new JarInputStream(new FileInputStream(input));
             var os = new JarOutputStream(new FileOutputStream(output))) {
            JarEntry entry;
            while ((entry = is.getNextJarEntry()) != null) {
                if (entry.isDirectory() || classes.contains(entry.getName()) != whitelist)
                    continue;
                os.putNextEntry(entry);
                is.transferTo(os);
                os.closeEntry();
            }
        }
    }
}
