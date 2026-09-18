// ==============================================================================
// thz-lsp-jvm — LSP Server do THZ-LANG (LSP4J)
//
// Servidor LSP via stdio, alimentado diretamente pelo thz-core-jvm.
// Substitui o servidor LSP Node.js (vscode-languageserver).
// ==============================================================================

plugins {
    java
    application
    id("com.gradleup.shadow") version "9.6.1"
}

val repoRoot = rootProject.projectDir.resolve("../thz-lang/")
val versionFile = if (file("version.txt").exists()) file("version.txt") else repoRoot.resolve("version.txt")
val thzVersion = if (versionFile.exists()) versionFile.readText().trim() else "0.4.0"

group = "thz.lang"
version = thzVersion

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

dependencies {
    implementation("thz.lang:thz-core:$thzVersion")

    // LSP4J — implementação Java do Language Server Protocol
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.21.1")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.21.1")

    // Gson (usado pelo LSP4J para serialização JSON)
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("org.junit.jupiter:junit-jupiter:5.11.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("thz.lang.lsp.ThzLanguageServer")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-processing"))
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

tasks.register<Copy>("instalarLspJar") {
    group = "build"
    description = "Copia o JAR do LSP para target/ na raiz do workspace"
    from(tasks.shadowJar.flatMap { it.archiveFile })
    into(rootProject.projectDir.resolve("../thz-lang/target"))
    rename { "thz-lsp.jar" }
}

tasks.shadowJar {
    archiveBaseName.set("thz-lsp")
    archiveClassifier.set("")
    archiveVersion.set(thzVersion)

    finalizedBy("instalarLspJar")
}

