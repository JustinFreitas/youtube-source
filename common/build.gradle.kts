import com.vanniktech.maven.publish.JavaLibrary
import com.vanniktech.maven.publish.JavadocJar
import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    `java-library`
    groovy
    alias(libs.plugins.maven.publish.base)
}

base {
    archivesName = "youtube-common"
}

dependencies {
    // Compile against the HC5 lavaplayer fork (v2) so the shared interfaces (HttpContextFilter,
    // HttpInterface) resolve to their HttpClient-5 signatures.
    compileOnly(libs.lavaplayer.v2)
    compileOnly(libs.httpclient5)
    compileOnly(libs.httpcore5)

    implementation(libs.rhino.engine)
    implementation(libs.nanojson)
    compileOnly(libs.slf4j)
    compileOnly(libs.annotations)

    testImplementation(libs.lavaplayer.v2)
    testImplementation(libs.httpclient5)
    testImplementation(libs.httpcore5)
    testImplementation("org.apache.logging.log4j:log4j-core:2.26.1")
    testImplementation("org.apache.logging.log4j:log4j-slf4j2-impl:2.26.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:6.1.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:6.1.1")
    testRuntimeOnly(libs.junit.platform.launcher)

    // Spock
    testImplementation(libs.groovy)
    testImplementation(libs.spock.core)
    testImplementation(libs.byte.buddy)
    testImplementation(libs.byte.buddy.agent)
    testImplementation(libs.objenesis)
}

mavenPublishing {
    configure(JavaLibrary(JavadocJar.Javadoc()))
}

tasks {
    processResources {
        filter<ReplaceTokens>(
            "tokens" to mapOf(
                "version" to project.version
            )
        )
    }
    test {
        useJUnitPlatform() // Enable JUnit Platform for running JUnit 5 tests
    }
}
