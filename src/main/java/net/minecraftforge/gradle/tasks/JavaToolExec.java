package net.minecraftforge.gradle.tasks;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.util.Util;

// We can't not directly use JavaExec because that disables caching. So we invoke it directly.
public abstract class JavaToolExec extends DefaultTask {
    public JavaToolExec() {
        var dir = getProject().getLayout().getBuildDirectory().dir(getName());
        this.getLog().convention(dir.map(d -> d.file("log.txt")));
        this.getJavaVersion().convention(JavaLanguageVersion.of("17"));
        this.getWorkDir().convention(dir.get().getAsFile().getAbsolutePath());
    }

    @InputFile
    public abstract RegularFileProperty getTool();

    @Input
    public abstract Property<JavaLanguageVersion> getJavaVersion();
    public void javaVersion(String value) {
        this.getJavaVersion().set(JavaLanguageVersion.of(value));
    }

    @Input
    public abstract ListProperty<String> getArgs();
    public void args(String... args) {
        this.getArgs().set(Arrays.asList(args));
    }

    @Input
    @Optional
    public abstract ListProperty<String> getJvmArgs();
    public void jvmArgs(String... args) {
        this.getJvmArgs().set(Arrays.asList(args));
    }

    @Input
    @Optional
    public abstract Property<String> getWorkDir();

    @OutputFile
    public abstract RegularFileProperty getLog();

    @Inject
    public abstract JavaToolchainService getJavaToolchainService();

    @TaskAction
    public void exec() {
        try {
            execUnsafe();
        } catch (Throwable e) {
            Util.sneakyThrow(e);;
        }
    }

    public void execUnsafe() throws IOException {
        var logFile = getLog().get().getAsFile();
        var tool = getTool().get().getAsFile();
        var workDir = new File(getWorkDir().get());
        var main = getMainClass(tool);

        if (!logFile.getParentFile().exists())
            logFile.getParentFile().mkdirs();

        try (var log = new PrintWriter(new FileWriter(logFile), true)) {
            var launcher = getJavaToolchainService().launcherFor(spec -> spec.getLanguageVersion().set(getJavaVersion())).get();
            var args = filterArgs(getArgs().get());
            List<String> jvmArgs = getJvmArgs().isPresent() ? filterJvmArgs(getJvmArgs().get()) : Collections.emptyList();
            log.println("Java:      " + launcher.getExecutablePath().getAsFile().getAbsolutePath());
            log.println("Arguments: " + args.stream().collect(Collectors.joining(", ", "'", "'")));
            log.println("JVMArgs:   " + jvmArgs.stream().collect(Collectors.joining(", ", "'", "'")));
            log.println("Classpath: " + tool.getAbsolutePath());
            log.println("Main:      " + main);
            log.println("Work Dir:  " + workDir.getAbsolutePath());
            log.println("====================================");

            var output = new OutputStream() {
                @Override public void flush() { log.flush(); }
                @Override public void close() {}
                @Override public void write(int b) { log.write(b); }
            };

            this.getProject().javaexec(exec -> {
                exec.setExecutable(launcher.getExecutablePath().toString());
                exec.setClasspath(getProject().files(tool));
                exec.getMainClass().set(main);
                exec.setArgs(args);
                if (!jvmArgs.isEmpty())
                    exec.setJvmArgs(jvmArgs);
                exec.setWorkingDir(workDir);
                exec.setStandardOutput(output);
                exec.setErrorOutput(output);
            }).rethrowFailure().assertNormalExitValue();

            log.flush();

        }

        if (this instanceof SingleFileOutput sfo) {
            var output = sfo.getOutput().get().getAsFile();
            if (!output.exists())
                throw new IllegalStateException("Failed to write output file: " + output.getAbsolutePath());
        }
    }

    private String getMainClass(File tool) throws IOException {
        try (var jar = new JarFile(tool)) {
            return jar.getManifest().getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        }
    }

    protected List<String> filterArgs(List<String> args) {
        return args;
    }

    protected List<String> filterJvmArgs(List<String> args) {
        return args;
    }

    @ApiStatus.Internal
    public static abstract class WithOutput extends JavaToolExec implements SingleFileOutput {}
}
