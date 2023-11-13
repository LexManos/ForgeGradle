package net.minecraftforge.gradle.tasks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import net.minecraftforge.gradle.data.JsonData;
import net.minecraftforge.gradle.util.Artifact;
import net.minecraftforge.gradle.util.BundleMetadata;
import xmlparser.XmlParser;
import xmlparser.model.XmlElement;

public abstract class CreateIvyMetadata extends DefaultTask implements SingleFileOutput {
    public CreateIvyMetadata() {
        var build = getProject().getLayout().getBuildDirectory().dir(getName());
        getOutput().convention(build.map(d -> d.file("ivy.xml")));
    }

    @Input
    public abstract Property<String> getOrganization();

    @Input
    public abstract Property<String> getArtifact();

    @Input
    public abstract Property<String> getVersion();

    @InputFile
    @Optional
    public abstract RegularFileProperty getBundledServer();

    @InputFile
    @Optional
    public abstract RegularFileProperty getLauncherMeta();

    @Input
    @Optional
    public abstract ListProperty<String> getAdditionalDeps();

    @TaskAction
    public void run() throws IOException {
        var libs = new TreeMap<Artifact, Map<String, Artifact>>();

        if (getBundledServer().isPresent()) {
            try (var zip = new ZipFile(getBundledServer().get().getAsFile())) {
                var bundle = BundleMetadata.read(zip);
                if (bundle != null && bundle.libraries != null) {
                    for (var lib : bundle.libraries)
                        addLib(libs, lib.artifact);
                }
            }
        } else if (getLauncherMeta().isPresent()) {
            var meta = JsonData.minecraftVersion(getLauncherMeta().get().getAsFile());
            for (var lib : meta.libraries)
                addLib(libs, lib.name);
        }

        if (getAdditionalDeps().isPresent()) {
            for (var lib : getAdditionalDeps().get())
                addLib(libs, lib);
        }

        // https://ant.apache.org/ivy/history/latest-milestone/ivyfile.html
        var xml = node("ivy-module",
            "version", "2.0",
            "xmlns:m", "http://ant.apache.org/ivy/maven"
        );
        xml.children.add(node("info",
            "organisation", getOrganization().get(),
            "module", getArtifact().get(),
            "revision", getVersion().get()
        ));

        if (!libs.isEmpty()) {
            var deps = node("dependencies");
            xml.children.add(deps);
            for (var cls : libs.values()) {
                var lib = cls.values().iterator().next();
                var dep = node("dependency",
                    "transitive", "false",
                    "org", lib.getGroup(),
                    "name", lib.getName(),
                    "rev", lib.getVersion()
                );

                if (!"jar".equals(lib.getExtension()))
                    dep.attributes.put("ext", lib.getExtension());

                for (var art : cls.values()) {
                    if (art.getClassifier() != null || cls.values().size() > 1) {
                        var child = node("artifact", "name", art.getName());
                        if (art.getClassifier() != null)
                            child.attributes.put("m:classifier", art.getClassifier());
                        if (!"jar".equals(art.getExtension()))
                            child.attributes.put("ext", art.getExtension());
                        dep.appendChild(child);
                    }
                }
                deps.appendChild(dep);
            }
        }

        var data = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                   new XmlParser().domToXml(xml);
        Files.writeString(getOutput().get().getAsFile().toPath(), data, StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
    }

    private static Artifact key(Artifact art) {
        return Artifact.from(art.getGroup(), art.getName(), "0", "", "");
    }
    private static void addLib(Map<Artifact, Map<String, Artifact>> libs, String coord) {
        var art = Artifact.from(coord);
        var cls = art.getClassifier() == null ? "" : art.getClassifier();
        libs.computeIfAbsent(key(art), k -> new TreeMap<>()).put(cls, art);
    }

    private static XmlElement node(String name, String... attributes) {
        var ret = new XmlElement(null, name, new LinkedHashMap<>());
        if (attributes.length % 2 != 0)
            throw new IllegalArgumentException("Helper must take even number of arguments: " + attributes.length);

        for (int x = 0; x < attributes.length; x += 2)
            ret.attributes.put(attributes[x], attributes[x + 1]);
        return ret;
    }
}
