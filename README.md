## YaguaLauncher
---

**Como ejecutar:**

* **Crear el JAR**
./gradlew clean shadowJar

----
* **Imagen**
jlink `
  --module-path "$env:JAVA_HOME\jmods" `
  --add-modules java.base,java.desktop,java.logging,java.xml,javafx.controls,javafx.fxml,java.net.http,jdk.crypto.ec,jdk.zipfs `
  --strip-debug `
  --no-man-pages `
  --no-header-files `
  --compress=2 `
  --output jre-custom

-----

* **Empaquetar EXE**
jpackage `
   --name "YaguaLauncher" `
   --input "build\libs" `
   --main-jar "YaguaLauncher.jar" `
   --main-class "ui.MainWindow" `
   --type exe `
   --icon "src\main\resources\ui\icon.ico" `
   --win-shortcut `
   --win-menu `
   --app-version "1.0.9" `
   --win-per-user-install `
   --win-dir-chooser `
   --runtime-image "jre-custom"
