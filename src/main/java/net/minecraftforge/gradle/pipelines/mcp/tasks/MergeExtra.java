package net.minecraftforge.gradle.pipelines.mcp.tasks;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.srgutils.IMappingFile;

@ApiStatus.Internal
public abstract class MergeExtra extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getInput();

    @InputFile
    public abstract RegularFileProperty getBase();

    @InputFile
    public abstract RegularFileProperty getMappings();

    @TaskAction
    public void exec() throws IOException {

        var input = getInput().getAsFile().get();
        var output = getOutputFile().getAbsoluteFile();

        if (output.exists())
            output.delete();

        if (!output.getParentFile().exists())
            output.getParentFile().mkdirs();

        try (var jout = new JarOutputStream(new FileOutputStream(output))) {
            var blacklist = new HashSet<String>();
            try (var jin = new JarInputStream(new FileInputStream(input))) {
                transfer(jin, jout, blacklist);
            }

            // Blacklist any obfusicated classes before merging form the main jar
            var map = IMappingFile.load(getMappings().getAsFile().get());
            map.getClasses().forEach(c -> blacklist.add(c.getOriginal() + ".class"));

            var base = getBase().getAsFile().get();
            boolean isBundle = true;
            try (var jar = new JarFile(base)) {
                var format = jar.getManifest().getMainAttributes().getValue("Bundler-Format");
                if (format == null) {
                    isBundle = false;
                } else if ("1.0".equals(format)) {
                      var entry = jar.getEntry("META-INF/versions.list");
                      if (entry == null)
                          throw new IllegalStateException("Invalid bundle: `" + base + "` - Missing META-INF/versions.list");

                      String path = null;
                      var reader = new BufferedReader(new InputStreamReader(jar.getInputStream(entry)));
                      String line;
                      while ((line = reader.readLine()) != null) {
                          var pts = line.split("\t");
                          if (pts.length < 3)
                              throw new IllegalStateException("Invalid bundle: `" + base + "` - Invalid line: " + line);
                          if (path != null)
                              throw new IllegalStateException("Invalid bundle: `" + base + "` - Multiple files in versions.list: " + line);
                          path = pts[2];
                      }

                      entry = jar.getEntry("META-INF/versions/" + path);
                      if (entry == null)
                          throw new IllegalStateException("Invalid bundle: `" + base + "` - Missing META-INF/versions/" + path);

                      try (var jin = new JarInputStream(jar.getInputStream(entry))) {
                          transfer(jin, jout, blacklist);
                      }

                } else
                    throw new RuntimeException("Invalid bundle: `" + base + "` - Unsupported format " + format);
            }

            if (!isBundle) {
                try (var jin = new JarInputStream(new FileInputStream(base))) {
                    transfer(jin, jout, blacklist);
                }
            }
        }
    }

    private void transfer(JarInputStream jin, JarOutputStream jout, Set<String> blacklist) throws IOException {
        JarEntry entry;
        while ((entry = jin.getNextJarEntry()) != null) {
            if (entry.isDirectory() || isSignature(entry.getName()) || !blacklist.add(entry.getName()))
                continue;
            jout.putNextEntry(entry);
            jin.transferTo(jout);
            jout.closeEntry();
        }
    }

    private static Set<String> SIG_FILES = Set.of(".RSA", ".SF", ".DSA", ".EC");
    private boolean isSignature(String name) {
        if (!name.startsWith("META-INF/"))
            return false;

        for (var ext : SIG_FILES) {
            if (name.endsWith(ext))
                return true;
        }

        return false;
    }
}
