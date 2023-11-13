package net.minecraftforge.gradle.pipelines.vanilla;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.file.RegularFileProperty;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.ForgeGradleExtension;
import net.minecraftforge.gradle.mappings.tasks.ConvertMappingFile;
import net.minecraftforge.gradle.pipelines.Pipeline;
import net.minecraftforge.gradle.pipelines.vanilla.tasks.ListLibraries;
import net.minecraftforge.gradle.pipelines.vanilla.tasks.MergeJarFile;
import net.minecraftforge.gradle.pipelines.vanilla.tasks.StripServerJar;
import net.minecraftforge.gradle.repo.RepositoryArtifact;
import net.minecraftforge.gradle.repo.RepositoryManager;
import net.minecraftforge.gradle.tasks.CreateIvyMetadata;
import net.minecraftforge.gradle.tasks.DecompileJar;
import net.minecraftforge.gradle.tasks.DownloadMavenArtifact;
import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.tasks.RenameJarFile;
import net.minecraftforge.gradle.util.FileHelper;
import net.minecraftforge.gradle.util.Tools;
import net.minecraftforge.gradle.util.Util;

@ApiStatus.Internal
public class VanillaPipeline implements Pipeline {
    private final Set<String> VALID_NAMES = Set.of("client", "server", "joined");
    private final Map<String, VersionTasks> verTasks = new HashMap<>();
    private final Project project;
    private final ForgeGradleExtension fg;
    private final FileHelper local;
    private DownloadMavenArtifact dlRenamer;
    private DownloadMavenArtifact dlMerger;
    private DownloadMavenArtifact dlDecompiler;

    public VanillaPipeline(Project project, ForgeGradleExtension fg) {
        this.project = project;
        this.fg = fg;
        this.local = new FileHelper(project, "_vanilla");
    }

    /*
     * Vanilla Steps:
     *   Download manfiest
     *   Download Version Info
     *   Add Libraries to project's config so it gets published in pom
     *   Download Client
     *   Download Client Mappings
     *   Reverse Client Mappings
     *   Rename Client Jar
     *
     *   Download Server
     *   Download Server Mappings
     *   Reverse Server Mappings
     *   Strip Server Jar
     *   Rename Server Jar
     *
     *   Merge
     *   Decompile
     *   Fix Binary line numbers
     *
     *   Generate Ivy.xml fils listing dependencies
     */
    @Override
    public Dependency handleDependency(RepositoryManager repo, Dependency dep) {
        // We are vanilla, so lets verify we are targeting the vanilla artifact
        if (!"net.minecraft".equals(dep.getGroup()) || !VALID_NAMES.contains(dep.getName()))
            throw new IllegalArgumentException("Invalid vanilla dependency, only `net.minecraft:[client|server|joined]` supported. `" + dep.getGroup() + ":" + dep.getName() + "` requested");

        var version = dep.getVersion();
        if (version.endsWith("+"))
            throw new IllegalArgumentException("Dynamic versions are not supported");

        setupCommonTasks();

        var verTasks = this.verTasks.get(dep.getVersion());
        if (verTasks == null) {
            verTasks = setupVersionTasks(dep);
            this.verTasks.put(version, verTasks);
        }

        repo.addArtifact(getRet(dep, verTasks));

        return dep;
    }

