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
public abstract class ChainMappingFiles extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getLeft();

    @Input
    public abstract Property<Boolean> getReverseLeft();

    @InputFile
    public abstract RegularFileProperty getRight();
    @Input
    public abstract Property<Boolean> getReverseRight();

    @Input
    public abstract Property<IMappingFile.Format> getFormat();

    @Input
    public abstract Property<Boolean> getReverseOutput();

    public ChainMappingFiles() {
        getFormat().convention(IMappingFile.Format.TSRG);
        getReverseLeft().convention(false);
        getReverseRight().convention(false);
        getReverseOutput().convention(false);
    }

    @TaskAction
    public void exec() throws IOException {
        var left = IMappingFile.load(getLeft().getAsFile().get());
        if (getReverseLeft().get())
            left = left.reverse();

        var right = IMappingFile.load(getRight().getAsFile().get());
        if (getReverseRight().get())
            right = right.reverse();

        var output = left.chain(right);
        output.write(getOutputFile().toPath(), getFormat().get(), getReverseOutput().get());
    }
}
