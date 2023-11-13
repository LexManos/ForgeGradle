package net.minecraftforge.gradle.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class MCPConfig extends Config {
    public String version; // Minecraft version
    public Map<String, Object> data;
    public Map<String, List<Map<String, String>>> steps;
    public Map<String, Function> functions;
    public Map<String, List<String>> libraries;

    public Map<String, String> getData(String side) {
        if (data == null)
            return Collections.emptyMap();
        var ret = new HashMap<String, String>();
        data.forEach((k, v) -> ret.put(k, (String)(v instanceof Map m ? m.get(side) : v)));
        return ret;
    }

    public List<String> getLibraries(String side) {
        if (libraries == null)
            return List.of();
        return libraries.getOrDefault(side, List.of());
    }

    public static class Function {
        public String version; //Maven artifact for the jar to run
        public String repo; //Maven repo to download the jar from
        public List<String> args;
        public List<String> jvmargs;
        public Integer java_version;
    }

    public static class V2 extends MCPConfig {
        public boolean official = false;
        public int java_target = 8;
        public String encoding = "UTF-8";

        public V2(MCPConfig o) {
            this.version = o.version;
            this.data = o.data;
            this.steps = o.steps;
            this.functions = o.functions;
            this.libraries = o.libraries;
        }
    }
}
