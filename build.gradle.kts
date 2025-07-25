plugins {
    application
    id("org.openjfx.javafxplugin") version "0.0.13"
}




application {

    mainClass.set("ui.MainWindow")
}

repositories {
    mavenCentral()
}

dependencies {
    // JSON
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    //JavaFX
    implementation("org.openjfx:javafx-controls:21")
    implementation("org.openjfx:javafx-fxml:21")
    //Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    runtimeOnly("ch.qos.logback:logback-classic:1.4.7")
}

javafx {
    version = "21"
    modules = listOf("javafx.controls", "javafx.fxml")
}