    private VersionTasks setupVersionTasks(Dependency dep) {
        var version = dep.getVersion();
        var mc = fg.getMcTasks();
        var dlVersionJson = mc.getVersionJson(dep.getVersion());
        var dlClientJar = mc.getVersionFile(version, "client", "jar");
        var dlServerJar = mc.getVersionFile(version, "server", "jar");
        var dlClientMap = mc.getVersionFile(version, "client_mappings", "txt");
        var dlServerMap = mc.getVersionFile(version, "server_mappings", "txt");
        var revClientMap = reverse(version, "ClientMappings", dlClientMap);
        var revServerMap = reverse(version, "ServerMappings", dlServerMap);
        var stripServer = strip(version, dlServerJar, dlServerMap);
        var renameClient = rename(version, "Client", dlClientJar, revClientMap, dlRenamer);
        var renameServer = rename(version, "Server", stripServer, revServerMap, dlRenamer);
        var merge = merge(version, renameClient, renameServer, dlMerger);
        var clientMetadata = meta(dep, "client", dlVersionJson, null);
        var serverMetadata = meta(dep, "server", dlVersionJson, dlServerJar);
        var joinedMetadata = meta(dep, "joined", dlVersionJson, null);

        if (!Util.isSourceDisabled()) {
            var clientLibraries = listLibraries(version, "Client", dlVersionJson, null);
            var serverLibraries = listLibraries(version, "Server", dlVersionJson, dlServerJar);

            var decompClient = decomp(version, "client", dlDecompiler, renameClient, clientLibraries);
            var decompServer = decomp(version, "server", dlDecompiler, renameServer, serverLibraries);
            var decompJoined = decomp(version, "joined", dlDecompiler, merge, clientLibraries);

            var linedClient = fixLines(version, "client", renameClient.getOutput(), decompClient.getOutput(), dlRenamer.getOutput());
            var linedServer = fixLines(version, "server", renameServer.getOutput(), decompServer.getOutput(), dlRenamer.getOutput());
            var linedJoined = fixLines(version, "joined", merge.getOutput(), decompJoined.getOutput(), dlRenamer.getOutput());

            return new VersionTasks(
                clientMetadata, linedClient, decompClient,
                serverMetadata, linedServer, decompServer,
                joinedMetadata, linedJoined, decompJoined
            );
        } else {
            return new VersionTasks(
                clientMetadata, renameClient, null,
                serverMetadata, renameServer, null,
                joinedMetadata, merge,        null
            );
        }
    }

    private RepositoryArtifact getRet(Dependency dep, VersionTasks tasks) {
        switch (dep.getName()) {
            case "client": return new RepositoryArtifact(dep, tasks.clientMetadata, tasks.clientBinary, tasks.clientSource);
            case "server": return new RepositoryArtifact(dep, tasks.serverMetadata, tasks.serverBinary, tasks.serverSource);
            case "joined": return new RepositoryArtifact(dep, tasks.joinedMetadata, tasks.joinedBinary, tasks.joinedSource);
            default: throw new IllegalArgumentException("How the hell did you get here?");
        }
    }

    private void setupCommonTasks() {
        if (dlRenamer == null) {
            dlRenamer = task(name("downloadJarRenamer"), DownloadMavenArtifact.class);
            dlRenamer.getArtifact().convention(Tools.FART);
            dlRenamer.getOutput().convention(local.file("renamer.jar"));
        }

        if (dlMerger == null) {
            dlMerger = task(name("downloadJarMerger"), DownloadMavenArtifact.class);
            dlMerger.getArtifact().convention(Tools.MERGE);
            dlMerger.getOutput().convention(local.file("merger.jar"));
        }

        if (dlDecompiler == null) {
            dlDecompiler = task(name("downloadDecompiler"), DownloadMavenArtifact.class);
            dlDecompiler.getArtifact().convention(Tools.FERNFLOWER);
            dlDecompiler.getOutput().convention(local.file("decompiler.jar"));
        }
    }

    private ConvertMappingFile reverse(String version, String name, SingleFileOutput map) {
        var ret = task(name(version, "reverse" + name), ConvertMappingFile.class);
        ret.getInput().set(map.getOutput());
        ret.getReverse().set(true);
        ret.getOutput().convention(local.file(version, "official2obf" + name + ".tsrg"));
        return ret;
    }

    private SingleFileOutput strip(String version, SingleFileOutput dlServerJar, SingleFileOutput dlServerMap) {
        var task = task(name(version, "stripServer"), StripServerJar.class);
        task.getInput().convention(dlServerJar.getOutput());
        task.getMapping().convention(dlServerMap.getOutput());
        task.getOutput().convention(local.file(version, "strip_server.jar"));
        return task;
    }

    private RenameJarFile rename(String version, String name, SingleFileOutput jar, SingleFileOutput map, SingleFileOutput tool) {
        name = "rename" + name;
        var ret = task(name(version, name), RenameJarFile.class);
        ret.args("--input", "{input}", "--output", "{output}", "--names", "{map}", "--strip-sigs", "--src-fix", "--ids-fix");
        ret.getInput().convention(jar.getOutput());
        ret.getTool().convention(tool.getOutput());
        ret.getMapping().convention(map.getOutput());
        ret.getOutput().convention(local.file(version, name + ".jar"));
        ret.getLog().convention(local.file(version, name + ".jar.log"));
        ret.getWorkDir().convention(local.dir(version).get().toString());
        return ret;
    }

