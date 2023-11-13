package net.minecraftforge.gradle.data;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import org.jetbrains.annotations.ApiStatus;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;

import net.minecraftforge.gradle.util.Util;

@ApiStatus.Internal
public class JsonData {
    public static LauncherManifest launcherManifest(File file) {
        return fromJson(file, LauncherManifest.class);
    }
    public static LauncherManifest launcherManifest(InputStream stream) {
        return fromJson(stream, LauncherManifest.class);
    }
    public static LauncherManifest launcherManifest(byte[] data) {
        return fromJson(data, LauncherManifest.class);
    }

    public static MinecraftVersion minecraftVersion(File file) {
        return fromJson(file, MinecraftVersion.class);
    }
    public static MinecraftVersion minecraftVersion(InputStream stream) {
        return fromJson(stream, MinecraftVersion.class);
    }
    public static MinecraftVersion minecraftVersion(byte[] data) {
        return fromJson(data, MinecraftVersion.class);
    }

    public static int configSpec(File file) {
        return fromJson(file, Config.class).spec;
    }
    public static int configSpec(InputStream stream) {
        return fromJson(stream, Config.class).spec;
    }
    public static int configSpec(byte[] data) {
        return fromJson(data, Config.class).spec;
    }

    public static PatcherConfig patcherConfig(File file) {
        return fromJson(file, PatcherConfig.class);
    }
    public static PatcherConfig patcherConfig(InputStream stream) {
        return fromJson(stream, PatcherConfig.class);
    }
    public static PatcherConfig patcherConfig(byte[] data) {
        return fromJson(data, PatcherConfig.class);
    }

    public static PatcherConfig.V2 patcherConfigV2(File file) {
        return fromJson(file, PatcherConfig.V2.class);
    }
    public static PatcherConfig.V2 patcherConfigV2(InputStream stream) {
        return fromJson(stream, PatcherConfig.V2.class);
    }
    public static PatcherConfig.V2 patcherConfigV2(byte[] data) {
        return fromJson(data, PatcherConfig.V2.class);
    }

    public static MCPConfig mcpConfig(File file) {
        return fromJson(file, MCPConfig.class);
    }
    public static MCPConfig mcpConfig(InputStream stream) {
        return fromJson(stream, MCPConfig.class);
    }
    public static MCPConfig mcpConfig(byte[] data) {
        return fromJson(data, MCPConfig.class);
    }

    public static MCPConfig.V2 mcpConfigV2(File file) {
        return fromJson(file, MCPConfig.V2.class);
    }
    public static MCPConfig.V2 mcpConfigV2(InputStream stream) {
        return fromJson(stream, MCPConfig.V2.class);
    }
    public static MCPConfig.V2 mcpConfigV2(byte[] data) {
        return fromJson(data, MCPConfig.V2.class);
    }

    private static <T> T fromJson(File file, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
        try (var stream = new FileInputStream(file)) {
            return fromJson(stream, classOfT);
        } catch (IOException e) {
            throw new JsonIOException(e);
        }
    }
    private static <T> T fromJson(InputStream stream, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
        return Util.GSON.fromJson(new InputStreamReader(stream), classOfT);
    }
    private static <T> T fromJson(byte[] data, Class<T> classOfT) throws JsonSyntaxException, JsonIOException {
        return fromJson(new ByteArrayInputStream(data), classOfT);
    }
}
