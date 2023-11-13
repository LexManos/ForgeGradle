package net.minecraftforge.gradle.tasks.archive;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.util.HashFunction;
import net.minecraftforge.gradle.util.Util;

/**
 * This is here because Gradle's normal Copy/Zip tasks expand the input zips to the user's
 * file system instead of doing it in memory. Which is annoying and prone to killing hard drives
 * If this ever changes, delete this task and use the normal one.
 *
 * I also sort the entries to satisfy JarInputStream
 * As well as striping signature data, probably worth pulling out to a different task
 * I also merge service files, I don't merge module files, I don't wanna deal with that.
 */
public abstract class OrderedMergeJars extends DefaultTask implements SingleFileOutput {
    public OrderedMergeJars() {
        /*
         * FileCollection, ListProperty, FileProperty all are unordered when using Files.
         * So i have to do this stupid hack to encode the order as a string because THEN it cares about order.
         *
         * I *probably* dont have to include the hash but fuck it lets be extra careful and performance wise
         * this is negligible compared to anything gradle itself does. Hell including the hash myself means I
         * could disable the getArchives as a input.
         */
        getOrder().convention(getProject().provider(() -> {
            var ret = new ArrayList<String>();
            for (var input : getArchives().get())
                ret.add(HashFunction.SHA1.hash(input.getAsFile()) + ' ' + input.getAsFile().getName());
            return ret;
        }));

        getStripSignatures().convention(false);
    }

    @InputFiles
    @PathSensitive(PathSensitivity.NONE)
    public abstract SetProperty<RegularFile> getArchives();

    @Input
    public abstract Property<Boolean> getStripSignatures();

    @Input
    public abstract ListProperty<String> getOrder();

    @TaskAction
    public void exec() throws IOException {
        var output = this.getOutputFile().getAbsoluteFile();
        if (!output.getParentFile().exists())
            output.getParentFile().mkdirs();

        record Info(ZipEntry entry, ZipFile file) {}

        var files = new ArrayList<ZipFile>();
        try {
            // Find all entries, first one wins. We also use a TreeMap so the output is sorted.
            // JarInputStream requires the manifest to be the first entry, followed by any signature
            // files, so special case them.
            Info manifest = null;
            var signatures = new TreeMap<String, Info>();
            var services = new TreeMap<String, ByteArrayOutputStream>();
            var entries = new TreeMap<String, Info>();
            for (var input : getArchives().get()) {
                var zip = new ZipFile(input.getAsFile());
                files.add(zip);
                for (var itr = zip.entries(); itr.hasMoreElements(); ) {
                    var entry = itr.nextElement();
                    var name = entry.getName();
                    if (JarFile.MANIFEST_NAME.equalsIgnoreCase(name)) {
                        if (manifest == null)
                            manifest = new Info(entry, zip);
                    } else if (name.startsWith("META-INF/services/") && !entry.isDirectory()) { // Technically services can be multi-release files, but I don't care. If this comes up in the MC world then i'll care
                        var data = services.get(name);
                        if (data == null) {
                            data = new ByteArrayOutputStream();
                            services.put(name, data);
                        } else {
                            data.write('\n');
                        }
                        zip.getInputStream(entry).transferTo(data);
                    } else if (isBlockOrSF(name))
                        signatures.putIfAbsent(name, new Info(entry, zip));
                    else
                        entries.putIfAbsent(name, new Info(entry, zip));
                }
            }

            var strip = getStripSignatures().get();
            try (var zos = new ZipOutputStream(new FileOutputStream(output))) {
                if (manifest != null) {
                    if (strip)
                        writeStrippedManifest(zos, manifest.file(), manifest.entry());
                    else
                        writeEntry(zos, manifest.file(), manifest.entry());
                }
                if (!strip) {
                    for (var info : signatures.values())
                        writeEntry(zos, info.file(), info.entry());
                }
                for (var entry : services.entrySet()) {
                    zos.putNextEntry(Util.getStableEntry(entry.getKey()));
                    zos.write(entry.getValue().toByteArray());
                    zos.closeEntry();
                }
                for (var info : entries.values())
                    writeEntry(zos, info.file(), info.entry());
            }
        } finally {
            for (var zip : files) {
                try {
                    zip.close();
                } catch (IOException e) {}
            }
        }
    }

    private static void writeEntry(ZipOutputStream zos, ZipFile file, ZipEntry entry) throws IOException {
        zos.putNextEntry(Util.getStableEntry(entry.getName()));
        file.getInputStream(entry).transferTo(zos);
        zos.closeEntry();
    }

    private static void writeStrippedManifest(ZipOutputStream zos, ZipFile file, ZipEntry entry) throws IOException {
        var manifest = new Manifest(file.getInputStream(entry));
        for (var itr = manifest.getEntries().entrySet().iterator(); itr.hasNext();) {
            var section = itr.next();
            for (var sItr = section.getValue().entrySet().iterator(); sItr.hasNext();) {
                var attribute = sItr.next();
                var key = attribute.getKey().toString().toLowerCase(Locale.ROOT);
                if (key.endsWith("-digest"))
                    sItr.remove();
            }

            if (section.getValue().isEmpty())
                itr.remove();
        }
        zos.putNextEntry(Util.getStableEntry(entry.getName()));
        manifest.write(zos);
        zos.closeEntry();
    }

    public static boolean isBlockOrSF(String path) {
        var s = path.toUpperCase(Locale.ENGLISH);
        if (!s.startsWith("META-INF/")) return false;
        // Copy of sun.security.util.SignatureFileVerifier.isBlockOrSF(String), hasn't really ever changed, but just in case
        return s.endsWith(".SF")
            || s.endsWith(".DSA")
            || s.endsWith(".RSA")
            || s.endsWith(".EC");
    }
}
