rootProject.name = "youtube-source"

include("v2")
include("common")
// The "plugin" module targets the standalone Lavalink server and is not consumed by ukulele
// (which embeds lavaplayer directly), so it is excluded from this HttpClient-5 fork build.

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // Justin's HttpClient-5 lavaplayer fork, consumed via JitPack. Both the v1 and v2
            // catalog aliases point at the same fork so the shared `common` module compiles
            // against the HC5 `HttpContextFilter`/`HttpInterface` signatures (see ukulele/build.gradle.kts).
            version("lavaplayer-v1", "v2.2.6_31")
            version("lavaplayer-v2", "v2.2.6_31")

            library("lavaplayer-v1", "com.github.JustinFreitas.lavaplayer", "lavaplayer").versionRef("lavaplayer-v1")
            library("lavaplayer-v2", "com.github.JustinFreitas.lavaplayer", "lavaplayer").versionRef("lavaplayer-v2")

            version("lavalink", "3.7.11")
            library("lavalink-server", "dev.arbjerg.lavalink", "Lavalink-Server").versionRef("lavalink")
            library("lavaplayer-ext-youtube-rotator", "com.github.JustinFreitas.lavaplayer", "lavaplayer-ext-youtube-rotator").versionRef("lavaplayer-v1")

            // HttpClient 5, matching the lavaplayer fork's catalog (httpclient = 5.6.1).
            library("httpclient5", "org.apache.httpcomponents.client5", "httpclient5").version("5.6.2")
            library("httpcore5", "org.apache.httpcomponents.core5", "httpcore5").version("5.4.3")

            library("rhino-engine", "org.mozilla", "rhino-engine").version("1.9.1")
            library("nanojson", "com.grack", "nanojson").version("1.10")
            library("slf4j", "org.slf4j", "slf4j-api").version("2.0.18")
            library("annotations", "org.jetbrains", "annotations").version("26.1.0")

            plugin("lavalink-gradle-plugin", "dev.arbjerg.lavalink.gradle-plugin").version("1.1.2")

            val mavenPublishPlugin = version("maven-publish-plugin", "0.37.0")
            plugin("maven-publish", "com.vanniktech.maven.publish").versionRef(mavenPublishPlugin)
            plugin("maven-publish-base", "com.vanniktech.maven.publish.base").versionRef(mavenPublishPlugin)

            version("ben-manes-versions", "0.54.0")
            plugin("versions", "com.github.ben-manes.versions").versionRef("ben-manes-versions")
        }
    }
}
