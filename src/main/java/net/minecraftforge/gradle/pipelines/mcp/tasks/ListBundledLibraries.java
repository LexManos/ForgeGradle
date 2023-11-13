package net.minecraftforge.gradle.pipelines.mcp.tasks;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.TreeSet;
import java.util.jar.JarFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.tasks.SingleFileOutput;

@ApiStatus.Internal
public abstract class ListBundledLibraries extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getBundle();

    @OutputDirectory
    public abstract DirectoryProperty getLibraryDir();

    @TaskAction
    public void exec() throws IOException {
        var bundle = getBundle().getAsFile().get();
        try (var jar = new JarFile(bundle)) {
            var format = jar.getManifest().getMainAttributes().getValue("Bundler-Format");
            if (format == null)
                throw new IllegalStateException("Invalid bundle: `" + bundle + "` - Missing format entry from manifest");

            if (!"1.0".equals(format))
                throw new RuntimeException("Invalid bundle: `" + bundle + "` - Unsupported format " + format);

            var entry = jar.getEntry("META-INF/libraries.list");
            if (entry == null)
                throw new IllegalStateException("Invalid bundle: `" + bundle + "` - Missing META-INF/libraries.list");

            var libs = new TreeSet<String>();

            var reader = new BufferedReader(new InputStreamReader(jar.getInputStream(entry)));
            String line;
            while ((line = reader.readLine()) != null) {
                var pts = line.split("\t");
                if (pts.length < 3)
                    throw new IllegalStateException("Invalid bundle: `" + bundle + "` - Invalid line: " + line);
                libs.add(pts[2]);
            }

            var buf = new StringBuilder();

            for (var lib : libs) {
                var target = getLibraryDir().get().file(lib).getAsFile();
                buf.append("-e=").append(target.getAbsolutePath()).append('\n');

                if (target.exists())
                    continue; // TODO: [FG][MCP][ListBundledLibraries] Add hash checking?

                entry = jar.getEntry("META-INF/libraries/" + lib);
                if (entry == null)
                    throw new IllegalStateException("Invalid bundle: `" + bundle + "` - Missing META-INF/libraries/" + lib);

                if (!target.getParentFile().exists())
                    target.getParentFile().mkdirs();

                try (var os = new FileOutputStream(target);
                     var is = jar.getInputStream(entry)) {
                    is.transferTo(os);
                }
            }

            try (var os = new FileOutputStream(getOutputFile())) {
                os.write(buf.toString().getBytes(StandardCharsets.UTF_8));
            }
        }
    }
}
