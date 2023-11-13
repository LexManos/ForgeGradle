package net.minecraftforge.gradle.tasks;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.jvm.tasks.Jar;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public abstract class JarWithOutput extends Jar implements SingleFileOutput {
    @Override
    public RegularFileProperty getOutput() {
        return (RegularFileProperty)this.getArchiveFile();
    }
}