package net.minecraftforge.gradle.tasks.abstractutil;

import groovy.lang.Closure;
import net.minecraftforge.gradle.FileUtils;
import net.minecraftforge.gradle.common.Constants;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.*;

public class EtagDownloadTask extends DefaultTask {
    Object uri;
    Object file;
    boolean dieWithError;

    @TaskAction
    public void doTask() throws IOException, URISyntaxException {
        URI uri = getUri();
        File outFile = getFile();
        File etagFile = getProject().file(getFile().getPath() + ".etag");

        // ensure folder exists
        outFile.getParentFile().mkdirs();

        String etag;
        if (etagFile.exists()) {
            etag = FileUtils.readString(etagFile);
        } else {
            etag = "";
        }

        try {
            HttpURLConnection con = (HttpURLConnection) uri.toURL().openConnection();
            con.setInstanceFollowRedirects(true);
            con.setRequestProperty("User-Agent", Constants.USER_AGENT);
            con.setRequestProperty("If-None-Match", etag);

            con.connect();

            switch (con.getResponseCode()) {
                case 404: // file not found.... duh...
                    error(uri + "  404'ed!");
                    break;
                case 304: // content is the same.
                    this.setDidWork(false);
                    break;
                case 200: // worked
                    // write file
                    FileUtils.ensureParent(outFile);
                    InputStream input = con.getInputStream();
                    OutputStream output = new FileOutputStream(outFile);

                    byte[] data = new byte[1024];
                    int read = input.read(data);
                    while (read != -1) {
                        output.write(data, 0, read);
                        read = input.read(data);
                    }

                    input.close();
                    output.flush();
                    output.close();

                    // write etag
                    etag = con.getHeaderField("ETag");
                    if (etag != null && !etag.isEmpty())
                        FileUtils.write(etagFile, etag);

                    break;
                default: // another code?? uh..
                    error("Unexpected reponse " + con.getResponseCode() + " from " + uri);
                    break;
            }

            con.disconnect();
        } catch (Throwable e) {
            // just in case people dont have internet at the moment.
            error(e.getLocalizedMessage());
        }
    }

    private void error(String error) {
        if (dieWithError) {
            throw new RuntimeException(error);
        } else {
            getLogger().error(error);
        }
    }

    @Deprecated
    public URL getUrl() throws MalformedURLException {
        try {
            return getUri().toURL();
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    public void setUrl(Object url) {
        this.setUri(url);
    }

    @Input
    public URI getUri() throws URISyntaxException {
        while (uri instanceof Closure<?>) {
            uri = ((Closure<?>) uri).call();
        }

        return new URI(uri.toString());
    }

    public void setUri(Object url) {
        this.uri = url;
    }

    @OutputFile
    public File getFile() {
        return getProject().file(file);
    }

    public void setFile(Object file) {
        this.file = file;
    }

    @Input
    public boolean isDieWithError() {
        return dieWithError;
    }

    public void setDieWithError(boolean dieWithError) {
        this.dieWithError = dieWithError;
    }
}