    private RenameJarFile fixLines(String version, String name, RegularFileProperty jar, RegularFileProperty map, RegularFileProperty tool) {
        name = "fixLines" + Util.capitalize(name);
        var ret = task(name(version, name), RenameJarFile.class);
        ret.args("--input", "{input}", "--output", "{output}", "--ff-line-numbers", "{map}");
        ret.getInput().convention(jar);
        ret.getTool().convention(tool);
        ret.getMapping().convention(map);
        ret.getOutput().convention(local.file(version, name + ".jar"));
        ret.getLog().convention(local.file(version, name + ".jar.log"));
        ret.getWorkDir().convention(local.dir(version).get().toString());
        return ret;
    }

    private MergeJarFile merge(String version, SingleFileOutput client, SingleFileOutput server, SingleFileOutput tool) {
        var name = "mergeJars";
        var ret = task(name(version, name), MergeJarFile.class);
        ret.args("--merge", "--client", "{client}", "--server", "{server}", "--output", "{output}", "--inject", "false", "--keep-data");
        ret.getClient().convention(client.getOutput());
        ret.getServer().convention(server.getOutput());
        ret.getTool().convention(tool.getOutput());
        ret.getOutput().convention(local.file(version, name + ".jar"));
        ret.getLog().convention(local.file(version, name + ".jar.log"));
        ret.getWorkDir().convention(local.dir(version).get().toString());
        return ret;
    }

    private DecompileJar decomp(String version, String name, SingleFileOutput tool, SingleFileOutput input, SingleFileOutput libs) {
        name = "decompile" + Util.capitalize(name);
        var ret = task(name(version, name), DecompileJar.class);
        ret.getTool().convention(tool.getOutput());
        ret.args("-din=1", "-rbr=1", "-dgs=1", "-asc=1", "-rsy=1", "-iec=1", "-jvn=1", "-isl=0", "-iib=1", "-bsm=1", "-dcl=1", "-sef=1",
                "-log=TRACE", "-cfg", "{libraries}", "{input}", "{output}");
        ret.jvmArgs("-Xmx4G");
        ret.getInput().convention(input.getOutput());
        ret.javaVersion("17"); // TODO: Pull this from the launcher json, and select a version of FF that supports that output. Or teach FF to properly disable modern features when trying to target a older version.
        ret.getLibraries().convention(libs.getOutput());
        ret.getOutput().convention(local.file(version, name + ".jar"));
        ret.getLog().convention(local.file(version, name + ".jar.log"));
        ret.getWorkDir().convention(local.dir(version).get().toString());
        return ret;
    }

    private CreateIvyMetadata meta(Dependency dep, String side, SingleFileOutput json, SingleFileOutput serverJar) {
        var name = "metadata" + Util.capitalize(side);
        var ret = task(name(dep.getVersion(), name), CreateIvyMetadata.class);
        ret.getOrganization().convention(dep.getGroup());
        ret.getArtifact().convention(side.toLowerCase(Locale.ROOT));
        ret.getVersion().convention(dep.getVersion());
        ret.getLauncherMeta().convention(json.getOutput());
        if (serverJar != null)
            ret.getBundledServer().convention(serverJar.getOutput());
        ret.getOutput().convention(local.file(dep.getVersion(), "ivy_" + side + ".xml"));
        return ret;
    }

    private SingleFileOutput listLibraries(String version, String name, SingleFileOutput json, SingleFileOutput jar) {
        var task = task(name(version, "listLibraries" + name), ListLibraries.class);
        task.getLauncherMeta().convention(json.getOutput());
        if (jar != null)
            task.getBundledServer().convention(jar.getOutput());
        task.getOutput().convention(local.file(version, "listLibraries" + name + ".txt"));
        return task;
    }

    private String name(String name) {
        return "_vanilla_" + name;
    }

    private String name(String version, String name) {
        return "_vanilla_" + version + '_' + name;
    }

    private <T extends Task> T task(String name, Class<T> type) {
        var ret = project.getTasks().create(name, type);
        ret.setGroup("Forge Gradle Internal - Vanilla Pipeline");
        return ret;
    }

    private record VersionTasks(
        SingleFileOutput clientMetadata, SingleFileOutput clientBinary, SingleFileOutput clientSource,
        SingleFileOutput serverMetadata, SingleFileOutput serverBinary, SingleFileOutput serverSource,
        SingleFileOutput joinedMetadata, SingleFileOutput joinedBinary, SingleFileOutput joinedSource
    ) {}
}
