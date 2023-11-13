package net.minecraftforge.gradle.pipelines.patcher;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.zip.ZipFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import codechicken.diffpatch.util.PatchMode;
import net.minecraftforge.gradle.data.JsonData;
import net.minecraftforge.gradle.data.PatcherConfig;
import net.minecraftforge.gradle.mappings.tasks.RenameSources;
import net.minecraftforge.gradle.pipelines.mcp.MCP;
import net.minecraftforge.gradle.pipelines.mcp.MCPSide;
import net.minecraftforge.gradle.pipelines.mcp.MCPTaskFactory;
import net.minecraftforge.gradle.pipelines.mcp.tasks.ApplyZippedPatches;
import net.minecraftforge.gradle.pipelines.mcp.tasks.Inject;
import net.minecraftforge.gradle.pipelines.patcher.tasks.InjectPatcherSources;
import net.minecraftforge.gradle.pipelines.patcher.tasks.ListBinaryPackages;
import net.minecraftforge.gradle.repo.RepositoryArtifact;
import net.minecraftforge.gradle.tasks.CreateIvyMetadata;
import net.minecraftforge.gradle.tasks.DownloadMavenArtifact;
import net.minecraftforge.gradle.tasks.DownloadMavenArtifactManual;
import net.minecraftforge.gradle.tasks.JarWithOutput;
import net.minecraftforge.gradle.tasks.JavaToolExec;
import net.minecraftforge.gradle.tasks.RenameJarFile;
import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.tasks.archive.ExtractZipFile;
import net.minecraftforge.gradle.tasks.archive.FilterArchive;
import net.minecraftforge.gradle.tasks.archive.OrderedMergeJars;
import net.minecraftforge.gradle.tasks.archive.SubArchive;
import net.minecraftforge.gradle.util.Artifact;
import net.minecraftforge.gradle.util.FileHelper;
import net.minecraftforge.gradle.util.HashFunction;
import net.minecraftforge.gradle.util.Util;

@ApiStatus.Internal
class Patcher {
    private final PatcherPipeline pipeline;
    private final Artifact name;
    private final String prefix;
    private final File data;
    private final PatcherConfig.V2 config;
    private final Map<String, SingleFileOutput> extracts = new HashMap<>();
    private Patcher parent;
    private MCP mcp;
    private final FileHelper local;
    private Collection<SingleFileOutput> ats;
    private Collection<SingleFileOutput> sas;

    private SingleFileOutput sources;
    private SingleFileOutput universal;
    private SingleFileOutput universalFiltered;
    private SingleFileOutput userdevInject;
    private SingleFileOutput predecomp;
    private SingleFileOutput last;
    private boolean setup = false;

    Patcher(PatcherPipeline pipeline, String name, File userdev) {
        this.pipeline = pipeline;
        this.name = Artifact.from(name);
        this.prefix = Util.sanitizeTaskName(this.name.getName() + '_' + this.name.getVersion());
        this.data = userdev;
        this.config = loadConfig(userdev);
        this.local = new FileHelper(this.pipeline.global.dir(), this.name.getName(), this.name.getVersion());
        if (this.config.parent != null)
            this.parent = this.pipeline.getPatcher(this.config.parent);
        else if (this.config.mcp != null)
            this.mcp = this.pipeline.fg.getMcpPipeline().getMcp(this.config.mcp, "joined");
        else
            throw new IllegalStateException("Invalid patcher config: " + name + " - Missing `parent` or `mcp`");
    }

    private PatcherConfig.V2 loadConfig(File data) {
        try (var zip = new ZipFile(data)) {
            var entry = zip.getEntry("config.json");
            if (entry == null)
                throw new IllegalStateException("Invalid patcher dependency: " + name + " - Missing config.json");
            var cfg_data = zip.getInputStream(entry).readAllBytes();

            int spec = JsonData.configSpec(cfg_data);

            if (spec == 1)
                return new PatcherConfig.V2(JsonData.patcherConfig(cfg_data));
            else if (spec == 2)
                return JsonData.patcherConfigV2(cfg_data);
            else
                throw new IllegalStateException("Invalid patcher dependency: " + name + " - Unknown Spec: " + spec);

        } catch (IOException e) {
            throw new RuntimeException("Invalid patcher dependency: " + name, e);
        }
    }

