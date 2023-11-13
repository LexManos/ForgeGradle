package net.minecraftforge.gradle.tasks;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;

import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import net.minecraftforge.gradle.util.Artifact;
import net.minecraftforge.gradle.util.DownloadUtils;
import net.minecraftforge.gradle.util.HashFunction;
import net.minecraftforge.gradle.util.Tools;

// TODO: Central cache? Cant use hashs because of task caching, maybe make a companion 'getMavenArtifactHash' task?
public abstract class DownloadMavenArtifactManual extends SimpleDownloadTask {
    public DownloadMavenArtifactManual() {
        getMaven().convention(Tools.FORGE_MAVEN);
        var tmp = getProject().getLayout().getBuildDirectory().dir(getName());
        getOutput().convention(tmp.map(d -> d.file("output.jar")));
        getHashFunction().convention(HashFunction.MD5);
    }

    @Input
    public abstract Property<String> getMaven();

    @Input
    public abstract Property<HashFunction> getHashFunction();

    @Input
    public abstract Property<String> getArtifact();

    @Override
    public void run() throws IOException {
        var output = getOutputFile();
        var url = getMaven().get() + Artifact.from(getArtifact().get()).getPath();

        if (output.exists()) {
            var func = getHashFunction().get();
            var expected = DownloadUtils.downloadString(new URL(url + '.' + func.getExtension()));
            if (expected != null) {
                expected = expected.toLowerCase(Locale.ROOT);
                var current = func.hash(output);
                if (current.equals(expected)) {
                    getState().setDidWork(false);
                    return;
                }
            }
        }
        download.src(url);
        super.run();
    }
}
