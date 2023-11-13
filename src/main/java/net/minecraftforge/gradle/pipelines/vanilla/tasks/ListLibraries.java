package net.minecraftforge.gradle.pipelines.vanilla.tasks;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import com.google.common.io.Files;

import de.undercouch.gradle.tasks.download.DownloadAction;
import net.minecraftforge.gradle.data.JsonData;
import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.util.Artifact;
import net.minecraftforge.gradle.util.BundleMetadata;
import net.minecraftforge.gradle.util.HashFunction;
import net.minecraftforge.gradle.util.Util;

public abstract class ListLibraries extends DefaultTask implements SingleFileOutput {
    public ListLibraries() {
        var build = getProject().getLayout().getBuildDirectory().dir(getName());
        var cache = Util.getGlobalCache(getProject());
        getCache().convention(cache.getAbsolutePath());
        getOutput().convention(build.map(d -> d.file("output.txt")));
    }

    @InputFile
    @Optional
    public abstract RegularFileProperty getBundledServer();

    @InputFile
    @Optional
    public abstract RegularFileProperty getLauncherMeta();

    @Input
    public abstract Property<String> getCache();

    @TaskAction
    public void run() throws IOException {
        var fromJson = true;
        var libs = new ArrayList<File>();

        if (getBundledServer().isPresent()) {
            try (var zip = new ZipFile(getBundledServer().get().getAsFile())) {
                var bundle = BundleMetadata.read(zip);
                if (bundle != null) {
                    libs.addAll(extractBundled(zip, bundle));
                    fromJson = false;
                }
            }
        }

        if (fromJson) {
            if (!getLauncherMeta().isPresent())
                throw new IllegalStateException("Missing launcher metadata file");
            var meta = JsonData.minecraftVersion(getLauncherMeta().get().getAsFile());
            var cache = new File(getCache().get());
            for (var lib : meta.libraries) {
                if (lib.downloads == null || lib.downloads.artifact == null)
                    throw new IllegalStateException("Invalid library entry: " + lib.name + " Missing downloads");

                var info = lib.downloads.artifact;
                var artifact = Artifact.from(lib.name);
                // We use the sha in the path because I don't give enough fucks to try and prevent duplication/locking.
                var path = artifact.getGroup().replace('.', '/') + artifact.getName() + '/' + artifact.getVersion() + '/' + info.sha1 + '/' + artifact.getFilename();
                var output = new File(cache, path);
                if (!output.exists()) {
                    if (!output.getParentFile().exists())
                        output.getParentFile().mkdirs();

                    var dl = new DownloadAction(getProject(), this);
                    dl.useETag("all");
                    dl.quiet(true);
                    dl.onlyIfModified(true);
                    dl.dest(output);
                    dl.src(info.url);
                    dl.execute().join();
                }
                libs.add(output);
            }
        }

        var libList = getOutput().getAsFile().get();
        if (!libList.getParentFile().exists())
            libList.getParentFile().mkdirs();

        var data = libs.stream().map(File::getAbsolutePath).collect(Collectors.joining("\r\n-e=", "-e=", "\r\n")).getBytes(StandardCharsets.UTF_8);
        if (libs.isEmpty())
            throw new IllegalStateException("No libraries found");
        Files.write(data, libList);
    }

    private List<File> extractBundled(ZipFile zip, BundleMetadata meta) throws IOException {
        var ret = new ArrayList<File>();
        var cache = new File(getCache().get());
        for (var lib : meta.libraries) {
            //listfile uses sha256, version json uses sha1. So lets calculate the hash ourselves.
            var entry = zip.getEntry("META-INF/libraries/" + lib.path);
            if (entry == null)
                throw new IOException("Bundle missing library: " + lib.path);
            var data = zip.getInputStream(entry).readAllBytes();
            var sha = HashFunction.SHA1.hash(data);
            var artifact = Artifact.from(lib.artifact);
            // We use the sha in the path because I don't give enough fucks to try and prevent duplication/locking.
            var path = artifact.getGroup().replace('.', '/') + artifact.getName() + '/' + artifact.getVersion() + '/' + sha + '/' + artifact.getFilename();
            var output = new File(cache, path);
            if (!output.exists()) {
                if (!output.getParentFile().exists())
                    output.getParentFile().mkdirs();
                Files.write(data, output);
            }
            ret.add(output);
        }
        return ret;
    }
}