    private MCP getMcp() {
        return this.mcp == null ? this.parent.getMcp() : this.mcp;
    }

    public Stack<Patcher> getStack() {
        return getStack(true);
    }

    public Stack<Patcher> getParents() {
        return getStack(false);
    }

    private Stack<Patcher> getStack(boolean includeSelf) {
        var stack = new Stack<Patcher>();
        if (includeSelf)
            stack.add(this);
        var parent = this.parent;
        while (parent != null) {
            stack.add(parent);
            parent = parent.parent;
        }
        return stack;
    }

    private <T extends Task> T task(String name, Class<T> type) {
        name = "_patcher_" + this.prefix + '_' + name;
        var ret = this.pipeline.project.getTasks().create(name, type);
        ret.setGroup("Forge Gradle Internal - Patcher Pipeline - " + this.name);
        return ret;
    }

    void setupTasks() {
        if (setup)
            return;
        setup = true;

        var mcp = this.getMcp();
        var mcpSide = mcp.getSide("joined");

        if (parent != null) {
            parent.setupTasks();
            predecomp = parent.predecomp;
            last = parent.last;
        } else {
            predecomp = mcpSide.predecomp();
            last = mcpSide.last();
        }
        var parentPre = predecomp;

        // Apply access transformers if we have any
        if (config.ats != null && !config.ats.isEmpty()) {
            ats = extract("extractAt", "accessTransformer", config.ats);
            predecomp = modifyAccess("applyAccessTransformation", predecomp, ats);
        }

        // Apply Side Annotation Strippers if we have any
        if (config.sass != null && config.sass.isEmpty()) {
            sas = extract("extractSas", "sideStripper", config.sass);
            predecomp = stripSideAnnotations("applySideStripper", predecomp, sas);
        }

        var stack = new Stack<Patcher>();
        stack.add(this);

        // If we changed the decompile input, rebuild decompile and subsequent tasks
        if (predecomp != parentPre) {
            last = completeMcp(predecomp);
            stack = this.getStack();
        }

        int parents = 0;
        while (!stack.isEmpty()) {
            var patcher = stack.pop();
            if (patcher.config.processor != null)
                last = postProcessor(mcp, patcher, last, parents);
            if (patcher.config.patches != null)
                last = patch(patcher, last, parents);
            if (patcher.config.sources != null)
                last = inject(patcher, last, parents);
            parents++;
        }
    }

    private List<SingleFileOutput> extract(String taskPrefix, String filePrefix, List<String> files) {
        var ret = new ArrayList<SingleFileOutput>();
        for (int x = 0; x < files.size(); x++) {
            var at = files.get(x);
            var task = task(taskPrefix + (x == 0 ? "" : x), ExtractZipFile.class);
            task.getZip().set(this.data);
            task.getInternalPath().set(at);
            task.getOutput().set(local.file(filePrefix + (x == 0 ? "" : x) + ".cfg"));
            ret.add(task);
        }
        return ret;
    }

    private SingleFileOutput modifyAccess(String name, SingleFileOutput input, Collection<SingleFileOutput> cfgs) {
        var task = task(name, JavaToolExec.WithOutput.class);
        var args = new ArrayList<String>();
        args.add("--inJar");
        args.add(input.getOutputFile().getAbsolutePath());
        task.getInputs().file(input.getOutput());

        for (var cfg : cfgs) {
            args.add("--atfile");
            args.add(cfg.getOutputFile().getAbsolutePath());
            task.getInputs().file(cfg.getOutput());
        }

        var output = local.file(name + ".jar");
        args.add("--outJar");
        args.add(output.get().getAsFile().getAbsolutePath());
        task.getOutput().set(output);

        task.getTool().convention(this.pipeline.dlAt.getOutput());
        task.getArgs().convention(args);
        task.getLog().convention(local.file(name + ".jar.log"));
        task.getWorkDir().convention(local.dir().get().toString());
        return task;
    }

