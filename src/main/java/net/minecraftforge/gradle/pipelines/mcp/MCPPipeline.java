package net.minecraftforge.gradle.pipelines.mcp;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.ForgeGradleExtension;
import net.minecraftforge.gradle.pipelines.Pipeline;
import net.minecraftforge.gradle.repo.RepositoryArtifact;
import net.minecraftforge.gradle.repo.RepositoryManager;
import net.minecraftforge.gradle.util.DownloadUtils;
import net.minecraftforge.gradle.util.Util;

@ApiStatus.Internal
public class MCPPipeline implements Pipeline {
    private final Project project;
    private final ForgeGradleExtension fg;
    private record Key(String coord, String side) {}
    private final Map<Key, List<RepositoryArtifact>> cache = new HashMap<>();
    private final Map<String, MCP> mcps = new HashMap<>();

    public MCPPipeline(Project target, ForgeGradleExtension fg) {
        this.project = target;
        this.fg = fg;
    }

    /*
     * MCPConfig is a data driven system designed to give a compilable minecraft codebase.
     * It also allows the addition of libraries beyond what vanilla gives you.
     *
     * For our purposes we make a few assumptions about the MCPConfig step list.
     * These assumptions have been in place in FG for years, but really should be standardized
     * in a MCPCOnfig spec bump.
     *
     * I assume there is a step called 'decompile' which takes an input named 'input'
     * That will be the start of our binary artifact
     * Rename that using the configured mapping channel/version from the extension.
     *
     * For source artifact, i'll take the output of the final step
     * Rename it using the configured mappings
     * Recompile the entire sourcecode to get updated line mappings
     * TODO: [FG][MCP] Recompile only changed files, update line mappings without recompile.
     *
     * I also assume there is a 'rename' step with a argument named 'mappings'
     * That is used as the obf->srg mappings.
     *
     * Older FG created an 'extra' jar that would contain any data files, and non-mapped classes
     * These non-mapped classes are typically the libraries that the fat-jared server required.
     * For the joined and client sides we just use the libraries from the client JSON file.
     * For the server side, I make another artifact under the 'server-libraries' name.
     * This is done instead of a classifier because gradle has issues related to multiple
     * classifiers from the same artifact
     *
     * Also instead of making a 'extra' jar, I re-package all non-class files into the binary
     * artifact. Making sure that the texts/data is packed where it needs to be for vanilla minecraft
     * Forge may have issues with this because it expects a special 'extra' jar. But I'll deal with
     * that when the time comes.
     */
    @Override
    public Dependency handleDependency(RepositoryManager repo, Dependency dep) {
        var key = getKey(dep);
        var artifacts = cache.get(key);

        if (artifacts == null) {
            artifacts = buildArtifacts(dep, key);
            cache.put(key, artifacts);
        }

        for (var art : artifacts)
            repo.addArtifact(art);

        return dep;
    }

    private Key getKey(Dependency dep) {
        var name = dep.getName();
        String side;

        if (name.endsWith("-joined"))
            side = "joined";
        else if (name.endsWith("-client"))
            side = "client";
        else if (name.endsWith("-server"))
            side = "server";
        else
            throw new IllegalArgumentException("Invalid MPC dependency " + dep + " name must end with '-client', '-server', or '-joined'");

        name = name.substring(0, name.length() - 7);
        var coords = dep.getGroup() + ':' + name + ':' + dep.getVersion() + "@zip";
        return new Key(coords, side);
    }

    private List<RepositoryArtifact> buildArtifacts(Dependency dep, Key key) {
        var side = key.side();
        var mcp = getMcp(key.coord(), key.side());
        var mcpside = mcp.getSide(side);
        if (mcpside == null)
            throw new IllegalArgumentException("Invalid MCP Config: " + key.coord() + " - Missing side `" + side +"`");
        return mcpside.buildArtifacts(dep, !Util.isSourceDisabled());
    }

    public MCP getMcp(String coord, String side) {
        var ret = getMcp(coord);
        var mcpside = ret.getSide(side);
        mcpside.setupTasks();
        return ret;
    }

    private MCP getMcp(String coord) {
        var ret = mcps.get(coord);
        if (ret != null)
            return ret;

        var file = DownloadUtils.downloadSingleFile(project, coord);
        ret = new MCP(project, coord, file, this.fg);
        mcps.put(coord, ret);
        return ret;
    }
}
