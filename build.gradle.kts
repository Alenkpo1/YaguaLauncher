// build.gradle.kts

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
    application
    id("org.openjfx.javafxplugin")     version "0.0.14"
    id("com.github.johnrengelman.shadow") version "8.1.1"

}

group = "com.tuusuario"
version = "1.0.8"

repositories {
    mavenCentral()
}

// 1) Toolchain Java 21
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// 2) JavaFX
javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}

// 3) Aplicación
application {
    mainClass.set("ui.MainWindow")
}

// 4) Dependencias
dependencies {
    // JavaFX
    implementation("org.openjfx:javafx-base:21")
    implementation("org.openjfx:javafx-graphics:21")
    implementation("org.openjfx:javafx-controls:21")
    implementation("org.openjfx:javafx-fxml:21")
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("net.java.dev.jna:jna-platform:5.13.0")

    // Jackson (JSON), si lo usas
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.13.4")
    implementation("com.fasterxml.jackson.core:jackson-core:2.13.4")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.13.4.2")

    // Otras dependencias…
}

// 5) Fat-jar con ShadowJar
tasks.withType<ShadowJar> {
    archiveBaseName.set("YaguaLauncher")
    archiveClassifier.set("")   // quita el sufijo “-all”
    archiveVersion.set("")      // quita la versión en el nombre

    from(sourceSets["main"].output)
    configurations = listOf(project.configurations.runtimeClasspath.get())
    manifest {
        attributes["Main-Class"] = "ui.MainWindow"
        "Implementation-Title" to "YaguaLauncher"
        "Implementation-Version" to project.version.toString()
    }
}

tasks.withType<Jar> {
    manifest {
        attributes(
            "Implementation-Title" to "YaguaLauncher",
            "Implementation-Version" to project.version.toString()
        )
    }
}