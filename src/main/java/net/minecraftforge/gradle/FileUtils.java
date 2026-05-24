package net.minecraftforge.gradle;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

import com.google.common.base.Charsets;

public class FileUtils {
    public static String readString(File file) throws IOException {
        return readString(file, Charsets.UTF_8);
    }

    public static String readString(File file, Charset charset) throws IOException {
        InputStream input = null;
        try {
            input = new FileInputStream(file);
            return StringUtils.fromUTF8Stream(input);
        } finally {
            input.close();
        }
    }

    public static void write(File file, String data) throws IOException {
        write(file, data, Charsets.UTF_8);
    }

    public static void write(File file, String data, Charset charset) throws IOException {
        write(file, data.getBytes(charset));
    }

    public static void write(File file, byte[] data) throws IOException {
        ensureParent(file);
        OutputStream out = new FileOutputStream(file);
        out.write(data);
        out.flush();
        out.close();
    }

    public static void updateDate(File file) throws IOException {
        if (!file.createNewFile() && !file.setLastModified(System.currentTimeMillis())) {
            throw new IOException("Unable to update modification time of " + file);
        }
    }

    public static String getFileExtension(String fullName) {
        if (fullName == null)
            throw new IllegalArgumentException("fullName can not be null");
        String fileName = new File(fullName).getName();
        int dotIndex = fileName.lastIndexOf('.');
        return (dotIndex == -1) ? "" : fileName.substring(dotIndex + 1);
    }

    public static void ensure(File path) {
        if (!path.isAbsolute())
            path = path.getAbsoluteFile();

        if (!path.exists() && !path.getAbsoluteFile().mkdirs())
            throw new IllegalStateException("Failed to ensure existence of directory: " + path.getAbsolutePath());
    }

    public static void ensureParent(File path) {
        ensure(path.getAbsoluteFile().getParentFile());
    }
}