package net.minecraftforge.gradle.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipFile;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class BundleMetadata {
    private static final Attributes.Name BUNDLER_FORMAT = new Attributes.Name("Bundler-Format");
    private static final String VERSIONS_LIST = "META-INF/versions.list";
    private static final String LIBRARIES_LIST = "META-INF/libraries.list";
    // classpath-joined, main-class

    @Nullable
    public static BundleMetadata read(ZipFile file) throws IOException {
        var mfEntry = file.getEntry(JarFile.MANIFEST_NAME);
        if (mfEntry == null)
            return null;
        var mf = new Manifest(file.getInputStream(mfEntry));
        var format = mf.getMainAttributes().getValue(BUNDLER_FORMAT);
        if (format == null)
            return null;

        if (!"1.0".equals(format))
            throw new IOException("Unsupported Bundler-Format: " + format);

        var verEntry = file.getEntry(VERSIONS_LIST);
        if (verEntry == null)
            throw new IllegalStateException("Bundled Jar missing " + VERSIONS_LIST);

        var verList = readList(file.getInputStream(verEntry));
        if (verList.size() != 1)
            throw new IllegalStateException("Invalid bundler " + VERSIONS_LIST + " file, " + verList.size() + " entries, expected 1");

        var libEntry = file.getEntry(LIBRARIES_LIST);
        List<BundleMetadata.Entry> libList = null;
        if (libEntry != null)
            libList = readList(file.getInputStream(libEntry));

        return new BundleMetadata(format, verList, libList);
    }

    private static List<BundleMetadata.Entry> readList(InputStream in) throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(in))) {
            var ret = new ArrayList<BundleMetadata.Entry>();
            while (reader.ready()) {
                var line = reader.readLine();
                var pts = line.split("\t");
                if (pts.length != 3)
                    throw new IOException("Invalid bunder list line: " + line);
                ret.add(new Entry(pts[0], pts[1], pts[2]));
            }
            return ret;
        }
    }

    public final String format;
    public final List<BundleMetadata.Entry> versions;
    public final List<BundleMetadata.Entry> libraries;

    private BundleMetadata(String format, List<BundleMetadata.Entry> versions, @Nullable List<BundleMetadata.Entry> libraries) {
        this.format = format;
        this.versions = versions;
        this.libraries = libraries == null ? Collections.emptyList() : libraries;
    }

    public static class Entry {
        public final String hash;
        public final String artifact;
        public final String path;

        public Entry(String hash, String artifact, String path) {
            this.hash = hash;
            this.artifact = artifact;
            this.path = path;
        }
    }
}