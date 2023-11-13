package net.minecraftforge.gradle.pipelines.mcp;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.jetbrains.annotations.ApiStatus;

import net.minecraftforge.gradle.ForgeGradleExtension;
import net.minecraftforge.gradle.data.JsonData;
import net.minecraftforge.gradle.data.MCPConfig;
import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.util.Artifact;
import net.minecraftforge.gradle.util.FileHelper;
import net.minecraftforge.gradle.util.Util;

@ApiStatus.Internal
public class MCP {
    final Artifact name;
    final String prefix;
    final File data;
    final MCPConfig.V2 config;
    final FileHelper files;
    final ForgeGradleExtension fg;

    private Map<String, MCPSide> sides = new HashMap<>();

    MCP(Project project, String name, File data, ForgeGradleExtension fg) {
        this.name = Artifact.from(name);
        if ("mcp_config".equals(this.name.getName()))
            this.prefix = Util.sanitizeTaskName(this.name.getVersion());
        else
            this.prefix = Util.sanitizeTaskName(this.name.getName() + '_' + this.name.getVersion());
        this.files = new FileHelper(project, "_mcp", this.prefix);
        this.data = data;
        this.fg = fg;
        this.config = loadConfig(name, this.data);
        validateConfig();
        for (var side : this.config.steps.keySet())
            sides.put(side, new MCPSide(project, this, this.files, side));
    }

    private static MCPConfig.V2 loadConfig(String name, File data) {
        try (var zip = new ZipFile(data)) {
            var entry = zip.getEntry("config.json");
            if (entry == null)
                throw new IllegalStateException("Invalid MCPConfig dependency: " + name + " - Missing config.json");
            var cfg_data = zip.getInputStream(entry).readAllBytes();

            int spec = JsonData.configSpec(cfg_data);

            if (spec == 2 || spec == 3 || spec == 4)
                return JsonData.mcpConfigV2(zip.getInputStream(entry));
            else if (spec == 1)
                return new MCPConfig.V2(JsonData.mcpConfig(zip.getInputStream(entry)));
            else
                throw new IllegalStateException("Invalid MCP Config: " + name + " - Unknown Spec: " + spec);
        } catch (IOException e) {
            throw new RuntimeException("Invalid MCPConfig dependency: " + name, e);
        }
    }

    private void validateConfig() {
        if (config.steps == null || config.steps.isEmpty())
            throw new IllegalStateException("Invalid MCP Config: " + name + " - Missing steps");

        for (var side : config.steps.keySet()) {
            var steps = config.steps.get(side);
            for (int x = 0; x < steps.size(); x++) {
                var type = steps.get(x).get("type");
                if (type == null)
                    throw new IllegalStateException("Invalid MCP Config: " + name + " - Step " + side + "[" + x + "] Missing `type`");

                switch (type) {
                    case "downloadClientMappings":
                    case "downloadServerMappings":
                        // Added in spec 2
                        if (config.spec < 2)
                            throw new IllegalStateException("Invalid MCP Config: " + name + " - Step " + side + "[" + x + "] `" + type + "` is only supported on spec 2 or higher, found spec: " + config.spec);
                        break;
                    case "listLibraries":
                        // Spec v3 adds 'bundle' to the listLibraries task.
                        if (config.spec < 3 && steps.get(x).containsKey("bundle"))
                            throw new IllegalStateException("Invalid MCP Config: " + name + " - Step " + side + "[" + x + "] `listLibraries.bundle` is only supported on spec 3 or higher, found spec: " + config.spec);
                        break;
                }
            }
        }

        // java_version
        if (config.spec < 4 && config.functions != null) {
            for (var func : config.functions.values()) {
                if (func.java_version != null)
                    throw new IllegalStateException("Invalid MCP Config: " + name + " - Function `java_version` property is only supported on spec 4 or higher, found spec: " + config.spec);
            }
        }
    }

    public MCPConfig.V2 getConfig() {
        return this.config;
    }

    public File getData() {
        return this.data;
    }

    public MCPSide getSide(String side) {
        return this.sides.get(side);
    }

    public static record Side(
        Map<String, SingleFileOutput> steps,
        SingleFileOutput predecomp,
        SingleFileOutput last,
        Map<String, String> data,
        Map<String, ExtractTask> extracts,
        MappingTaskHelper mappings
    ) {}
    record ExtractTask(Task task, String path) {};
}
