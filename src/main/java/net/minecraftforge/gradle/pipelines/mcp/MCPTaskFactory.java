package net.minecraftforge.gradle.pipelines.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.gradle.api.Task;
import org.gradle.jvm.toolchain.JavaLanguageVersion;

import net.minecraftforge.gradle.ForgeGradleExtension;
import net.minecraftforge.gradle.data.MCPConfig;
import net.minecraftforge.gradle.pipelines.mcp.MCP.ExtractTask;
import net.minecraftforge.gradle.pipelines.mcp.tasks.ApplyZippedPatches;
import net.minecraftforge.gradle.pipelines.mcp.tasks.Inject;
import net.minecraftforge.gradle.pipelines.mcp.tasks.ListBundledLibraries;
import net.minecraftforge.gradle.pipelines.mcp.tasks.ListJsonLibraries;
import net.minecraftforge.gradle.pipelines.mcp.tasks.ListSourcePackages;
import net.minecraftforge.gradle.pipelines.mcp.tasks.StripJar;
import net.minecraftforge.gradle.tasks.DownloadMavenArtifactManual;
import net.minecraftforge.gradle.tasks.JavaToolExec;
import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.tasks.archive.OrderedMergeJars;
import net.minecraftforge.gradle.util.FileHelper;

public abstract class MCPTaskFactory {
    private final ForgeGradleExtension fg;
    private final FileHelper local;
    private final MCP mcp;
    private final String side;

    public MCPTaskFactory(ForgeGradleExtension fg, FileHelper local, MCPSide mcpside) {
        this.fg = fg;
        this.local = local;
        this.mcp = mcpside.mcp;
        this.side = mcpside.side;
    }

    protected abstract <T extends Task> T task(String name, Class<T> type);

    public SingleFileOutput getTask(Map<String, SingleFileOutput> context, String name) {
        if (!name.startsWith("{") || !name.endsWith("Output}"))
            throw new IllegalArgumentException("Failed to find task output, invalid identifier: " + name);
        var taskName = name.substring(1, name.length() - 7);
        var ret = context.get(taskName);
        if (ret == null)
            throw new IllegalArgumentException("Failed to find task output, task `" + taskName + "` not defined");
        return ret;
    }

    public SingleFileOutput createTask(Map<String, ExtractTask> extracts, Map<String, SingleFileOutput> context, Map<String, String> step) {
        var data = mcp.config.getData(side);
        var type = step.get("type");
        var name = step.getOrDefault("name", type);
        switch (type) {
            case "downloadManifest":
                return this.fg.getMcTasks().getLauncherManifest();
            case "downloadJson":
                return this.fg.getMcTasks().getVersionJson(mcp.config.version);
            case "downloadClient":
                return this.fg.getMcTasks().getVersionFile(mcp.config.version, "client", "jar");
            case "downloadServer":
                return this.fg.getMcTasks().getVersionFile(mcp.config.version, "server", "jar");
            case "strip":
                return stripJar(name, context, data, step);
            case "listLibraries":
                if (step.containsKey("bundle"))
                    return listLibrariesBundle(name, context, data, step);
                return listLibraries(name, context, data, step);
            case "inject":
                return inject(name, context, data, step);
            case "patch":
                return patch(name, context, data, step);
        }

        if (mcp.config.spec >= 2) {
            switch (type) {
                case "downloadClientMappings":
                    return this.fg.getMcTasks().getVersionFile(mcp.config.version, "client_mappings", "txt");
                case "downloadServerMappings":
                    return this.fg.getMcTasks().getVersionFile(mcp.config.version, "server_mappings", "txt");
            }
        }

        var custom = mcp.config.functions == null ? null : mcp.config.functions.get(type);

        if (custom == null)
            throw new IllegalArgumentException("Invalid MCP Config: `" + mcp.data + "` - Unknown step type: " + type);

        return execute(name, context, data, step, extracts, custom);
    }

    private SingleFileOutput stripJar(String name, Map<String, SingleFileOutput> context, Map<String, String> data, Map<String, String> step) {
        var task = task(name, StripJar.class);
        task.getMcp().fileValue(mcp.data);
        task.getInput().convention(getTask(context, step.get("input")).getOutput());
        task.getWhitelist().convention(step.getOrDefault("mode", "whitelist").equalsIgnoreCase("whitelist"));
        task.getMappings().convention(data.get("mappings"));
        task.getOutput().convention(local.file(name, name + ".jar"));
        return task;
    }

    // TODO: [FG][MCP][ListJsonLibraries] Better task caching with known outputs?
    private SingleFileOutput listLibraries(String name, Map<String, SingleFileOutput> context, Map<String, String> data, Map<String, String> step) {
        var task = task(name, ListJsonLibraries.class);
        var input = context.get("downloadJson");
        if (input == null)
            throw new IllegalArgumentException("Failed to find task output, task `downloadJson` not defined");

        task.getJson().convention(input.getOutput());
        task.getLibraryDir().convention(local.dir(name, "libraries"));
        task.getOutput().convention(local.file(name, "libraries.txt"));
        return task;
    }

