package net.minecraftforge.gradle.util;

import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

public class FileHelper {
    private final Provider<Directory> root;

    public FileHelper(Project project, String... root) {
        var buildDir = project.getLayout().getBuildDirectory();
        if (root.length == 0)
            this.root = buildDir.map(d -> d);
        else
            this.root = buildDir.dir(String.join("/", root));
    }

    public FileHelper(Provider<Directory> base, String... root) {
        if (root.length == 0)
            this.root = base;
        else
            this.root = base.map(d -> d.dir(String.join("/", root)));
    }

    public Provider<Directory> dir() {
        return this.root;
    }

    public Provider<Directory> dir(String path) {
        return dir().map(d -> d.dir(path));
    }

    public Provider<Directory> dir(String... path) {
        return dir(String.join("/", path));
    }

    public Provider<RegularFile> file(String path) {
        return dir().map(d -> d.file(path));
    }

    public Provider<RegularFile> file(String... path) {
        return file(String.join("/", path));
    }
}
