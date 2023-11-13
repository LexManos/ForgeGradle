package net.minecraftforge.gradle.data;

import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class PatcherConfig extends Config {
    public String mcp;    // Do not specify this unless there is no parent.
    public String parent; // To fully resolve, we must walk the parents until we hit null, and that one must specify a MCP value.
    public List<String> ats;
    public List<String> sass;
    public List<String> srgs;
    public List<String> srg_lines;
    public String binpatches; //To be applied to joined.jar, remapped, and added to the classpath
    public Function binpatcher;
    public String patches;
    public String sources;
    public String universal; //Injtected into the final jar, TODO: [FG][PatcherConfig] Make Universal Jar seperate from main jar
    public List<String> libraries; //Additional libraries.
    public String inject;
    public Map<String, Object/*RunConfig*/> runs;
    public String sourceCompatibility; // Default to 1.8
    public String targetCompatibility; // Default to 1.8

    public List<String> libraries() {
        return libraries == null ? List.of() : libraries;
    }

    public static class Function {
        public String version; //Maven artifact for the jar to run
        public String repo; //Maven repo to download the jar from
        public List<String> args;
        public List<String> jvmargs;
        public Integer java_version;
    }

    public static class V2 extends PatcherConfig {
        public DataFunction processor;
        public String patchesOriginalPrefix;
        public String patchesModifiedPrefix;
        public Boolean notchObf; //This is a Boolean so we can set to null and it won't be printed in the json.
        public List<String> universalFilters;
        public List<String> modules; // Modules passed to --module-path
        public String sourceFileCharset; // = StandardCharsets.UTF_8.name();

        public V2(PatcherConfig o) {
            this.mcp = o.mcp;
            this.parent = o.parent;
            this.ats = o.ats;
            this.sass = o.sass;
            this.srgs = o.srgs;
            this.srg_lines = o.srg_lines;
            this.binpatches = o.binpatches;
            this.binpatcher = o.binpatcher;
            this.patches = o.patches;
            this.sources = o.sources;
            this.universal = o.universal;
            this.libraries = o.libraries;
            this.inject = o.inject;
            this.runs = o.runs;
            this.sourceCompatibility = o.sourceCompatibility;
            this.targetCompatibility = o.targetCompatibility;
        }

        public static class DataFunction extends Function {
            public Map<String, String> data;
        }

        public boolean notchObf() {
            return this.notchObf != null && this.notchObf;
        }
    }
}