    private SingleFileOutput stripSideAnnotations(String name, SingleFileOutput input, Collection<SingleFileOutput> cfgs) {
        var task = task(name, JavaToolExec.WithOutput.class);
        var args = new ArrayList<String>();
        args.add("--input");
        args.add(input.getOutputFile().getAbsolutePath());
        task.getInputs().file(input.getOutput());

        for (var cfg : cfgs) {
            args.add("--data");
            args.add(cfg.getOutputFile().getAbsolutePath());
            task.getInputs().file(cfg.getOutput());
        }

        var output = local.file(name + ".jar");
        args.add("--output");
        args.add(output.get().getAsFile().getAbsolutePath());
        task.getOutput().set(output);

        task.getTool().convention(this.pipeline.dlMergeTool.getOutput());
        task.getArgs().convention(args);
        task.getLog().convention(local.file(name + ".jar.log"));
        task.getWorkDir().convention(local.dir().get().toString());
        return task;
    }

    private MCPTaskFactory getTaskFactory(MCPSide mcp) {
        return new MCPTaskFactory(this.pipeline.fg, new FileHelper(this.local.dir(), "_mcp"), mcp) {
            @Override
            protected <T extends Task> T task(String name, Class<T> type) {
                return Patcher.this.task("mcp_" + name, type);
            }
        };
    }

    private SingleFileOutput completeMcp(SingleFileOutput input) {
        var mcp = getMcp();
        var side = mcp.getSide("joined");
        var taskFactory = getTaskFactory(side);
        var steps = mcp.getConfig().steps.get("joined");
        var tasks = new LinkedHashMap<>(side.steps());
        var foundDecomp = false;
        SingleFileOutput last = null;

        for (int x = 0; x < steps.size(); x++) {
            var step = steps.get(x);
            var type = step.get("type");
            var name = step.getOrDefault("name", type);
            var isDecomp = "decompile".equals(name);
            if (!foundDecomp && !isDecomp)
                continue;

            foundDecomp = true;
            // Replace the decomp input with ours
            if (isDecomp) {
                var inputTask = step.get("input");
                inputTask = inputTask.substring(1, inputTask.length() - 7);
                tasks.put(inputTask, input);
            }

            var task = taskFactory.createTask(side.extracts(), tasks, step);
            tasks.put(name, task);
            last = task;
        }
        return last;
    }

    private SingleFileOutput extract(String path) {
        return this.extracts.computeIfAbsent(path, k -> {
            var name = "extract" + HashFunction.CRC32.hash(path);
            var extract = task(name, ExtractZipFile.class);
            extract.getZip().set(this.data);
            extract.getInternalPath().set(path);
            extract.getOutput().convention(local.file("data", path));
            return extract;
        });
    }

    private SingleFileOutput postProcessor(MCP mcp, Patcher info, SingleFileOutput input, int index) {
        var data = info.config.processor;
        var dir = "postProcess";
        if (index != 0)
            dir += index;

        var dlTool = task(dir + "Download", DownloadMavenArtifactManual.class);
        dlTool.getArtifact().convention(data.version);
        dlTool.getMaven().convention(data.repo);
        dlTool.getOutput().convention(local.file(dir, "tool.jar"));

        var task = task(dir, JavaToolExec.WithOutput.class);
        task.getJvmArgs().addAll(data.jvmargs);
        task.getTool().convention(dlTool.getOutput());
        task.getOutput().convention(local.file(dir, "output.jar"));
        task.getLog().convention(local.file(dir, "log.txt"));
        task.getWorkDir().convention(local.dir(dir).get().toString());
        int java_version = data.java_version != null ? data.java_version : mcp.getConfig().java_target;
        task.getJavaVersion().convention(JavaLanguageVersion.of(java_version));

        // Create tasks to extract any data files that may need extracting
        var extracts = new HashMap<String, String>();
        for (var entry : data.data.entrySet()) {
            var extract = info.extract(entry.getValue());
            task.dependsOn(extract);
            extracts.put('{' + entry.getKey() + '}', extract.getOutputFile().getAbsolutePath());
        }

        var args = new ArrayList<String>();
        for (var arg : data.args) {
            if ("{input}".equals(arg)) {
                args.add(input.getOutputFile().getAbsolutePath());
                task.getInputs().file(input.getOutputFile());
            } else if ("{output}".equals(arg))
                args.add(task.getOutputFile().getAbsolutePath());
            else
                args.add(extracts.getOrDefault(arg, arg));
        }
        task.getArgs().addAll(args);
        return task;
    }

