/*
 * Copyright (c) Forge Development LLC and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.minecraftforge.gradle.mappings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipOutputStream;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.annotations.ApiStatus;

import com.google.common.collect.ImmutableSet;

import de.siegmar.fastcsv.writer.CsvWriter;
import de.siegmar.fastcsv.writer.LineDelimiter;
import net.minecraftforge.gradle.ForgeGradleExtension;
import net.minecraftforge.gradle.tasks.SingleFileOutput;
import net.minecraftforge.gradle.util.FileHelper;
import net.minecraftforge.gradle.util.HashFunction;
import net.minecraftforge.gradle.util.Util;
import net.minecraftforge.srgutils.IMappingFile;

@ApiStatus.Internal
class OfficialMappings implements MappingsProvider {
    private record Key(String version, String hash) {}
    private final Map<Key, MakeOfficialMappings> tasks = new HashMap<>();
    private final Project project;
    private final FileHelper local;

    OfficialMappings(Project project) {
        this.project = project;
        this.local = new FileHelper(project, "_mappings", "official");
    }

    @Override
    public Set<String> getChannels() {
        return ImmutableSet.of("official");
    }

    @Override
    public SingleFileOutput getMappings(String channel, String version, SingleFileOutput obfToIntermediate) {
        var crc = HashFunction.CRC32.hash(obfToIntermediate.getName().getBytes(StandardCharsets.UTF_8));
        var key = new Key(version, crc);
        var task = tasks.get(key);
        if (task != null)
            return task;

        var fg = project.getExtensions().getByType(ForgeGradleExtension.class);

        var output = local.file(version, "mappings_" + crc + ".zip");
        task = project.getTasks().create("_mappings_official_" + Util.sanitizeTaskName(version) + '_' + crc + "_make", MakeOfficialMappings.class);
        task.setGroup("Forge Gradle Internal - Official Mappings");
        task.getOfficialClientMappings().convention(fg.getMcTasks().getVersionFile(version, "client_mappings", "txt").getOutput());
        task.getOfficialServerMappings().convention(fg.getMcTasks().getVersionFile(version, "server_mappings", "txt").getOutput());
        task.getIntermediate().convention(obfToIntermediate.getOutput());
        task.getOutput().convention(output);

        tasks.put(key, task);
        return task;
    }

    public static abstract class MakeOfficialMappings extends DefaultTask implements SingleFileOutput {
        @InputFile
        public abstract RegularFileProperty getIntermediate();

        @InputFile
        public abstract RegularFileProperty getOfficialClientMappings();
        @InputFile
        public abstract RegularFileProperty getOfficialServerMappings();

        @TaskAction
        public void exec() throws IOException {
            File tsrg = getIntermediate().getAsFile().get();
            File mappings = getOutputFile();

            var pg_client = IMappingFile.load(getOfficialClientMappings().getAsFile().get());
            var pg_server = IMappingFile.load(getOfficialServerMappings().getAsFile().get());

            IMappingFile srg = IMappingFile.load(tsrg);

            var cfields = new TreeMap<String, String>();
            var sfields = new TreeMap<String, String>();
            var cmethods = new TreeMap<String, String>();
            var smethods = new TreeMap<String, String>();

            findValues(srg, pg_client, cfields, cmethods);
            findValues(srg, pg_server, sfields, smethods);

            var header = new String[] {"searge", "name", "side", "desc"};
            var fields = new ArrayList<String[]>();
            var methods = new ArrayList<String[]>();
            fields.add(header);
            methods.add(header);

            for (String name : cfields.keySet()) {
                String cname = cfields.get(name);
                String sname = sfields.get(name);
                if (cname.equals(sname)) {
                    fields.add(new String[]{name, cname, "2", ""});
                    sfields.remove(name);
                } else
                    fields.add(new String[]{name, cname, "0", ""});
            }

            for (String name : cmethods.keySet()) {
                String cname = cmethods.get(name);
                String sname = smethods.get(name);
                if (cname.equals(sname)) {
                    methods.add(new String[]{name, cname, "2", ""});
                    smethods.remove(name);
                } else
                    methods.add(new String[]{name, cname, "0", ""});
            }

            sfields.forEach((k,v) -> fields.add(new String[] {k, v, "1", ""}));
            smethods.forEach((k,v) -> methods.add(new String[] {k, v, "1", ""}));

            if (!mappings.getParentFile().exists())
                mappings.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(mappings);
                    ZipOutputStream out = new ZipOutputStream(fos)) {
                writeCsv("fields.csv", fields, out);
                writeCsv("methods.csv", methods, out);
            }
        }
    }

    private static void findValues(IMappingFile srg, IMappingFile pg_client, Map<String, String> cfields, Map<String, String> cmethods) {

        for (var cls : pg_client.getClasses()) {
            var obf = srg.getClass(cls.getMapped());
            if (obf == null) // Class exists in official source, but doesn't make it past obfusication so it's not in our mappings.
                continue;
            for (var fld : cls.getFields()) {
                var name = obf.remapField(fld.getMapped());
                if (name.startsWith("field_") || name.startsWith("f_"))
                    cfields.put(name, fld.getOriginal());
            }
            for (var mtd : cls.getMethods()) {
                var name = obf.remapMethod(mtd.getMapped(), mtd.getMappedDescriptor());
                if (name.startsWith("func_") || name.startsWith("m_"))
                    cmethods.put(name, mtd.getOriginal());
            }
        }
    }

    protected static void writeCsv(String name, List<String[]> mappings, ZipOutputStream out) throws IOException {
        if (mappings.size() <= 1)
            return;
        out.putNextEntry(Util.getStableEntry(name));
        var output = new OutputStreamWriter(out) {
            @Override
            public void close() throws IOException {
                super.flush();
            }
        };

        var writer = CsvWriter.builder().lineDelimiter(LineDelimiter.LF).build(output);
        mappings.forEach(writer::writeRow);
        output.flush();
        out.closeEntry();
    }
}
