plugins {
    id("net.neoforged.moddev") version "2.0.147"
    id("neoforge-mutex")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "voice-translate-neoforge"

val requiredJava = JavaVersion.VERSION_25

/** Which voice mod a dev client starts with: -Pvoice=plasmo, or Simple Voice Chat by default. */
val devVoiceMod = (findProperty("voice") as String?) ?: "svc"

repositories {
    mavenCentral()
    maven("https://maven.maxhenkel.de/repository/public") { name = "MaxHenkel" }
    maven("https://repo.plasmoverse.com/releases") { name = "Plasmoverse" }
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
}

dependencies {
    compileOnly("de.maxhenkel.voicechat:voicechat-api:${property("deps.voicechat_api")}")
    compileOnly("maven.modrinth:simple-voice-chat:${property("deps.voicechat")}")
    compileOnly("maven.modrinth:plasmo-voice:${property("deps.plasmovoice")}")

    if (devVoiceMod == "plasmo") {
        runtimeOnly("maven.modrinth:plasmo-voice:${property("deps.plasmovoice")}")
    } else {
        runtimeOnly("maven.modrinth:simple-voice-chat:${property("deps.voicechat")}")
    }
}

neoForge {
    version = property("deps.neo_loader") as String

    mods {
        register("voice_translate") {
            sourceSet(sourceSets.main.get())
        }
    }

    runs {
        register("client") {
            gameDirectory = rootProject.file("run/${sc.current.project}")
            client()
        }
    }
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava
    toolchain.languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
        options.release = requiredJava.majorVersion.toInt()
    }

    processResources {
        // NeoForge 26.3 warns about logoFile on startup; iconFile replaces it
        // there, and older NeoForge does not know the new key.
        val iconKey = if (sc.current.parsed >= "26.3") "iconFile" else "logoFile"

        val props: Map<String, String> = mapOf(
            "id" to project.property("mod.neoforge_id").toString(),
            "name" to project.property("mod.name").toString(),
            "version" to project.property("mod.version").toString(),
            "description" to project.property("mod.description").toString(),
            "minecraft" to project.property("mod.mc_compat").toString(),
            "voicechat" to project.property("deps.voicechat_compat").toString(),
            "plasmovoice" to project.property("deps.plasmovoice_compat").toString(),
            "icon_key" to iconKey
        )
        inputs.properties(props)
        filesMatching("META-INF/neoforge.mods.toml") { expand(props) }
        filesMatching("*.mixins.json") { expand("java" to "JAVA_${requiredJava.majorVersion}") }
        exclude("fabric.mod.json")
    }

    named("createMinecraftArtifacts") {
        dependsOn("stonecutterGenerate")
    }

    jar {
        from(rootProject.file("LICENSE.txt")) {
            rename { "${it}_voice-translate" }
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        dependsOn("build")
        from(jar.flatMap { it.archiveFile }, named<Jar>("sourcesJar").flatMap { it.archiveFile })
        into(rootProject.layout.projectDirectory.dir("dist"))
    }
}
