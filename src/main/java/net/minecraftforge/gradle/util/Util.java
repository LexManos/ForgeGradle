package net.minecraftforge.gradle.util;

import java.io.File;
import java.util.TimeZone;
import java.util.zip.ZipEntry;

import org.gradle.api.Project;
import org.jetbrains.annotations.ApiStatus;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

@ApiStatus.Internal
public class Util {
    public static final Gson GSON = new GsonBuilder()
                                        .setPrettyPrinting()
                                        .create();
    private static final long ZIPTIME = 628041600000L;
    public static final TimeZone GMT = TimeZone.getTimeZone("GMT");

    @SuppressWarnings("unchecked")
    public static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    public static ZipEntry getStableEntry(String name) {
        return getStableEntry(name, ZIPTIME);
    }

    public static ZipEntry getStableEntry(String name, long time) {
        var _default = TimeZone.getDefault();
        TimeZone.setDefault(GMT);
        var ret = new ZipEntry(name);
        ret.setTime(time);
        TimeZone.setDefault(_default);
        return ret;
    }

    public static boolean isSourceDisabled() {
        return System.getenv("FORGE_DISABLE_SOURCE") != null; // Hacky for now, lets us disable decomp.
    }

    public static File getGlobalCache(Project project) {
        var home = project.getGradle().getGradleUserHomeDir();
        return new File(home, "caches/forge_dev_gradle");
    }

    public static String sanitizeTaskName(String value) {
        return value
            .replace(' ', '_')
            .replace('/', '_')
            .replace('\\', '_')
            .replace(':', '_')
            .replace('<', '_')
            .replace('>', '_')
            .replace('"', '_')
            .replace('?', '_')
            .replace('*', '_')
            .replace('|', '_');
    }

    public static String capitalize(String str) {
        var ret = new StringBuilder();
        for (var pt : str.split("_"))
            ret.append(Character.toUpperCase(pt.charAt(0))).append(pt.substring(1));
        return ret.toString();
    }
}
