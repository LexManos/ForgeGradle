package net.minecraftforge.gradle.pipelines.mcp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaToolchainService;

import net.minecraftforge.gradle.data.JsonData;
import net.minecraftforge.gradle.mappings.MappingsExtension;
import net.minecraftforge.gradle.mappings.tasks.ChainMappingFiles;
import net.minecraftforge.gradle.mappings.tasks.ConvertMappingFile;
import net.minecraftforge.gradle.mappings.tasks.RenameMappingFile;
import net.minecraftforge.gradle.mappings.tasks.RenameSources;
import net.minecraftforge.gradle.pipelines.mcp.MCP.ExtractTask;
import net.minecraftforge.gradle.pipelines.mcp.tasks.MergeExtra;
import net.minecraftforge.gradle.repo.RepositoryArtifact;
import net.minecraftforge.gradle.tasks.CreateIvyMetadata;
import net.minecraftforge.gradle.tasks.JarWithOutput;
import net.minecraftforge.gradle.tasks.RenameJarFile;
import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.tasks.archive.ExtractZipDir;
import net.minecraftforge.gradle.tasks.archive.ExtractZipFile;
import net.minecraftforge.gradle.util.FileHelper;
import net.minecraftforge.gradle.util.Util;
import net.minecraftforge.srgutils.IMappingFile;

public class MCPSide {
    private final Project project;
    final MCP mcp;
    final String side;
    private final FileHelper local;
    private boolean setup = false;

    private SingleFileOutput predecomp;
    private SingleFileOutput last;
    private Map<String, SingleFileOutput> tasks;
    private Map<String, ExtractTask> extracts;
    private MappingTasks mappingHelper;

    MCPSide(Project project, MCP owner, FileHelper global, String side) {
        this.project = project;
        this.mcp = owner;
        this.side = side;
        this.local = new FileHelper(global.dir(), side);
    }

    private <T extends Task> T task(String name, Class<T> type) {
        name = "_mcp_" + mcp.prefix + '_' + side + '_' + name;
        var ret = project.getTasks().create(name, type);
        ret.setGroup("Forge Gradle Internal - MCP Pipeline - " + mcp.name);
        return ret;
    }

    void setupTasks() {
        if (setup)
            return;
        setup = true;

        var steps = mcp.config.steps.get(side);
        if (steps == null)
            throw new IllegalArgumentException("Invalid MCP Dependency: " + mcp.name + " - Does not contain requested side `" + side + "`");

        var data = mcp.config.getData(side);

        // Create tasks to extract any data files that may need extracting
        extracts = new HashMap<>();
        for (var key : data.keySet()) {
            var value = data.get(key);
            var name = "extract_" + Util.sanitizeTaskName(key);
            if (value.endsWith("/")) {
                var target = local.dir(name);
                var task = task(name, ExtractZipDir.class);
                task.getZip().set(mcp.data);
                task.getPrefix().set(value);
                task.getOutput().convention(target);
                extracts.put(key, new ExtractTask(task, target.get().getAsFile().getAbsolutePath()));
            } else {
                var idx = value.lastIndexOf('/');
                var filename = idx == -1 ? value : value.substring(idx);
                var target = local.file(name, filename);
                var task = task(name, ExtractZipFile.class);
                task.getZip().set(mcp.data);
                task.getInternalPath().set(value);
                task.getOutput().convention(target);
                extracts.put(key, new ExtractTask(task, target.get().getAsFile().getAbsolutePath()));
            }
        }

        var taskFactory = new MCPTaskFactory(this.mcp.fg, local, this) {
            @Override
            protected <T extends Task> T task(String name, Class<T> type) {
                return MCPSide.this.task(name, type);
            }
        };

        SingleFileOutput mappings = null;
        tasks = new LinkedHashMap<>();
        for (int x = 0; x < steps.size(); x++) {
            var step = steps.get(x);
            var type = step.get("type");
            var name = step.getOrDefault("name", type);

            if ("decompile".equals(name))
                predecomp = taskFactory.getTask(tasks, step.get("input"));
            else if ("rename".equals(name)) {
                var value = step.getOrDefault("mappings", "{mappings}");
                if (!value.startsWith("{") || !value.endsWith("}"))
                    throw new IllegalArgumentException("Invalid MCP Dependency: " + mcp.name + " - Expected `rename` step's `mappings` argument to be a variable");

                if (value.endsWith("Output}"))
                    mappings = taskFactory.getTask(tasks, value);
                else {
                    var extract = extracts.get(value);
                    if (extract == null)
                        throw new IllegalArgumentException("Invalid MCP Dependency: " + mcp.name + " - Could not find mapping extract task");
                    else if (extract.task() instanceof SingleFileOutput task)
                        mappings = task;
                    else
                        throw new IllegalArgumentException("Invalid MCP Dependency: " + mcp.name + " - Mapping data is expected to be a file, not directory");
                }
            }

            var task = taskFactory.createTask(extracts, tasks, step);
            tasks.put(name, task);
            last = task;
        }

        if (predecomp == null)
            throw new IllegalArgumentException("Invalid MCP Dependency: " + mcp.name + " - Could not find `decompile` step");

        if (mappings == null)
            throw new IllegalArgumentException("Invalid MCP Dependency: " + mcp.name + " - Could not find `mappings` task");

        this.mappingHelper = new MappingTasks(mappings);
    }

