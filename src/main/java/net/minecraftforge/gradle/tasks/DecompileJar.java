package net.minecraftforge.gradle.tasks;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;

public abstract class DecompileJar extends JavaToolExec implements SingleFileOutput {
    public DecompileJar() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file(getName() + ".jar")));
    }

    @InputFile
    public abstract RegularFileProperty getInput();

    @InputFile
    public abstract RegularFileProperty getLibraries();

    @Override
    protected List<String> filterArgs(List<String> args) {
        var values = Map.of(
            "{input}", getInput().get().getAsFile().getAbsolutePath(),
            "{output}", getOutput().get().getAsFile().getAbsolutePath(),
            "{libraries}", getLibraries().get().getAsFile().getAbsolutePath()
        );
        return args.stream().map(p -> values.getOrDefault(p, p)).collect(Collectors.toList());
    }
}
