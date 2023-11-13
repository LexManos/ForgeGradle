package net.minecraftforge.gradle.pipelines.mcp.tasks;

import java.io.IOException;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import codechicken.diffpatch.cli.PatchOperation;
import codechicken.diffpatch.util.ConsumingOutputStream;
import codechicken.diffpatch.util.LogLevel;
import codechicken.diffpatch.util.PatchMode;
import net.minecraftforge.gradle.tasks.SingleFileOutput;

public abstract class ApplyZippedPatches extends DefaultTask implements SingleFileOutput {
    public ApplyZippedPatches() {
        getMode().convention(PatchMode.OFFSET);
    }

    @InputFile
    public abstract RegularFileProperty getInput();

    @InputFile
    public abstract RegularFileProperty getArchive();

    @Input
    @Optional
    public abstract Property<String> getArchivePrefix();

    @Input
    @Optional
    public abstract Property<String> getOriginalPrefix();

    @Input
    @Optional
    public abstract Property<String> getModifiedPrefix();

    @Input
    public abstract Property<PatchMode> getMode();

    @OutputFile
    @Optional
    public abstract RegularFileProperty getRejects();

    @TaskAction
    public void exec() throws IOException {
        var input = getInput().getAsFile().get();
        var mcp = getArchive().getAsFile().get();
        var output = getOutputFile();
        var rejects = getRejects().getAsFile().get();

        var builder = PatchOperation.builder()
            .logTo(new ConsumingOutputStream(getLogger()::lifecycle))
            .basePath(input.toPath())
            .patchesPath(mcp.toPath())
            .outputPath(output.toPath())
            .mode(getMode().get())
            .level(LogLevel.ERROR);

        if (getArchivePrefix().isPresent())
            builder.patchesPrefix(getArchivePrefix().get());

        if (getRejects().isPresent())
            builder.rejectsPath(getRejects().getAsFile().get().toPath());

        if (getOriginalPrefix().isPresent())
            builder.aPrefix(getOriginalPrefix().get());

        if (getModifiedPrefix().isPresent())
            builder.bPrefix(getModifiedPrefix().get());

        var result = builder.build().operate();

        boolean success = result.exit == 0;
        if (!success) {
            getLogger().error("Rejects saved to: {}", rejects);
            result.summary.print(System.out, true);
            throw new RuntimeException("Patch failure.");
        }
    }
}
