package net.minecraftforge.gradle;

public class Ideas {
    /**
     *
     * Requirements:
     * 		Java 17, because I like var, and various other improvements. Plus with Gradle toolchains we can target
     * 			lower versions for the output.
     *
     * So this is a place for me to define some of the ideas that I have and want to expose to people.
     * The point of this plugin is to expose AS LITTLE as public api as possible, Whatever I do expose
     * should be explicitly defined here and explained.
     *
     * When it comes to classes, assume that anything that's NOT in the api package is not intended to be public api.
     *
     * To that effect, any tasks that I add should be named in such a way to indicate that they are not meant to be public.
     * Simplest way i can see to do this is to prefix all task names with a _ it's not that intrusive and also shows the internal
     * intention. It would also allow tasks to be grouped with 'namespaces' such as `_mcp_decompile_client`. its ugly but makes sense
     * to me.
     *
     * The crux of the functionality of this project is that I create a fake Ivy repository, and then have a 'buildDependencies'
     * and 'buildDependenciesSources' tasks, exposed by a extension? so that if modders run into cases where they need to make their
     * own tasks depend on these dependencies existing in a valid state, they have a simple way `myTask.dependsOn(fg.buildDependencies)`
     *
     * I can either use an Ivy Repository, or a Maven repository, because a flatRepo does not support any metadata what so ever, so
     * I can't have any transitive dependencies. When using local respositories, gradle now bypasses all caching, which is nice. I had
     * a lot of issues with that in FG. https://docs.gradle.org/current/userguide/declaring_repositories.html#sub:local-repos
     *
     * So phase one, get a proof of concept working.
     *
     */
}