    private SingleFileOutput patch(Patcher info, SingleFileOutput input, int index) {
        var name = "applyPatches";
        if (index != 0)
            name += index;

        var task = task(name, ApplyZippedPatches.class);
        task.getArchive().fileValue(info.data);
        task.getInput().convention(input.getOutput());
        task.getArchivePrefix().convention(info.config.patches);
        task.getOutput().convention(local.file("sourcesPatched" + (index == 0 ? "" : index) + ".jar"));
        task.getRejects().convention(local.file("sourcesPatched" + (index == 0 ? "" : index) + "Rejects.jar"));
        task.getMode().convention(PatchMode.OFFSET);

        if (info.config.patchesOriginalPrefix != null)
            task.getOriginalPrefix().convention(info.config.patchesOriginalPrefix);

        if (info.config.patchesModifiedPrefix != null)
            task.getModifiedPrefix().convention(info.config.patchesModifiedPrefix);

        return task;
    }

    private SingleFileOutput downloadSource() {
        if (sources == null) {
            var task = task("downloadSource", DownloadMavenArtifact.class);
            task.getArtifact().convention(config.sources);
            task.getOutput().convention(local.file("sources.jar"));
            sources = task;
        }
        return sources;
    }

    private SingleFileOutput downloadUniversal() {
        if (universal == null) {
            var task = task("downloadUniversal", DownloadMavenArtifact.class);
            task.getArtifact().convention(config.universal);
            task.getOutput().convention(local.file("universal.jar"));
            universal = task;
        }
        return universal;
    }

    private SingleFileOutput filterUniversal() {
        if (universalFiltered == null) {
            if (config.universalFilters == null || config.universalFilters.isEmpty())
                universalFiltered = downloadUniversal();
            else {
                var task = task("filterUniversal", FilterArchive.class);
                task.getInput().convention(downloadUniversal().getOutput());
                config.universalFilters.forEach(task.getFilters()::add);
                task.getWhitelist().set(true);
                task.getOutput().convention(local.file("universalFiltered.jar"));
                universalFiltered = task;
            }
        }
        return universalFiltered;
    }

    private SingleFileOutput userdevInjection() {
        if (userdevInject == null && config.inject != null) {
            var task = task("userdevInjectJar", SubArchive.class);
            task.getInput().set(this.data);
            task.getPrefix().set(config.inject);
            task.getOutput().convention(local.file("userdev-inject.jar"));
            userdevInject = task;
        }
        return userdevInject;
    }

    private SingleFileOutput inject(Patcher info, SingleFileOutput input, int index) {
        var name = "inject";
        if (index != 0)
            name += index;

        var task = task(name, InjectPatcherSources.class);
        task.getInput().convention(input.getOutput());
        task.getArchive().convention(info.downloadSource().getOutput());
        task.getOutput().convention(local.file("sourcesInjected" + (index == 0 ? "" : index) + ".jar"));
        return task;
    }

    List<RepositoryArtifact> buildArtifacts(Dependency dep, boolean sources) {
        setupTasks();

        var metadata = metadata(dep);
        SingleFileOutput bin = null;
        SingleFileOutput src = null;

        var mcp = getMcp();
        var mcp_side = mcp.getSide("joined");

        if (Util.isSourceDisabled()) {
            /*
             * If source are disabled we apply binary patches
             * base = cfg.notchObf ? mcp.joined.merge.output : mcp.joined.rename.output
             * If we're not official then Mojang strips out some needed metadata, so we need to clean that
             * Inject Universal Jars
             * Build a jar containing the 'injected' sources from MCP Config.
             * Compile those sources
             * Merge them with the binpatched jar
             * if notchObf rename to srg
             * Apply access transformers
             * Apply Side Strippers
             * rename to user selected
             */
            var base = mcp_side.steps().get(config.notchObf() ? "merge" : "rename");
            var patched = binpatch(mcp, base);
            if (!mcp.getConfig().official)
                patched = fixBinaryCompileIssues(mcp, patched);
            patched = mergeUniversals("binpatchedMerged", patched);

            var inject = mcpSources(mcp, mcp_side.mappings().obf2srg(), mcp.getConfig().getData("joined").get("inject"), base);
            var compile = compile(mcp, inject, patched);
            var toInject = packageCompiled("mcpInjectClasses", compile);

            var srg = merge("inject", patched, toInject);
            if (config.notchObf())
                srg = renameJar("injectedObf2Srg", srg, mcp_side.mappings().obf2srg());

            var ats = new ArrayList<SingleFileOutput>();
            var sas = new ArrayList<SingleFileOutput>();
            for (var parent : getStack()) {
                if (parent.ats != null)
                    ats.addAll(parent.ats);
                if (parent.sas != null)
                    sas.addAll(parent.sas);
            }

            if (!ats.isEmpty())
                srg = modifyAccess("applyAccessTransformationsBinary", srg, ats);
            if (!sas.isEmpty())
                srg = stripSideAnnotations("applySideStripperBinary", srg, sas);

            bin = renameJar("srg2Named", srg, mcp_side.mappings().srg2names());
        } else {
            /*
             * If we're able to decompile.
             * Use Patched Sources
             * Rename to selected mappings.
             * Compile Sources
             * Inject Universal data & Userdev injection
             */
            src = renameSources(this.last, mcp_side.mappings().names());
            var compiled = compile(mcp, src, null);
            bin = packageCompiled("compiled", compiled);
            bin = mergeUniversals("compiledMerged", bin);
        }

        return List.of(new RepositoryArtifact(dep, metadata, bin, src));
    }