    // TODO: [FG][MCP][ListBundledLibraries] Better task caching with known outputs?
    private SingleFileOutput listLibrariesBundle(String name, Map<String, SingleFileOutput> context, Map<String, String> data, Map<String, String> step) {
        var task = task(name, ListBundledLibraries.class);
        task.getBundle().convention(getTask(context, step.get("bundle")).getOutput());
        task.getLibraryDir().convention(local.dir(name, "libraries"));
        task.getOutput().convention(local.file(name, "libraries.txt"));
        return task;
    }

    private SingleFileOutput inject(String name, Map<String, SingleFileOutput> context, Map<String, String> data, Map<String, String> step) {
        var input = getTask(context, step.get("input")).getOutput();

        var list = task(name + "ListPackages", ListSourcePackages.class);
        list.getInput().convention(input);
        list.getOutput().convention(local.file(name, "packages.txt"));

        var _new = task(name + "NewSources", Inject.class);
        _new.getMcp().fileValue(mcp.data);
        _new.getPackages().convention(list.getOutput());
        _new.getPrefix().convention(data.get("inject"));
        _new.getSide().convention(side);
        _new.getOutput().convention(local.file(name, "sources.jar"));

        var task = task(name, OrderedMergeJars.class);
        task.getArchives().add(_new.getOutput());
        task.getArchives().add(input);
        task.getOutput().convention(local.file(name, "merged.jar"));
        return task;
    }

    private SingleFileOutput patch(String name, Map<String, SingleFileOutput> context, Map<String, String> data, Map<String, String> step) {
        var task = task(name, ApplyZippedPatches.class);
        task.getArchive().fileValue(mcp.data);
        task.getInput().convention(getTask(context, step.get("input")).getOutput());
        task.getArchivePrefix().convention(data.get("patches"));
        task.getOutput().convention(local.file(name, "patched.jar"));
        task.getRejects().convention(local.file(name, "rejects.jar"));
        return task;
    }

    private SingleFileOutput execute(String name, Map<String, SingleFileOutput> context, Map<String, String> data, Map<String, String> step,
        Map<String, ExtractTask> extracts,
        MCPConfig.Function func
    ) {
        var args = new HashMap<String, String>();
        args.putAll(step);

        // Find any inputs from previous tasks
        var deps = new HashSet<Task>();
        for (var key : step.keySet()) {
            var value = step.get(key);
            if (!isVariable(value))
                continue;

            if (value.endsWith("Output}")) {
                var task = getTask(context, value);
                deps.add(task);
                args.put(key, task.getOutputFile().getAbsolutePath());
            } else {
                var data_name = value.substring(1, value.length() - 1);
                var extract = extracts.get(data_name);
                if (extract == null)
                    throw new IllegalStateException("Invalid execution argument `" + value + "` no substitution found");
                args.put(data_name, extract.path());
                deps.add(extract.task());
            }
        }

        // Add the outputs to the possible arg lists
        var ext = step.getOrDefault("outputExtension", "jar");
        var output = local.file(name, name + '.' + ext);
        var log = local.file(name, "log.txt");
        args.put("output", output.get().getAsFile().getAbsolutePath());
        args.put("log", log.get().getAsFile().getAbsolutePath());
        // Fill substitutions in arguments, also builds dependencies on extract tasks
        var jvmArgs = fillArgs(func.jvmargs, args, extracts, deps);
        var runArgs = fillArgs(func.args, args, extracts, deps);

        int java_version = func.java_version != null ? func.java_version : mcp.config.java_target;

        var dl = task(name + "_download", DownloadMavenArtifactManual.class);
        dl.getArtifact().convention(func.version);
        dl.getMaven().convention(func.repo);
        dl.getOutput().convention(local.file(name, "tool.jar"));

        var task = task(name, JavaToolExec.WithOutput.class);
        task.dependsOn(deps);
        task.getTool().convention(dl.getOutput());
        task.getJavaVersion().convention(JavaLanguageVersion.of(java_version));
        task.getJvmArgs().addAll(jvmArgs);
        task.getArgs().addAll(runArgs);
        task.getOutput().convention(output);
        task.getLog().convention(log);
        task.getWorkDir().convention(local.dir(name).get().toString());

        return task;
    }

    private boolean isVariable(String value) {
        return value.startsWith("{") && value.endsWith("}");
    }

    private List<String> fillArgs(List<String> lst, Map<String, String> args, Map<String, ExtractTask> extracts, Set<Task> deps) {
        if (lst == null)
            return List.of();
        var ret = new ArrayList<String>(lst.size());
        for (var value : lst) {
            if (!isVariable(value)) {
                ret.add(value);
            } else {
                var data_name = value.substring(1, value.length() - 1);
                var arg = args.get(data_name);
                if (arg != null) {
                    ret.add(arg);
                } else {
                    var extract = extracts.get(data_name);
                    if (extract == null)
                        throw new IllegalStateException("Invalid execution argument `" + value + "` no substitution found");
                    ret.add(extract.path());
                    deps.add(extract.task());
                }
            }
        }
        return ret;
    }
}
