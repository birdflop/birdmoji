plugins {
    java
}

allprojects {
    group = "com.birdflop"
    version = "1.1.0"
}

subprojects {
    apply(plugin = "java")

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
        maven("https://nexus.scarsz.me/content/groups/public/")
    }

    dependencies {
        "compileOnly"("io.papermc.paper:paper-api:1.21.10-R0.1-SNAPSHOT")
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release = 21
    }
}

// Each plugin bundles the shared code from common
configure(listOf(project(":discordsrv"), project(":ascension"))) {
    dependencies {
        "compileOnly"(project(":common"))
    }

    tasks.named<Jar>("jar") {
        archiveBaseName = "Birdmoji-" + if (project.name == "discordsrv") "DiscordSRV" else "Ascension"
        from(project(":common").the<SourceSetContainer>()["main"].output)
    }

    tasks.named<ProcessResources>("processResources") {
        filesMatching("plugin.yml") {
            expand("version" to project.version)
        }
    }
}
