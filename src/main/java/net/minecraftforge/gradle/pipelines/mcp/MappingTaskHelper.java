package net.minecraftforge.gradle.pipelines.mcp;

import net.minecraftforge.gradle.tasks.SingleFileOutput;

// TODO: [FG] Document and make this public API
public interface MappingTaskHelper {
    SingleFileOutput obf2srg();
    SingleFileOutput srg2obf();

    SingleFileOutput names();
    SingleFileOutput names(String channel, String version);

    SingleFileOutput srg2names();
    SingleFileOutput srg2names(String channel, String version);

    SingleFileOutput names2srg();
    SingleFileOutput names2srg(String channel, String version);

    SingleFileOutput obf2names();
    SingleFileOutput obf2names(String channel, String version);

    SingleFileOutput names2obf();
    SingleFileOutput names2obf(String channel, String version);
}