    public SingleFileOutput predecomp() {
        setupTasks();
        return this.predecomp;
    }

    public SingleFileOutput last() {
        setupTasks();
        return this.last;
    }

    public MappingTaskHelper mappings() {
        setupTasks();
        return this.mappingHelper;
    }

    public Map<String, SingleFileOutput> steps() {
        setupTasks();
        return this.tasks;
    }

    public Map<String, ExtractTask> extracts() {
        setupTasks();
        return this.extracts;
    }

    List<RepositoryArtifact> buildArtifacts(Dependency dep, boolean sources) {
        var metadata = metadata(dep);
        SingleFileOutput bin = null;
        SingleFileOutput src = null;
        if (Util.isSourceDisabled()) {
            /**
             * Sources are disabled so take the pre-decomp jar
             * Make intermediate -> named mapping file
             * Run through Jar Renamer
             *
             * TODO: [FG][MCP] Compile injected code in a seperate smaller jar then merge
             */
            var namedJar = renameJar(predecomp(), mappings().srg2names());
            bin = mergeExtra("mergeExtraSourceDisabled", namedJar, mappings().obf2srg());
        } else {
            /*
             * Sources are enabled, to take the output of the last step
             * Load Intermediate -> Human names, and javadocs
             * Inject into last output
             * Compile new jar
             * Merge any assets/data from the original minecraft jar.
             */
            src = renameSources(last(), mappings().names());
            var compile = compile(src);
            var packJar = packageCompiled(compile);
            bin = mergeExtra("mergeExtra", packJar, mappings().obf2srg());
        }

        return List.of(new RepositoryArtifact(dep, metadata, bin, src));
    }

    private SingleFileOutput metadata(Dependency dep) {
        var task = task("metadata", CreateIvyMetadata.class);
        task.getOrganization().convention(dep.getGroup());
        task.getArtifact().convention(dep.getName());
        task.getVersion().convention(dep.getVersion());
        task.getLauncherMeta().convention(mcp.fg.getMcTasks().getVersionJson(mcp.config.version).getOutput());
        mcp.config.getLibraries(side).forEach(task.getAdditionalDeps()::add);
        task.getOutput().convention(local.file("ivy.xml"));
        return task;
    }

    private SingleFileOutput renameJar(SingleFileOutput jar, SingleFileOutput mappings) {
        var task = task("srg2named", RenameJarFile.class);
        task.getInput().convention(jar.getOutput());
        task.getMapping().convention(mappings.getOutput());
        task.getOutput().convention(local.file("named.jar"));
        return task;
    }

    private SingleFileOutput mergeExtra(String name, SingleFileOutput binary, SingleFileOutput mappings) {
        var base = "joined".equals(side) ? "client" : side;
        var baseTask = mcp.fg.getMcTasks().getVersionFile(mcp.config.version, base, "jar");

        var task = task(name, MergeExtra.class);
        task.getInput().convention(binary.getOutput());
        task.getBase().convention(baseTask.getOutput());
        task.getMappings().convention(mappings.getOutput());
        task.getOutput().convention(local.file(name + ".jar"));
        return task;
    }

    private SingleFileOutput renameSources(SingleFileOutput sources, SingleFileOutput names) {
        var task = task("renameSource", RenameSources.class);
        task.getInput().convention(sources.getOutput());
        task.getNames().convention(names.getOutput());
        task.getJavadocs().convention(true);
        task.getEncoding().convention(mcp.config.encoding);
        task.getOutput().convention(local.file("renamed-sources.jar"));
        return task;
    }

    private JavaCompile compile(SingleFileOutput sources) {
        var task = task("recompile", JavaCompile.class);
        var config = task("recompileConfig", DefaultTask.class);

        var jsonTask = mcp.fg.getMcTasks().getVersionJson(mcp.config.version);
        config.dependsOn(jsonTask);
        config.doFirst(t -> {
            var deps = new ArrayList<Dependency>();
            for (var lib : mcp.config.getLibraries(side))
                deps.add(project.getDependencies().create(lib));
            var json = JsonData.minecraftVersion(jsonTask.getOutputFile());
            for (var lib : json.libraries)
                deps.add(project.getDependencies().create(lib.name));
            var cfg = project.getConfigurations().detachedConfiguration(deps.toArray(Dependency[]::new));
            task.setClasspath(cfg);
        });

        var toolchainService = project.getExtensions().getByType(JavaToolchainService.class);
        var javaCompiler = toolchainService.compilerFor(cfg -> cfg.getLanguageVersion().set(JavaLanguageVersion.of(mcp.config.java_target)));
        task.getOptions().setWarnings(false);
        task.dependsOn(sources, config);
        task.getJavaCompiler().set(javaCompiler);
        task.setSource(project.zipTree(sources.getOutputFile()));
        task.getDestinationDirectory().set(local.dir("recompile-classes"));
        mcp.fg.getRepo().blacklist(task);
        return task;
    }

