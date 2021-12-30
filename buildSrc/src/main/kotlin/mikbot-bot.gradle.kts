import java.nio.file.Files

plugins {
    application
}

group = "dev.schlaubi"
version = "2.0.1"

application {
    mainClass.set("dev.schlaubi.mikbot.tester.LauncherKt")
}

dependencies {
    if(name != "test-bot") {
        implementation(project(":test-bot"))
    }
}

tasks {
    val pluginsTxt = file("plugins.txt").toPath()
    val plugins = if (Files.exists(pluginsTxt)) {
        Files.readAllLines(pluginsTxt)
            .asSequence()
            .filterNot { it.startsWith("#") }
            .filterNot { it.isBlank() }
            .toList()
    } else {
        emptyList()
    }
    val pluginsDirectory = buildDir.resolve("installed-plugins")

    val deleteObsoletePlugins = register<Delete>("deleteObsoletePlugins") {
        delete(pluginsDirectory.absolutePath + "/*")
    }

    val installPlugins = register<Copy>("installPlugins") {
        dependsOn(deleteObsoletePlugins)

        outputs.dir(pluginsDirectory)

        plugins.forEach {
            val task = project(it).tasks.getByName("assemblePlugin") as Jar

            dependsOn(task)

            from(task.destinationDirectory)
            include(task.archiveFile.get().asFile.name)
            into(pluginsDirectory)
        }
    }

    val exportProjectPath = register("exportProjectPath") {
        val output = buildDir.resolve("resources").resolve("main").resolve("bot-project-path.txt")
        outputs.file(output)

        doFirst {
            val path = output.toPath()
            Files.writeString(path, pluginsDirectory.absolutePath)
        }
    }

    classes {
        dependsOn(exportProjectPath, installPlugins)
    }
}
