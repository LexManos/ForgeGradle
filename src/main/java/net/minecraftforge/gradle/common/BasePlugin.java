package net.minecraftforge.gradle.common;

import groovy.lang.Closure;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;

import net.minecraftforge.gradle.FileLogListenner;
import net.minecraftforge.gradle.delayed.DelayedAlternatorFile;
import net.minecraftforge.gradle.delayed.DelayedBase.IDelayedResolver;
import net.minecraftforge.gradle.delayed.DelayedFile;
import net.minecraftforge.gradle.delayed.DelayedFileTree;
import net.minecraftforge.gradle.delayed.DelayedString;
import net.minecraftforge.gradle.json.JsonFactory;
import net.minecraftforge.gradle.json.LauncherManifest;
import net.minecraftforge.gradle.json.version.AssetIndex;
import net.minecraftforge.gradle.json.version.Version;
import net.minecraftforge.gradle.tasks.DownloadAssetsTask;
import net.minecraftforge.gradle.tasks.ObtainFernFlowerTask;
import net.minecraftforge.gradle.tasks.abstractutil.DownloadTask;
import net.minecraftforge.gradle.tasks.abstractutil.EtagDownloadTask;

import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.repositories.FlatDirectoryArtifactRepository;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.tasks.Delete;
import org.gradle.testfixtures.ProjectBuilder;

import com.google.common.base.Throwables;

public abstract class BasePlugin<K extends BaseExtension> implements Plugin<Project>, IDelayedResolver<K>
{
    public Project    project;
    public Version    version;
    public AssetIndex assetIndex;

    @Override
    public final void apply(Project arg)
    {
        project = arg;

        // logging
        FileLogListenner listener = new FileLogListenner(project.file(Constants.LOG));
        project.getLogging().addStandardOutputListener(listener);
        project.getLogging().addStandardErrorListener(listener);
        project.getGradle().addBuildListener(listener);

        if (project.getBuildDir().getAbsolutePath().contains("!"))
        {
            project.getLogger().error("Build path has !, This will screw over a lot of java things as ! is used to denote archive paths, REMOVE IT if you want to continue");
            throw new RuntimeException("Build path contains !");
        }

        // extension objects
        project.getExtensions().create(Constants.EXT_NAME_MC, getExtensionClass(), project);
        project.getExtensions().create(Constants.EXT_NAME_JENKINS, JenkinsExtension.class, project);

        // repos
        project.allprojects(new Action<Project>() {
            public void execute(Project proj)
            {
                addMavenRepo(proj, "forge", Constants.FORGE_MAVEN);
                addMavenRepo(proj, "central", Constants.CENTRAL_MAVEN);
                addMavenRepo(proj, "minecraft", Constants.LIBRARY_URL);
            }
        });

        // after eval
        project.afterEvaluate(new Action<Project>() {
            @Override
            public void execute(Project project)
            {
                afterEvaluate();

                if (version != null){
                    File index = delayedFile(Constants.ASSETS + "/indexes/" + version.getAssets() + ".json").call();
                    if (index.exists())
                        parseAssetIndex();
                }
                finalCall();
            }
        });

        // some default tasks
        makeObtainTasks();

        // at last, apply the child plugins
        applyPlugin();
    }

    public abstract void applyPlugin();

    protected abstract String getDevJson();

    private static boolean displayBanner = true;

    public void afterEvaluate()
    {
        if (!displayBanner)
            return;
        project.getLogger().lifecycle("****************************");
        project.getLogger().lifecycle(" Powered By MCP:            ");
        project.getLogger().lifecycle(" http://mcp.ocean-labs.de/  ");
        project.getLogger().lifecycle(" Searge, ProfMobius, Fesh0r,");
        project.getLogger().lifecycle(" R4wk, ZeuX, IngisKahn      ");
        project.getLogger().lifecycle(delayedString(" MCP Data version : {MCP_VERSION}").call());
        project.getLogger().lifecycle("****************************");
        displayBanner = false;
    }

    public void finalCall()
    {
    }

