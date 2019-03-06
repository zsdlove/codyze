val deployUsername: String? by extra // imported from settings.gradle.kts
val deployPassword: String? by extra // imported from settings.gradle.kts

plugins {
    // built-in
    java
    application
    jacoco
    `maven-publish`
    `java-library`

    id("org.sonarqube") version "2.6"
    id("com.diffplug.gradle.spotless") version "3.18.0"
}

group = "de.fraunhofer.aisec"
version = "1.0-SNAPSHOT"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }

    repositories {
        maven {
            val repoUrl = "http://repository.***REMOVED***"

            val releasesRepoUrl = "$repoUrl/repository/releases"
            val snapshotsRepoUrl = "$repoUrl/repository/snapshots"
            url = uri(if (version.toString().endsWith("SNAPSHOT")) snapshotsRepoUrl else releasesRepoUrl)

            credentials {
                username = deployUsername
                password = deployPassword
            }
        }
    }
}

repositories {
    mavenCentral()

    ivy {
        url = uri("https://download.eclipse.org/tools/cdt/releases/9.6/cdt-9.6.0/plugins")
        patternLayout {
            artifact("/[organisation].[artifact]_[revision].[ext]")
        }
    }

    ivy {
        url = uri("https://ftp.gnome.org/mirror/eclipse.org/oomph/products/repository/plugins/")
        patternLayout {
            artifact("/[organisation].[artifact]_[revision].[ext]")
        }
    }

    maven {
        url = uri("http://repository.***REMOVED***/repository/snapshots/")
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

configurations.all {
    resolutionStrategy {
        cacheChangingModulesFor(0, "seconds")
    }
}

val versions = mapOf(
        "junit5" to "5.3.1",
        "log4j" to "2.11.1",
        "jersey" to "2.28",
        "javaparser" to "3.11.0",
        "commons-lang3" to "3.8.1",
        "jython" to "2.7.1b3",
        "tinkerpop" to "3.3.4"
)

dependencies {
    implementation("org.apache.commons", "commons-lang3", versions["commons-lang3"])
    api("org.apache.logging.log4j", "log4j-slf4j18-impl", versions["log4j"])
    api("org.slf4j", "jul-to-slf4j", "1.8.0-beta2")
    implementation("com.github.javaparser", "javaparser-symbol-solver-core", versions["javaparser"])

    implementation("de.fraunhofer.aisec", "cpg", "1.0-SNAPSHOT")

    // api stuff
    api("org.glassfish.jersey.inject", "jersey-hk2", versions["jersey"])
    api("org.glassfish.jersey.containers", "jersey-container-grizzly2-http", versions["jersey"])
    api("org.glassfish.jersey.media", "jersey-media-json-jackson", versions["jersey"])

    // seriously eclipse...
    api("org.eclipse", "osgi", "3.13.200.v20181130-2106")
    api("org.eclipse.equinox", "common", "3.10.200.v20181021-1645")
    api("org.eclipse.equinox", "preferences", "3.7.200.v20180827-1235")
    api("org.eclipse.core", "runtime", "3.15.100.v20181107-1343")
    api("org.eclipse.core", "jobs", "3.10.200.v20180912-1356")
    api("org.eclipse.cdt", "core", "6.6.0.201812101042")

    api("org.eclipse.lsp4j", "org.eclipse.lsp4j", "0.6.0")

    api("org.apache.tinkerpop", "gremlin-core", versions["tinkerpop"])
    annotationProcessor("org.apache.tinkerpop", "gremlin-core", versions["tinkerpop"])  // Newer Gradle versions require specific classpath for annotatation processors
    api("org.apache.tinkerpop", "gremlin-python", versions["tinkerpop"])
    api("org.apache.tinkerpop", "tinkergraph-gremlin", versions["tinkerpop"])
    api("org.apache.tinkerpop", "gremlin-driver", versions["tinkerpop"])
    api("org.apache.tinkerpop", "neo4j-gremlin", versions["tinkerpop"])  // Neo4j multi-label support for gremlin
    api("com.steelbridgelabs.oss", "neo4j-gremlin-bolt", "0.3.1")  // For fast bolt:// access to Neo4J

    api("org.python", "jython-standalone", versions["jython"])

    // needed for jersey, not part of JDK anymore
    api("javax.xml.bind", "jaxb-api", "2.3.1")

    testImplementation("org.junit.jupiter", "junit-jupiter-api", versions["junit5"])
    testRuntimeOnly("org.junit.jupiter", "junit-jupiter-engine", versions["junit5"])
}

application {
    mainClassName = "de.fraunhofer.aisec.crymlin.Main"
}
tasks.named<Test>("test") {
    useJUnitPlatform()
}

// Persist source files generated by annotation processor to a sane source path so IDEs can use them directly.
sourceSets.configureEach {
    tasks.named<JavaCompile>(compileJavaTaskName) {
        options.annotationProcessorGeneratedSourcesDirectory = file("${projectDir}/src/main/generated/annotationProcessor/java/${this@configureEach.name}")
    }
}

tasks {
    val docker by registering(Exec::class) {
        description = "Builds a docker image based on the Dockerfile."

        dependsOn(build)

        executable = "docker"

        val commit = System.getenv("CI_COMMIT_SHA")

        setArgs(listOf("build",
                "-t", "registry.***REMOVED***/cpganalysisserver/" + project.name + ':' + (commit?.substring(0, 8)
                ?: "latest"),
                '.'))
    }
}

spotless {
    java {
        targetExclude(
                fileTree(project.projectDir) {
                    include("build/generated-src/**")
                }
        )
        googleJavaFormat()
    }
}
