package net.minecraftforge.gradle.data;

import java.net.URL;
import java.util.Map;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class MinecraftVersion {
    public String id;
    public Map<String, Download> downloads;
    public Library[] libraries;

    //TODO: [FG][MinecraftVersion] Add function to filter libraries based on current operating systems
    public static class Library {
        public String name;
        public Downloads downloads;
    }

    public static class Downloads {
        @Nullable public Map<String, LibraryDownload> classifiers;
        @Nullable public LibraryDownload artifact;
    }

    public static class Download {
        public String sha1;
        public URL url;
        public int size;
    }

    public static class LibraryDownload extends Download {
        public String path;
    }
}
