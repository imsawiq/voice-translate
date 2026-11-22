plugins {
    id("dev.kikugie.loom-back-compat")
}

version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = "voice-translate-fabric"

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
    minecraft("com.mojang:minecraft:${sc.current.version}")
    loomx.applyMojangMappings()

    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    modImplementation("net.fabricmc.fabric-api:fabric-api:${property("deps.fabric_api")}")

    // Both voice mods are optional: the jar works with whichever one the
    // player has, so they are compiled against and never bundled.
    compileOnly("de.maxhenkel.voicechat:voicechat-api:${property("deps.voicechat_api")}")
    modCompileOnly("maven.modrinth:simple-voice-chat:${property("deps.voicechat")}")
    modCompileOnly("maven.modrinth:plasmo-voice:${property("deps.plasmovoice")}")

    if (devVoiceMod == "plasmo") {
        modRuntimeOnly("maven.modrinth:plasmo-voice:${property("deps.plasmovoice")}")
    } else {
        modRuntimeOnly("maven.modrinth:simple-voice-chat:${property("deps.voicechat")}")
    }
}

loom {
    fabricModJsonPath = rootProject.file("src/main/resources/fabric.mod.json")

    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run/${sc.current.project}")
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
        val props: Map<String, String> = mapOf(
            "id" to project.property("mod.id").toString(),
            "name" to project.property("mod.name").toString(),
            "version" to project.property("mod.version").toString(),
            "description" to project.property("mod.description").toString(),
            "minecraft" to project.property("mod.mc_compat").toString(),
            "voicechat" to project.property("deps.voicechat_compat").toString(),
            "plasmovoice" to project.property("deps.plasmovoice_compat").toString(),
            "fabric_loader" to project.property("deps.fabric_loader").toString()
        )
        inputs.properties(props)
        filesMatching("fabric.mod.json") { expand(props) }
        filesMatching("*.mixins.json") { expand("java" to "JAVA_${requiredJava.majorVersion}") }
        exclude("META-INF/neoforge.mods.toml")
    }

    jar {
        from(rootProject.file("LICENSE.txt")) {
            rename { "${it}_voice-translate" }
        }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        dependsOn("build")
        from(loomx.modJar.flatMap { it.archiveFile }, loomx.modSourcesJar.flatMap { it.archiveFile })
        into(rootProject.layout.projectDirectory.dir("dist"))
    }
}
