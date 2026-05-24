package net.minecraftforge.gradle.json;

import org.gradle.api.Nullable;

/** Represents the launcher manifest for Minecraft versions. */
public class LauncherManifest {
    /** All Minecraft version manifest infos. */
    public VersionInfo[] versions;

    /** Represents a Minecraft version manifest info. */
    public static class VersionInfo {
        public String id;
        public String url;
        /** Added in version_manifest_v2.json, so possibly null. */
        @Nullable
        public String sha1;
    }

    /**
     * @param version The Minecraft version
     * @return The version entry from this manifest, null if no matching value could be found
     */
    public @Nullable VersionInfo getInfo(String version) {
        if (version == null || versions == null)
            return null;
        for (VersionInfo info : versions)
            if (version.equals(info.id))
                return info;
        return null;
    }
}
