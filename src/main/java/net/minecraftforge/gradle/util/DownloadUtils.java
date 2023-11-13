/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */
package net.minecraftforge.gradle.util;

import com.google.common.net.MediaType;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nullable;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Consumer;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

@ApiStatus.Internal
public class DownloadUtils {
    private static final Map<String, DecompressionStrategy> DECOMPRESSION_STRATEGIES = Map.of(
        "identity", stream -> stream,
        "gzip", GZIPInputStream::new,
        "deflate", InflaterInputStream::new
    );
    private static final String ACCEPT_ENCODING = String.join(", ", DECOMPRESSION_STRATEGIES.keySet());

    @Nullable
    public static String downloadString(URL url) throws IOException {
        var proto = url.getProtocol().toLowerCase();

        if ("http".equals(proto) || "https".equals(proto)) {
            var con = connectHttpWithRedirects(url);
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                return downloadString(con);
            }
        } else {
            var con = url.openConnection();
            con.connect();
            return downloadString(con);
        }
        return null;
    }

    private static String downloadString(URLConnection con) throws IOException {
        var len = con.getContentLength();
        var is = getInputStream(con);
        var out = new ByteArrayOutputStream();
        var read = IOUtils.copy(is, out);
        if (isEncoded(con) && len != -1 && read != len) {
            throw new IOException("Failed to read all data from " + con.getURL() + "; got " + read + " expected " + len);
        }

        var charset = StandardCharsets.UTF_8;
        if (con.getContentType() != null) {
            try {
                charset = MediaType.parse(con.getContentType()).charset().or(StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {}
        }
        return new String(out.toByteArray(), charset);
    }

    private static HttpURLConnection connectHttpWithRedirects(URL url) throws IOException {
        return connectHttpWithRedirects(url, (setupCon) -> {});
    }

    private static HttpURLConnection connectHttpWithRedirects(URL url, Consumer<HttpURLConnection> setup) throws IOException {
        var con = (HttpURLConnection) url.openConnection();
        con.setInstanceFollowRedirects(true);
        con.addRequestProperty("Accept-Encoding", ACCEPT_ENCODING);
        setup.accept(con);
        con.connect();
        if ("http".equalsIgnoreCase(url.getProtocol())) {
            int responseCode = con.getResponseCode();
            switch (responseCode) {
                case HttpURLConnection.HTTP_MOVED_TEMP:
                case HttpURLConnection.HTTP_MOVED_PERM:
                case HttpURLConnection.HTTP_SEE_OTHER:
                    var newLocation = con.getHeaderField("Location");
                    var newUrl = new URL(newLocation);
                    if ("https".equalsIgnoreCase(newUrl.getProtocol())) {
                        // Escalate from http to https.
                        // This is not done automatically by HttpURLConnection.setInstanceFollowRedirects
                        // See https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4959149
                        return connectHttpWithRedirects(newUrl, setup);
                    }
                    break;
            }
        }
        return con;
    }

    private static InputStream getInputStream(URLConnection connection) throws IOException {
        var encoding = connection.getContentEncoding();
        if (encoding == null || encoding.isEmpty()) return connection.getInputStream();
        var is = connection.getInputStream();
        var encodings = encoding.split(",");
        for (var enc : encodings) {
            var strategy = DECOMPRESSION_STRATEGIES.get(enc.trim());
            if (strategy == null)
                throw new IOException("Unknown content-encoding \"" + enc + "\"!");
            is = strategy.wrap(is);
        }
        return is;
    }

    private static boolean isEncoded(URLConnection connection) {
        var enc = connection.getContentEncoding();
        return enc == null || enc.equals("identity");
    }

    @FunctionalInterface
    public interface DecompressionStrategy {
        InputStream wrap(InputStream stream) throws IOException;
    }

    public static File downloadSingleFile(Project target, String coord) {
        var udep = target.getDependencies().create(coord);
        var cfg = target.getConfigurations().detachedConfiguration(udep);
        cfg.setTransitive(false);
        var files = cfg.resolve();

        if (files.isEmpty())
            throw new IllegalStateException("Failed to resolve " + coord + ": No Results");
        if (files.size() != 1)
            throw new IllegalStateException("Failed to resolve " + coord + ": Too Many Results");

        return files.iterator().next();
    }
}
