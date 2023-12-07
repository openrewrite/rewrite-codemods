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

afterEvaluate {
    // Disable the sourcesJar and javadocJar from being published. The `plugin-publish-plugin` adds these in an
    // `afterEvaluate` block, so we need to exclude them in one after that
    val javaComponent = components["java"] as AdhocComponentWithVariants
    javaComponent.withVariantsFromConfiguration(configurations["sourcesElements"]) {
        skip()
    }
    javaComponent.withVariantsFromConfiguration(configurations["javadocElements"]) {
        skip()
    }
}

// We don't care about publishing javadocs anywhere, so don't waste time building them
tasks.withType<Javadoc>().configureEach {
    enabled = false
}

node {
    nodeProjectDir.set(file("src/main/resources/codemods"))
}
