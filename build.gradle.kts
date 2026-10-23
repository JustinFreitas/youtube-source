plugins {
    alias(libs.plugins.versions)
    alias(libs.plugins.maven.publish.base) apply false
}

tasks.withType<com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask> {
    rejectVersionIf {
        val isGroovy6 = candidate.group == "org.apache.groovy" && candidate.version.startsWith("6.")
        val stableKeyword = listOf("RELEASE", "FINAL", "GA").any { candidate.version.uppercase().contains(it) }
        val regex = "^[0-9,.v-]+(-r)?$".toRegex()
        val isStable = stableKeyword || regex.matches(candidate.version)
        isGroovy6 || !isStable
    }
    outputFormatter = "json,plain"
}

val (gitVersion, release) = versionFromGit()
logger.lifecycle("Version: $gitVersion (release: $release)")

allprojects {
    group = "dev.lavalink.youtube"
    // The plugin project is the only one that should not have a snapshot version since lavalink expects the jar name to be specific
    version = if (project.name == "plugin") {
        gitVersion.removeSuffix("-SNAPSHOT")
    } else {
        gitVersion
    }


    repositories {
        mavenLocal()
        maven("https://repo.maven.apache.org/maven2/") { name = "MavenCentralDirect" }
        mavenCentral()
        maven(url = "https://maven.lavalink.dev/releases")
        maven(url = "https://jitpack.io")
    }
}

subprojects {
    apply<JavaPlugin>()
    apply<MavenPublishPlugin>()

    configure<JavaPluginExtension> {
        // The lavaplayer fork is compiled to Java 25 bytecode, so this fork must match
        // (an older JDK cannot even read the fork's class files).
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
    }

    configure<PublishingExtension> {
        val mavenUsername = findProperty("MAVEN_USERNAME") as String?
        val mavenPassword = findProperty("MAVEN_PASSWORD") as String?
        if (!mavenUsername.isNullOrEmpty() && !mavenPassword.isNullOrEmpty()) {
            repositories {
                val snapshots = "https://maven.lavalink.dev/snapshots"
                val releases = "https://maven.lavalink.dev/releases"

                maven(if (release) releases else snapshots) {
                    credentials {
                        username = mavenUsername
                        password = mavenPassword
                    }
                }
            }
        } else {
            logger.lifecycle("Not publishing to maven.lavalink.dev because credentials are not set")
        }
    }
}

fun versionFromGit(): Pair<String, Boolean> {
    val envTag = System.getenv("VERSION") ?: System.getenv("GIT_TAG") ?: System.getenv("RELEASE_TAG")
    if (envTag != null && envTag.isNotEmpty()) return envTag to true

    return try {
        val tagProcess = ProcessBuilder("git", "describe", "--tags", "--exact-match")
            .directory(project.rootDir)
            .redirectErrorStream(true)
            .start()
        val tag = tagProcess.inputStream.bufferedReader().readText().trim()
        val tagExit = tagProcess.waitFor()

        val statusProcess = ProcessBuilder("git", "status", "--porcelain")
            .directory(project.rootDir)
            .redirectErrorStream(true)
            .start()
        val status = statusProcess.inputStream.bufferedReader().readText().trim()
        statusProcess.waitFor()
        val clean = status.isEmpty() || System.getenv("CI") != null

        if (tagExit == 0 && tag.isNotEmpty() && clean) {
            tag to true
        } else {
            val revProcess = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(project.rootDir)
                .redirectErrorStream(true)
                .start()
            val rev = revProcess.inputStream.bufferedReader().readText().trim()
            revProcess.waitFor()
            "${rev.ifEmpty { "unknown" }}-SNAPSHOT" to false
        }
    } catch (e: Exception) {
        logger.warn("Failed to get version from git: ${e.message}")
        "v1.18.2_30" to true
    }
}