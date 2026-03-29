# YaguaLauncher

> Unofficial, lightweight and open-source Minecraft launcher built with **Java 21 + JavaFX 21**.

---

## Features

- **Offline authentication** — generates a local session with a consistent UUID, no Microsoft account required.
- **Version management** — lists and resolves Minecraft versions from the official Mojang manifest, with local fallback support for OptiFine, Forge, etc.
- **Automatic asset downloading** — downloads libraries, client JARs and assets on launch.
- **Player profiles** — create and save multiple launch profiles with a custom version and RAM allocation, persisted in `profiles.json`.
- **OptiFine / LaunchWrapper support** — detects tweakers and adjusts the main class automatically.
- **JavaFX UI** — graphical interface built with FXML and custom CSS.
- **Windows EXE distribution** — packaged with `jpackage` and an embedded JRE.

---

## Requirements

| Tool | Minimum version |
|---|---|
| Java (JDK) | 21 |
| JavaFX SDK | 21 |
| Gradle | included (`./gradlew`) |
| WiX Toolset *(EXE only)* | 3.x |

---

## Build & Run

### 1. Clone the repository

```bash
git clone https://github.com/your-username/YaguaLauncher.git
cd YaguaLauncher
```

### 2. Run in development mode

```bash
./gradlew run
```

### 3. Generate the executable JAR

```bash
./gradlew clean shadowJar
```

The JAR is generated at `build/libs/YaguaLauncher.jar`.

---

## Package as a Windows `.exe` installer

### Step 1 — Create a custom JRE

```powershell
jlink `
  --module-path "$env:JAVA_HOME\jmods" `
  --add-modules java.base,java.desktop,java.logging,java.xml,javafx.controls,javafx.fxml,java.net.http,jdk.crypto.ec,jdk.zipfs `
  --strip-debug `
  --no-man-pages `
  --no-header-files `
  --compress=2 `
  --output jre-custom
```

### Step 2 — Package with jpackage

```powershell
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
```

---

## Project Structure

```
YaguaLauncher/
├── src/
│   └── main/
│       ├── java/
│       │   ├── core/
│       │   │   ├── AuthManager.java       # Offline session with UUID
│       │   │   ├── ProfileManager.java    # Launch profiles
│       │   │   ├── VersionManager.java    # Manifest & version resolution
│       │   │   ├── VersionDetails.java    # Version metadata model
│       │   │   ├── AssetsManager.java     # Asset management
│       │   │   ├── AssetDownloader.java   # Asset downloading
│       │   │   └── LaunchExecutor.java    # Process builder & executor
│       │   └── ui/
│       │       └── MainWindow.java        # Main JavaFX controller
│       └── resources/
│           └── ui/
│               ├── styles.css             # UI stylesheet
│               └── icon.ico              # Application icon
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Main Dependencies

| Library | Purpose |
|---|---|
| `org.openjfx:javafx-*:21` | Graphical interface |
| `net.java.dev.jna:jna:5.13.0` | Native interoperability |
| `com.fasterxml.jackson.*:2.13.x` | JSON serialization |
| `com.github.johnrengelman.shadow` | Fat-JAR with bundled dependencies |

---

## License

This project is for personal/educational use. It is not affiliated with or endorsed by Mojang Studios or Microsoft.

---

