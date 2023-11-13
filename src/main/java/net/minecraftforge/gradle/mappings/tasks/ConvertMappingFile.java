package net.minecraftforge.gradle.mappings.tasks;

import java.io.IOException;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.srgutils.IMappingFile;

@ApiStatus.Internal
public abstract class ConvertMappingFile extends DefaultTask implements SingleFileOutput {
    public ConvertMappingFile() {
        getFormat().convention(IMappingFile.Format.TSRG2);
        getReverse().convention(false);
        getOutput().convention(getProject().getLayout().getBuildDirectory().dir(getName()).map(d -> d.file("output.tsrg")));
    }

    @InputFile
    public abstract RegularFileProperty getInput();

    @Input
    public abstract Property<IMappingFile.Format> getFormat();

    @Input
    public abstract Property<Boolean> getReverse();

    @TaskAction
    public void run() throws IOException {
        var in = IMappingFile.load(getInput().get().getAsFile());
        in.write(getOutput().get().getAsFile().toPath(), getFormat().get(), getReverse().get().booleanValue());
    }
}