    private SingleFileOutput metadata(Dependency dep) {
        var mcp = getMcp();
        var task = task("metadata", CreateIvyMetadata.class);
        task.getOrganization().convention(dep.getGroup());
        task.getArtifact().convention(dep.getName());
        task.getVersion().convention(dep.getVersion());
        task.getLauncherMeta().convention(pipeline.fg.getMcTasks().getVersionJson(mcp.getConfig().version).getOutput());
        for (var parent : this.getStack())
            parent.config.libraries().forEach(task.getAdditionalDeps()::add);
        task.getOutput().convention(local.file("ivy.xml"));
        return task;
    }

    private SingleFileOutput binpatch(MCP mcp, SingleFileOutput input) {
        var data = config.binpatcher;

        var dlTool = task("applyBinpatchesDownload", DownloadMavenArtifactManual.class);
        dlTool.getArtifact().convention(data.version);
        dlTool.getMaven().convention(data.repo);
        dlTool.getOutput().convention(local.file("binpatches-tool.jar"));

        var extract = extract(config.binpatches);

        var task = task("applyBinpatches", JavaToolExec.WithOutput.class);
        task.dependsOn(dlTool, extract, input);
        task.getJvmArgs().addAll(data.jvmargs);
        task.getTool().convention(dlTool.getOutput());
        task.getOutput().convention(local.file("binpatched.jar"));
        task.getLog().convention(local.file("binpatched.jar.log"));
        task.getWorkDir().convention(local.dir().get().toString());
        int java_version = data.java_version != null ? data.java_version : mcp.getConfig().java_target;
        task.getJavaVersion().convention(JavaLanguageVersion.of(java_version));

        var args = new ArrayList<String>();
        for (var arg : data.args) {
            if ("{clean}".equals(arg))
                args.add(input.getOutputFile().getAbsolutePath());
            else if ("{output}".equals(arg))
                args.add(task.getOutputFile().getAbsolutePath());
            else if ("{patch}".equals(arg))
                args.add(extract.getOutputFile().getAbsolutePath());
            else
                args.add(arg);
        }
        task.getArgs().set(args);
        return task;
    }

    private SingleFileOutput fixBinaryCompileIssues(MCP mcp, SingleFileOutput input) {
        // TODO: [FG][Patcher] Fix binpatched jar without 'mcinject' MCP task
        var side = mcp.getSide("joined");
        var taskFactory = getTaskFactory(side);
        var step = Map.of(
            "type", "mcinject",
            "name", "fixBinpatched",
            "input", input.getOutputFile().getAbsolutePath()
        );

        var task = taskFactory.createTask(side.extracts(), side.steps(), step);
        task.dependsOn(input);
        task.getInputs().file(input.getOutput());
        task.getOutput().convention(local.file("binpatchedFixed.jar"));
        return task;
    }

    private SingleFileOutput mergeUniversals(String filename, SingleFileOutput patched) {
        var task = task("mergeUniversals", OrderedMergeJars.class);
        task.getOutput().convention(local.file(filename + ".jar"));
        task.getArchives().add(patched.getOutput());
        for (var patcher : this.getStack()) {
            var injection = patcher.userdevInjection();
            if (injection != null)
                task.getArchives().add(injection.getOutput());
            task.getArchives().add(patcher.filterUniversal().getOutput());
        }
        return task;
    }