    private SingleFileOutput packageCompiled(JavaCompile compile) {
        var task = task("recompileJar", JarWithOutput.class);
        task.from(compile.getDestinationDirectory());
        task.getDestinationDirectory().convention(local.dir());
        task.getArchiveFileName().set("recompile.jar");
        return task;
    }

    private class MappingTasks implements MappingTaskHelper {
        private final MappingsExtension ext;
        private SingleFileOutput obf2srg;
        private SingleFileOutput srg2obf;
        private SingleFileOutput srg2srg;
        private Map<Key, SingleFileOutput> srg2names = new HashMap<>();
        private Map<Key, SingleFileOutput> names2srg = new HashMap<>();
        private Map<Key, SingleFileOutput> obf2names = new HashMap<>();
        private Map<Key, SingleFileOutput> names2obf = new HashMap<>();

        private record Key(String channel, String version) {
            private String clean() {
                return Util.sanitizeTaskName(Util.capitalize(channel) + version);
            }
        }

        private MappingTasks(SingleFileOutput base) {
            this.obf2srg = base;
            this.ext = mcp.fg.getMappings();
        }

        @Override
        public SingleFileOutput obf2srg() {
            return this.obf2srg;
        }

        private SingleFileOutput reverse(SingleFileOutput input, String name, String filename) {
            var task = task(name, ConvertMappingFile.class);
            task.getInput().set(input.getOutput());
            task.getFormat().set(IMappingFile.Format.TSRG);
            task.getReverse().set(true);
            task.getOutput().convention(local.file(filename));
            return task;
        }

        @Override
        public SingleFileOutput srg2obf() {
            if (this.srg2obf == null)
                this.srg2obf = reverse(obf2srg(), "makeSrg2Obf", "srg2obf.tsrg");
            return this.srg2obf;
        }

        private SingleFileOutput srg2srg() {
            if (this.srg2srg == null) {
                var task = task("makeSrg2Srg", ChainMappingFiles.class);
                task.getLeft().convention(obf2srg().getOutput());
                task.getReverseLeft().convention(true);
                task.getRight().convention(obf2srg().getOutput());
                task.getOutput().convention(local.file("srg2srg.tsrg"));
                this.srg2srg = task;
            }
            return this.srg2srg;
        }

        @Override
        public SingleFileOutput names() {
            return names(ext.getCannel(), ext.getVersion(mcp.config.version));
        }

        @Override
        public SingleFileOutput names(String channel, String version) {
            var ext = mcp.fg.getMappings();
            var provider = ext.getProvider(channel);
            if (provider == null)
                throw new IllegalStateException("Could not find mapping channel `" + channel + "`");
            return provider.getMappings(channel, version, obf2srg());
        }

        @Override
        public SingleFileOutput srg2names() {
            return srg2names(ext.getCannel(), ext.getVersion(mcp.config.version));
        }

        @Override
        public SingleFileOutput srg2names(String channel, String version) {
            return this.srg2names.computeIfAbsent(new Key(channel, version), key -> {
                var task = task("makeSrg2Names" + key.clean(), RenameMappingFile.class);
                task.getMappings().convention(srg2srg().getOutput());
                task.getNames().convention(names(channel, version).getOutput());
                task.getOutput().convention(local.file("srg2named" + key.clean() + ".tsrg"));
                return task;
            });
        }

        @Override
        public SingleFileOutput names2srg() {
            return names2srg(ext.getCannel(), ext.getVersion(mcp.config.version));
        }

        @Override
        public SingleFileOutput names2srg(String channel, String version) {
            return this.names2srg.computeIfAbsent(new Key(channel, version), key ->
                reverse(srg2names(channel, version), "makeNames2Srg" + key.clean(), "named2srg" + key.clean() + ".tsrg")
            );
        }

        @Override
        public SingleFileOutput obf2names() {
            return obf2names(ext.getCannel(), ext.getVersion(mcp.config.version));
        }

        @Override
        public SingleFileOutput obf2names(String channel, String version) {
            return this.obf2names.computeIfAbsent(new Key(channel, version), key -> {
                var task = task("makeObf2Names" + key.clean(), RenameMappingFile.class);
                task.getMappings().convention(obf2srg().getOutput());
                task.getNames().convention(names(channel, version).getOutput());
                task.getOutput().convention(local.file("obf2named" + key.clean() + ".tsrg"));
                return task;
            });
        }

        @Override
        public SingleFileOutput names2obf() {
            return names2obf(ext.getCannel(), ext.getVersion(mcp.config.version));
        }

        @Override
        public SingleFileOutput names2obf(String channel, String version) {
            return this.names2obf.computeIfAbsent(new Key(channel, version), key ->
                reverse(obf2names(channel, version), "makeNames2Obf" + key.clean(), "named2obf" + key.clean() + ".tsrg")
            );
        }
    }
}

