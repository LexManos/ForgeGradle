package net.minecraftforge.gradle.pipelines.vanilla.tasks;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;

import net.minecraftforge.gradle.tasks.JavaToolExec;
import net.minecraftforge.gradle.tasks.SingleFileOutput;

public abstract class MergeJarFile extends JavaToolExec implements SingleFileOutput {
    public MergeJarFile() {
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file(getName() + ".jar")));
    }

    @InputFile
    public abstract RegularFileProperty getClient();

    @InputFile
    public abstract RegularFileProperty getServer();

    @Override
    protected List<String> filterArgs(List<String> args) {
        var values = Map.of(
            "{client}", getClient().get().getAsFile().getAbsolutePath(),
            "{server}", getClient().get().getAsFile().getAbsolutePath(),
            "{output}", getOutput().get().getAsFile().getAbsolutePath()
        );
        return args.stream().map(p -> values.getOrDefault(p, p)).collect(Collectors.toList());
    }
}
