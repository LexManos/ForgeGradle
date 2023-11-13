package net.minecraftforge.gradle.pipelines.vanilla.tasks;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.util.BundleMetadata;
import net.minecraftforge.gradle.util.Util;
import net.minecraftforge.srgutils.IMappingFile;

public abstract class StripServerJar extends DefaultTask implements SingleFileOutput {
    public StripServerJar() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.jar")));
    }

    @InputFile
    public abstract RegularFileProperty getInput();

    @InputFile
    @Optional
    public abstract RegularFileProperty getMapping();

    @TaskAction
    protected void run() throws IOException {;
        var input = getInput().get().getAsFile();
        var output = getOutput().get().getAsFile();
        if (output.getParentFile() != null && output.getParentFile().exists())
            output.getParentFile().mkdirs();

        try (var jar = new JarFile(input)) {
            var bundle = BundleMetadata.read(jar);

            if (bundle != null) {
                var path = "META-INF/versions/" + bundle.versions.get(0).path;
                var entry = jar.getEntry(path);
                if (entry == null)
                    throw new IOException("Bundle missing version file: " + path);
                Files.copy(jar.getInputStream(entry), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else if (!getMapping().isPresent()) {
                throw new IllegalStateException("Input jar " + input + " is not a Bundled Jar, and Mappings not specified");
            } else {
                var classes = IMappingFile.load(getMapping().get().getAsFile())
                    .getClasses().stream()
                    .map(c -> c.getOriginal() + ".class")
                    .collect(Collectors.toSet());

                try (var out = new ZipOutputStream(new FileOutputStream(output))) {
                    for (var entries = jar.entries(); entries.hasMoreElements();) {
                        var entry = entries.nextElement();
                        var name = entry.getName();
                        boolean copy = true;
                        if (name.endsWith(".class") && !classes.contains(name)) {
                            copy = false;
                        }

                        if (copy) {
                            var _new = Util.getStableEntry(name);
                            out.putNextEntry(_new);
                            try (var ein = jar.getInputStream(entry)) {
                                ein.transferTo(out);
                            }
                            out.closeEntry();
                        }
                    }
                }
            }
        }
    }

}
