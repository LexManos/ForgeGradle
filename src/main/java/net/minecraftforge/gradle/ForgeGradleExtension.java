package net.minecraftforge.gradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.jetbrains.annotations.ApiStatus;

import groovy.lang.Closure;
import net.minecraftforge.gradle.mappings.MappingsExtension;
import net.minecraftforge.gradle.pipelines.Pipeline;
import net.minecraftforge.gradle.pipelines.mcp.MCPPipeline;
import net.minecraftforge.gradle.pipelines.patcher.PatcherPipeline;
import net.minecraftforge.gradle.pipelines.vanilla.VanillaPipeline;
import net.minecraftforge.gradle.repo.RepositoryManager;
import net.minecraftforge.gradle.util.MinecraftTaskHelper;

public class ForgeGradleExtension {
    public static final String NAME = "fg";
    private final Project target;
    private final MappingsExtension mappings;
    private final RepositoryManager repo;
    private final VanillaPipeline vanilla;
    private final MCPPipeline mcp;
    private final PatcherPipeline patcher;
    private final MinecraftTaskHelper mcTasks;

    public ForgeGradleExtension(Project target, MappingsExtension mappings) {
        this.target = target;
        this.mappings = mappings;
        this.mcTasks = new MinecraftTaskHelper(target);
        this.repo = new RepositoryManager(target);
        this.vanilla = new VanillaPipeline(target, this);
        this.mcp = new MCPPipeline(target, this);
        this.patcher = new PatcherPipeline(target, this);
    }

    public Dependency vanilla(Object dep)                 { return vanilla(dep, null); }
    public Dependency vanilla(Object dep, Closure<?> cfg) { return make(prefix("net.minecraft:joined:", dep), cfg, this.vanilla); }

    public Dependency mcp(Object dep)                 { return mcp(dep, null); }
    public Dependency mcp(Object dep, Closure<?> cfg) { return make(prefix("de.oceanlabs.mcp:mcp_config-joined:", dep), cfg, this.mcp); }
    public Dependency mcpClient(String version)                 { return mcpClient(version, null); }
    public Dependency mcpClient(String version, Closure<?> cfg) { return make("de.oceanlabs.mcp:mcp_config-client:" + version, cfg, this.mcp); }
    public Dependency mcpServer(String version)                 { return mcpServer(version, null); }
    public Dependency mcpServer(String version, Closure<?> cfg) { return make("de.oceanlabs.mcp:mcp_config-server:" + version, cfg, this.mcp); }

    public Dependency forge(String version)                 { return forge(version, null); }
    public Dependency forge(String version, Closure<?> cfg) { return patcher("net.minecraftforge:forge:" + version); }

    public Dependency patcher(Object dep)                 { return patcher(dep, null); }
    public Dependency patcher(Object dep, Closure<?> cfg) { return make(dep, cfg, this.patcher); }

    private Object prefix(String prefix, Object dep) {
        return dep instanceof String str && str.indexOf(':') == -1 ? prefix + dep : dep;
    }

    private Dependency make(Object dependency, Closure<?> cfg, Pipeline pipeline) {
        var dep = target.getDependencies().create(dependency, cfg);
        var ret = pipeline.handleDependency(this.repo, dep);
        if (dep != ret && cfg != null)
            target.configure(ret, cfg);
        return ret;
    }

    @ApiStatus.Internal public RepositoryManager getRepo() { return this.repo; }
    @ApiStatus.Internal public MinecraftTaskHelper getMcTasks() { return this.mcTasks; }
    @ApiStatus.Internal public MCPPipeline getMcpPipeline() { return this.mcp; }
    @ApiStatus.Internal public MappingsExtension getMappings() { return this.mappings; }
}
