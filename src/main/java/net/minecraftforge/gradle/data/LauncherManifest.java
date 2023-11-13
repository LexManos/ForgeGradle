package net.minecraftforge.gradle.data;

import java.net.URL;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class LauncherManifest {
    public VersionInfo[] versions;

    public static class VersionInfo {
        public String id;
        public URL url;
    }

    public URL getUrl(String version) {
        if (version == null || versions == null)
            return null;
        for (var info : versions)
            if (version.equals(info.id))
                return info.url;
        return null;
    }
}