    @SuppressWarnings("serial")
    private void makeObtainTasks()
    {
        {
            EtagDownloadTask task = makeTask("getVersionJsonIndex", EtagDownloadTask.class);
            task.setUri(delayedString(Constants.LAUNCHER_MANIFEST_URL));
            task.setFile(delayedFile(Constants.LAUNCHER_MANIFEST));
            task.setDieWithError(false);
        }
        {
            EtagDownloadTask task = makeTask("getVersionJson", EtagDownloadTask.class);
            class GetVersionJsonUrl extends DelayedString {
                public GetVersionJsonUrl() {
                    super(BasePlugin.this.project, "");
                }

                @Override
                public String call() {
                    try {
                        LauncherManifest manifest = JsonFactory.loadMCVersionManifest(delayedFile(Constants.LAUNCHER_MANIFEST).call());
                        String version = delayedString("{MC_VERSION}").call();
                        LauncherManifest.VersionInfo info = manifest.getInfo(version);
                        if (version == null)
                            throw new IllegalStateException("Could not find version \"" + version + "\" in launcher_manifest_v2.json");
                        return info.url;
                    } catch (IOException e) {
                        throw Throwables.propagate(e);
                    }
                }
            }
            task.dependsOn("getVersionJsonIndex");
            task.getInputs().file(delayedFile(Constants.LAUNCHER_MANIFEST));
            task.setUri(new GetVersionJsonUrl());
            task.setFile(delayedFile(Constants.VERSION_JSON));
            task.setDieWithError(false);
        }

        // download tasks
        {
            DownloadTask task = makeTask("downloadClient", DownloadTask.class);
            task.getInputs().file(delayedFile(Constants.VERSION_JSON));
            task.dependsOn("getVersionJson");

            task.setOutput(delayedFile(Constants.JAR_CLIENT_FRESH));
            task.setUrl(delayedVersionInfo(VersionInfoUrl.CLIENT));
        }

        {
            DownloadTask task = makeTask("downloadServer", DownloadTask.class);
            task.getInputs().file(delayedFile(Constants.VERSION_JSON));
            task.dependsOn("getVersionJson");

            task.setOutput(delayedFile(Constants.JAR_SERVER_FRESH));
            task.setUrl(delayedVersionInfo(VersionInfoUrl.SERVER));
        }

        {
            ObtainFernFlowerTask task = makeTask("downloadMcpTools", ObtainFernFlowerTask.class);
            task.setMcpUrl(delayedString(Constants.MCP_URL));
            task.setFfJar(delayedFile(Constants.FERNFLOWER));
        }

        {
            DownloadTask task = makeTask("getAssetsIndex", DownloadTask.class);
            task.getInputs().file(delayedFile(Constants.VERSION_JSON));
            task.dependsOn("getVersionJson");
            task.setUrl(delayedVersionInfo(VersionInfoUrl.ASSETS));
            task.setOutput(delayedFile(Constants.ASSETS + "/indexes/{ASSET_INDEX}.json"));
            task.setDoesCache(false);
            task.doLast(new Action<Task>() {
                public void execute(Task task) {
                    parseAssetIndex();
                }
            });

            task.getOutputs().upToDateWhen(new Closure<Boolean>(this, null) {
                public Boolean call(Object... obj) {
                    return false;
                }
            });
        }

        {
            DownloadAssetsTask task = makeTask("getAssets", DownloadAssetsTask.class);
            task.setAssetsDir(delayedFile(Constants.ASSETS));
            task.setIndex(getAssetIndexClosure());
            task.dependsOn("getAssetsIndex");
        }

        {
            Delete task = makeTask("cleanCache", Delete.class);
            task.delete(delayedFile("{CACHE_DIR}/minecraft"));
            task.setGroup("ForgeGradle");
            task.setDescription("Cleares the ForgeGradle cache. DONT RUN THIS unless you want a fresh start, or the dev tells you to.");
        }
    }

    public void parseAssetIndex() {
        File file = delayedFile(Constants.ASSETS + "/indexes/{ASSET_INDEX}.json").call();
        try {
            assetIndex = JsonFactory.loadAssetsIndex(file);
        } catch (Exception e) {
            throw new IllegalStateException("Could not parse " + file.getAbsolutePath());
        }
    }

