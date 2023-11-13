package net.minecraftforge.gradle.util;

import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.ApiStatus;

import com.google.common.collect.ComparisonChain;

import java.io.File;
import java.util.Comparator;
import java.util.Locale;
import javax.annotation.Nullable;

@ApiStatus.Internal
public class Artifact implements Comparable<Artifact> {
    // group:name:version[:classifier][@extension]
    private final String group;
    private final String name;
    private final String version;
    @Nullable
    private final String classifier;
    @Nullable
    private final String ext;

    // Cached after building the first time we're asked
    // Transient field so these aren't serialized
    @Nullable
    private transient String path;
    @Nullable
    private transient String file;
    @Nullable
    private transient String fullDescriptor;
    @Nullable
    private transient ComparableVersion comparableVersion;
    @Nullable
    private transient Boolean isSnapshot;

    public static Artifact from(String descriptor) {
        String group, name, version;
        String ext = null, classifier = null;

        String[] pts = descriptor.split(":");
        group = pts[0];
        name = pts[1];

        int last = pts.length - 1;
        int idx = pts[last].indexOf('@');
        if (idx != -1) { // we have an extension
            ext = pts[last].substring(idx + 1);
            pts[last] = pts[last].substring(0, idx);
        }

        version = pts[2];

        if (pts.length > 3) // We have a classifier
            classifier = pts[3];

        return new Artifact(group, name, version, classifier, ext);
    }

    public static Artifact from(String group, String name, String version) {
        return from(group, name, version, null, null);
    }

    public static Artifact from(String group, String name, String version, @Nullable String classifier, @Nullable String ext) {
        return new Artifact(group, name, version, classifier, ext);
    }

    Artifact(String group, String name, String version, @Nullable String classifier, @Nullable String ext) {
        this.group = group;
        this.name = name;
        this.version = version;
        this.classifier = classifier;
        this.ext = ext != null ? ext : "jar";
    }

    public String getLocalPath() {
        return getPath().replace('/', File.separatorChar);
    }

    public String getDescriptor() {
        if (fullDescriptor == null) {
            StringBuilder buf = new StringBuilder();
            buf.append(this.group).append(':').append(this.name).append(':').append(this.version);
            if (this.classifier != null)
                buf.append(':').append(this.classifier);
            if (ext != null && !"jar".equals(this.ext))
                buf.append('@').append(this.ext);
            this.fullDescriptor = buf.toString();
        }
        return fullDescriptor;
    }

    public String getPath() {
        if (path == null)
            this.path = String.join("/", this.group.replace('.', '/'), this.name, this.version, getFilename());
        return path;
    }

    public String getGroup() {
        return group;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    @Nullable
    public String getClassifier() {
        return classifier;
    }

    @Nullable
    public String getExtension() {
        return ext;
    }

    public String getFilename() {
        if (file == null) {
            String file;
            file = this.name + '-' + this.version;
            if (this.classifier != null) file += '-' + this.classifier;
            file += '.' + this.ext;
            this.file = file;
        }
        return file;
    }

    public boolean isSnapshot() {
        if (isSnapshot == null)
            this.isSnapshot = this.version.toLowerCase(Locale.ROOT).endsWith("-snapshot");
        return isSnapshot;
    }

    public Artifact withVersion(String version) {
        return Artifact.from(group, name, version, classifier, ext);
    }

    @Override
    public String toString() {
        return getDescriptor();
    }

    @Override
    public int hashCode() {
        return getDescriptor().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Artifact &&
                this.getDescriptor().equals(((Artifact) o).getDescriptor());
    }


    ComparableVersion getComparableVersion() {
        if (comparableVersion == null)
            this.comparableVersion = new ComparableVersion(this.version);
        return comparableVersion;
    }

    @Override
    public int compareTo(Artifact o) {
        return ComparisonChain.start()
                .compare(group, o.group)
                .compare(name, o.name)
                .compare(getComparableVersion(), o.getComparableVersion())
                // TODO: comparison of timestamps for snapshot versions (isSnapshot)
                .compare(classifier, o.classifier, Comparator.nullsFirst(Comparator.naturalOrder()))
                .compare(ext, o.ext, Comparator.nullsFirst(Comparator.naturalOrder()))
                .result();
    }
}
