package net.minecraftforge.gradle.mappings.tasks;

import java.io.IOException;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.mappings.McpNames;
import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.srgutils.IMappingFile;
import net.minecraftforge.srgutils.IRenamer;
import net.minecraftforge.srgutils.IMappingFile.IField;
import net.minecraftforge.srgutils.IMappingFile.IMethod;

@ApiStatus.Internal
public abstract class RenameMappingFile extends DefaultTask implements SingleFileOutput {
    @InputFile
    public abstract RegularFileProperty getMappings();

    @InputFile
    public abstract RegularFileProperty getNames();

    @Input
    public abstract Property<IMappingFile.Format> getFormat();

    @Input
    public abstract Property<Boolean> getReverse();

    public RenameMappingFile() {
        getFormat().convention(IMappingFile.Format.TSRG);
        getReverse().convention(false);
    }

    @TaskAction
    public void exec() throws IOException {
        var names = McpNames.load(getNames().getAsFile().get());
        var map = IMappingFile.load(getMappings().getAsFile().get());

        map.rename(new IRenamer() {
            @Override
            public String rename(IField value) {
                return names.rename(value.getMapped());
            }

            @Override
            public String rename(IMethod value) {
                return names.rename(value.getMapped());
            }
        });

        map.write(getOutputFile().toPath(), getFormat().get(), getReverse().get());
    }
}