    private SingleFileOutput mcpSources(MCP mcp, SingleFileOutput mappings, String prefix, SingleFileOutput base) {
        var packages = task("mcpPackageList", ListBinaryPackages.class);
        packages.getInput().convention(base.getOutput());
        packages.getMappings().convention(mappings.getOutput());
        packages.getOutput().convention(local.file("mcpPackages.txt"));

        var task = task("mcpInjectSources", Inject.class);
        task.getPackages().convention(packages.getOutput());
        task.getMcp().set(mcp.getData());
        task.getPrefix().set(prefix);
        task.getSide().set("joined");
        task.getOutput().convention(local.file("mcpInjectSources.jar"));
        return task;
    }

    // TODO: [FG]  Make this generic as it's a copy of MCPSide.compile with an added library
    private JavaCompile compile(MCP mcp, SingleFileOutput sources, @Nullable SingleFileOutput library) {
        var task = task("compile", JavaCompile.class);
        var config = task("compileConfig", DefaultTask.class);
        var project = task.getProject();

        var jsonTask = this.pipeline.fg.getMcTasks().getVersionJson(mcp.getConfig().version);
        config.dependsOn(jsonTask);
        if (library != null)
            config.dependsOn(library);
        config.doFirst(t -> {
            var deps = new ArrayList<Dependency>();
            if (library != null)
                deps.add(project.getDependencies().create(library.getOutputFile()));

            for (var lib : mcp.getConfig().getLibraries("joined"))
                deps.add(project.getDependencies().create(lib));

            var json = JsonData.minecraftVersion(jsonTask.getOutputFile());
            for (var lib : json.libraries)
                deps.add(project.getDependencies().create(lib.name));

            for (var parent : getStack()) {
                for (var lib : parent.config.libraries())
                    deps.add(project.getDependencies().create(lib));
            }

            var cfg = project.getConfigurations().detachedConfiguration(deps.toArray(Dependency[]::new));
            task.setClasspath(cfg);
        });

        var toolchainService = project.getExtensions().getByType(JavaToolchainService.class);
        var javaCompiler = toolchainService.compilerFor(cfg -> cfg.getLanguageVersion().set(JavaLanguageVersion.of(mcp.getConfig().java_target)));
        task.getOptions().setWarnings(false);
        task.dependsOn(sources, config);
        task.getJavaCompiler().set(javaCompiler);
        task.setSource(project.zipTree(sources.getOutputFile()));
        task.getDestinationDirectory().set(local.dir("compile"));
        this.pipeline.fg.getRepo().blacklist(task);
        return task;
    }

    private SingleFileOutput packageCompiled(String name, JavaCompile compile) {
        var task = task(name + "Jar", JarWithOutput.class);
        task.from(compile.getDestinationDirectory());
        task.getDestinationDirectory().convention(local.dir());
        task.getArchiveFileName().set(name + ".jar");
        return task;
    }

    private SingleFileOutput merge(String name, SingleFileOutput first, SingleFileOutput second) {
        var task = task(name, OrderedMergeJars.class);
        task.getArchives().add(first.getOutput());
        task.getArchives().add(second.getOutput());
        task.getOutput().convention(local.file(name + ".jar"));
        return task;
    }

    private SingleFileOutput renameJar(String name, SingleFileOutput jar, SingleFileOutput mappings) {
        var task = task("rename" + Util.capitalize(name), RenameJarFile.class);
        task.getInput().convention(jar.getOutput());
        task.getMapping().convention(mappings.getOutput());
        task.getOutput().convention(local.file(name + ".jar"));
        return task;
    }

    private SingleFileOutput renameSources(SingleFileOutput sources, SingleFileOutput names) {
        var task = task("renameSource", RenameSources.class);
        task.getInput().convention(sources.getOutput());
        task.getNames().convention(names.getOutput());
        task.getJavadocs().convention(true);
        task.getEncoding().convention(getMcp().getConfig().encoding);
        task.getOutput().convention(local.file("sourcesRenamed.jar"));
        return task;
    }
}
