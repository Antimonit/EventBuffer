package me.khol.gradle.publish

import org.gradle.api.credentials.PasswordCredentials

plugins {
    id("com.android.library")
    id("maven-publish")
    signing
}

/*
 * For local testing use the :publishToMavenLocal task.
 *  * Artifacts will be created in the ~/.m2/repository directory.
 *
 * For actual release to sonatype use the :publish task.
 *  * You need to have an account at https://s01.oss.sonatype.org/.
 *  *
 * and 'mavenUsername' and 'mavenPassword' gradle properties configured.
 */

val publicationRelease = "release"

android {
    publishing {
        /*
         * Executing `gradlew publishReleasePublicationToMavenLocal` will generate plain,
         * variant-free outputs, such as:
         * * my-lib-1.0.0-javadoc.jar
         * * my-lib-1.0.0-sources.jar
         * * my-lib-1.0.0.aar
         * * my-lib-1.0.0.module
         * * my-lib-1.0.0.pom
         */
        singleVariant(publicationRelease) {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

group = "io.github.antimonit"
// version is automatically taken from the root gradle.properties

signing {
    // If [useInMemoryPgpKeys] is not used, PGP signing information will be read from a keyring.
    // > gpg --list-keys --keyid-format short
    setRequired({
        !version.toString().endsWith("SNAPSHOT") &&
            gradle.taskGraph.hasTask("publish")
    })
    sign(publishing.publications)
}

afterEvaluate {
    publishing {
        publications {
            register<MavenPublication>(publicationRelease) {
                from(components.getByName(publicationRelease))
                // By default, GAV coordinates are configured as follows:
                // groupId = project.group
                // artifactId = project.name
                // version = project.version

                pom {
                    name.set("EventBuffer")
                    description.set("") // TODO
                    inceptionYear.set("2024")
                    url.set("https://github.com/Antimonit/EventBuffer")
                    licenses {
                        license {
                            name.set("The Apache License, Version 2.0")
                            url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("antimonit")
                            name.set("David Khol")
                            url.set("https://github.com/Antimonit/")
                        }
                    }
                    scm {
                        url.set("https://github.com/Antimonit/EventBuffer")
                        connection.set("scm:git:git://github.com/Antimonit/EventBuffer.git")
                        developerConnection.set("scm:git:ssh://git@github.com/Antimonit/EventBuffer.git")
                    }
                }
            }
        }
        repositories {
            maven {
                setUrl("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials(PasswordCredentials::class.java)
            }
        }
    }
}
