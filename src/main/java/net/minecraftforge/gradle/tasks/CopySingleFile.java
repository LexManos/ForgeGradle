package net.minecraftforge.gradle.tasks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;

public abstract class CopySingleFile extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getInput();

    @TaskAction
    public void run() throws IOException {
        Files.copy(getInput().get().getAsFile().toPath(), getOutputFile().toPath(), StandardCopyOption.REPLACE_EXISTING);
    }
}
