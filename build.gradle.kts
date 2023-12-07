import com.github.gradle.node.npm.task.NpmInstallTask

plugins {
    id("org.openrewrite.build.recipe-library") version "latest.release"
    id("com.github.node-gradle.node") version "latest.release"
}

// Set as appropriate for your organization
group = "org.openrewrite.recipe"
description = "Migrate JavaScript projects using codemods"

val rewriteVersion = rewriteRecipe.rewriteVersion.get()
dependencies {
    implementation(platform("org.openrewrite:rewrite-bom:$rewriteVersion"))

    implementation("org.openrewrite:rewrite-core")

    testImplementation("org.openrewrite:rewrite-test")
}

license {
    exclude("**/package.json")
    exclude("**/package-lock.json")
}

node {
    nodeProjectDir.set(file("build/resources/main/codemods"))
}

tasks.named("npmInstall") {
    dependsOn(tasks.named("processResources"))
}

tasks.named<Jar>("jar") {
    dependsOn(tasks.named("npmInstall"))
}

// We don't care about publishing javadocs anywhere, so don't waste time building them
tasks.withType<Javadoc>().configureEach {
    enabled = false
}

tasks.named<Jar>("sourcesJar") {
    enabled = false
}

tasks.named<Jar>("javadocJar") {
    enabled = false
}

val emptySourceJar = tasks.create<Jar>("emptySourceJar") {
    file("README.md")
    archiveClassifier.set("sources")
}

val emptyJavadocJar = tasks.create<Jar>("emptyJavadocJar") {
    file("README.md")
    archiveClassifier.set("javadoc")
}

publishing {
    publications.named<MavenPublication>("nebula") {
        artifactId = project.name
        description = project.description

        artifacts.clear() // remove the regular JAR
        // Empty JARs are OK: https://central.sonatype.org/publish/requirements/#supply-javadoc-and-sources
        artifact(tasks.named("jar"))
        artifact(emptySourceJar)
        artifact(emptyJavadocJar)

        pom {
            name.set(project.name)
            description.set(project.description)
            url.set("https://moderne.io")
            licenses {
                license {
                    name.set("Creative Commons Attribution-NonCommercial 4.0")
                    url.set("https://creativecommons.org/licenses/by-nc-nd/4.0/deed.en")
                }
            }
            developers {
                developer {
                    name.set("Team Moderne")
                    email.set("support@moderne.io")
                    organization.set("Moderne, Inc.")
                    organizationUrl.set("https://moderne.io")
                }
            }
            scm {
                connection.set("scm:git:git://github.com/moderneinc/rewrite-codemods.git")
                developerConnection.set("scm:git:ssh://github.com:moderneinc/rewrite-codemods.git")
                url.set("https://github.com/moderneinc/rewrite-codemods/tree/main")
            }
        }
    }
}