    @SuppressWarnings("serial")
    public Closure<AssetIndex> getAssetIndexClosure()
    {
        return new Closure<AssetIndex>(this, null) {
            public AssetIndex call(Object... obj)
            {
                return getAssetIndex();
            }
        };
    }

    public AssetIndex getAssetIndex()
    {
        return assetIndex;
    }

    /**
     * This extension object will have the name "minecraft"
     * @return
     */
    @SuppressWarnings("unchecked")
    protected Class<K> getExtensionClass()
    {
        return (Class<K>) BaseExtension.class;
    }

    /**
     * @return the extension object with name EXT_NAME_MC
     * @see Constants.EXT_NAME_MC
     */
    @SuppressWarnings("unchecked")
    public final K getExtension()
    {
        return (K) project.getExtensions().getByName(Constants.EXT_NAME_MC);
    }

    public DefaultTask makeTask(String name)
    {
        return makeTask(name, DefaultTask.class);
    }

    public <T extends Task> T makeTask(String name, Class<T> type)
    {
        return makeTask(project, name, type);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Task> T makeTask(Project proj, String name, Class<T> type)
    {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("name", name);
        map.put("type", type);
        return (T) proj.task(map, name);
    }

    public static Project getProject(File buildFile, Project parent)
    {
        ProjectBuilder builder = ProjectBuilder.builder();
        if (buildFile != null)
        {
            builder = builder.withProjectDir(buildFile.getParentFile())
                    .withName(buildFile.getParentFile().getName());
        }
        else
        {
            builder = builder.withProjectDir(new File("."));
        }

        if (parent != null)
        {
            builder = builder.withParent(parent);
        }

        Project project = builder.build();

        if (buildFile != null)
        {
            HashMap<String, String> map = new HashMap<String, String>();
            map.put("from", buildFile.getAbsolutePath());

            project.apply(map);
        }

        return project;
    }

    public void applyExternalPlugin(String plugin)
    {
        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("plugin", plugin);
        project.apply(map);
    }

    public MavenArtifactRepository addMavenRepo(Project proj, final String name, final String url)
    {
        return proj.getRepositories().maven(new Action<MavenArtifactRepository>() {
            @Override
            public void execute(MavenArtifactRepository repo)
            {
                repo.setName(name);
                repo.setUrl(url);
            }
        });
    }

    public FlatDirectoryArtifactRepository addFlatRepo(Project proj, final String name, final Object... dirs)
    {
        return proj.getRepositories().flatDir(new Action<FlatDirectoryArtifactRepository>() {
            @Override
            public void execute(FlatDirectoryArtifactRepository repo)
            {
                repo.setName(name);
                repo.dirs(dirs);
            }
        });
    }

    @Override
    public String resolve(String pattern, Project project, K exten)
    {
        if (version != null)
            pattern = pattern.replace("{ASSET_INDEX}", version.getAssets());
        return pattern;
    }

    protected DelayedString delayedString(String path)
    {
        return new DelayedString(project, path, this);
    }

    protected DelayedFile delayedFile(String path)
    {
        return new DelayedFile(project, path, this);
    }

    protected DelayedAlternatorFile delayedFile(String path, String... alternates)
    {
        DelayedAlternatorFile delayed = new DelayedAlternatorFile(project, path, this);
        for (String pat : alternates)
            delayed.add(pat);
        return delayed;
    }

    protected DelayedFileTree delayedFileTree(String path)
    {
        return new DelayedFileTree(project, path, this);
    }

    protected DelayedFileTree delayedZipTree(String path)
    {
        return new DelayedFileTree(project, path, true, this);
    }

    private enum VersionInfoUrl {
        CLIENT, SERVER, ASSETS;
    }

    protected DelayedString delayedVersionInfo(final VersionInfoUrl wanted) {
        return new DelayedString(this.project, "") {
            private static final long serialVersionUID = 2653205184054155514L;

            @Override
            public String call() {
                try {
                    Version manifest = JsonFactory.loadVersion(delayedFile(Constants.VERSION_JSON).call());
                    switch (wanted) {
                        case CLIENT: return manifest.downloads.client.url;
                        case SERVER: return manifest.downloads.server.url;
                        case ASSETS: return manifest.assetIndex.url;
                        default: throw new IllegalArgumentException("Unknown download type: " + wanted);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
