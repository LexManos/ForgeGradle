package net.minecraftforge.gradle.tasks;

import java.io.File;

import org.gradle.api.Task;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;

public interface SingleFileOutput extends Task {
    @OutputFile
    RegularFileProperty getOutput();

    // Just a helper functions cuz I do this a lot
    @Internal
    default File getOutputFile() {
        return getOutput().get().getAsFile();
    }

    @Internal
    default Provider<Directory> buildDir() {
        return getProject().getLayout().getBuildDirectory().dir(getName());
    }

    @Internal
    default Provider<RegularFile> buildDir(String file) {
        return buildDir().map(d -> d.file(file));
    }
}
