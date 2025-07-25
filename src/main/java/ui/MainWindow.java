package ui;

import core.AuthManager;
import core.AssetDownloader;
import core.AssetsManager;
import core.LaunchExecutor;
import core.VersionDetails;
import core.VersionManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.util.*;

/**
 * Ventana principal del launcher:
 *  - Login offline
 *  - Selección de versión (remote + instaladas)
 *  - Descarga de librerías, cliente y assets (solo si no está instalada)
 *  - Configuración de RAM
 *  - Lanzamiento del juego
 */
public class MainWindow extends Application {
    // UI de autenticación
    private TextField usernameField;
    private Button    loginButton;
    private Label     loginStatusLabel;

    // UI de versiones
    private ComboBox<String> versionCombo;
    private Button           downloadButton;

    // UI de progreso y lanzamiento
    private ProgressBar progressBar;
    private Label       statusLabel;
    private TextField   ramField;
    private Button      launchButton;

    // Managers y estado
    private AuthManager         authManager;
    private VersionManager      versionManager;
    private AssetDownloader     assetDownloader;
    private AssetsManager       assetsManager;
    private LaunchExecutor      launchExecutor;
    private AuthManager.Session session;

    // Directorio base ~/.minecraft (o %APPDATA%/.minecraft en Windows)
    private final Path mcBaseDir;

    // Conjunto de versiones ya instaladas localmente
    private final Set<String> installedVersions = new HashSet<>();

    public MainWindow() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            mcBaseDir = Paths.get(appdata != null ? appdata : System.getProperty("user.home"), ".minecraft");
        } else {
            mcBaseDir = Paths.get(System.getProperty("user.home"), ".minecraft");
        }
    }

    @Override
    public void start(Stage stage) {
        // 1) Crear managers
        authManager     = new AuthManager();
        versionManager  = new VersionManager();
        assetDownloader = new AssetDownloader();
        assetsManager   = new AssetsManager(mcBaseDir.resolve("assets"));
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) javaHome = System.getProperty("java.home");
        launchExecutor  = new LaunchExecutor(javaHome);

        // 2) Crear componentes UI
        usernameField    = new TextField(); usernameField.setPromptText("Usuario offline");
        loginButton      = new Button("Login Offline");
        loginStatusLabel = new Label("Por favor, inicia sesión offline.");

        versionCombo   = new ComboBox<>(); versionCombo.setPromptText("Selecciona una versión...");
        versionCombo.setDisable(true);
        downloadButton = new Button("Descargar versión");
        downloadButton.setDisable(true);

        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        progressBar.setVisible(false);
        statusLabel = new Label("");

        ramField     = new TextField("1024"); ramField.setPromptText("RAM (MB)");
        ramField.setDisable(true);
        launchButton = new Button("Lanzar Minecraft");
        launchButton.setDisable(true);

        // 3) Layout
        VBox root = new VBox(10,
                usernameField, loginButton, loginStatusLabel,
                versionCombo, downloadButton,
                progressBar, statusLabel,
                ramField, launchButton
        );
        root.setPadding(new Insets(20));

        stage.setScene(new Scene(root, 450, 380));
        stage.setTitle("MC Yagua Launcher");
        stage.show();

        // 4) Eventos
        loginButton    .setOnAction(e -> performLogin());
        downloadButton .setOnAction(e -> downloadVersionAssets());
        launchButton   .setOnAction(e -> launchGame());
        versionCombo   .getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            onVersionSelected(newV);
        });

        // 5) Intentar cargar sesión previa y escanear versiones instaladas
        loadExistingSession();
        scanInstalledVersions();
    }

    private void loadExistingSession() {
        try {
            AuthManager.Session s = authManager.loadSession();
            if (s != null) {
                session = s;
                onLoginSuccess(s);
            }
        } catch (Exception ex) {
            loginStatusLabel.setText("No hay sesión previa.");
        }
    }

    private void performLogin() {
        String user = usernameField.getText().trim();
        if (user.isEmpty()) {
            loginStatusLabel.setText("Ingresa un nombre de usuario.");
            return;
        }
        session = authManager.loginOffline(user);
        try {
            authManager.saveSession(session);
            onLoginSuccess(session);
        } catch (Exception ex) {
            loginStatusLabel.setText("Error guardando sesión.");
            ex.printStackTrace();
        }
    }

    private void onLoginSuccess(AuthManager.Session s) {
        loginStatusLabel.setText("Sesión: " + s.getUsername() + " (" + s.getUuid() + ")");
        usernameField.setDisable(true);
        loginButton  .setDisable(true);

        versionCombo.setDisable(false);
        loadVersionsAsync();
    }

    /**
     * Escanea ~/.minecraft/versions para saber qué versiones ya están instaladas.
     */
    private void scanInstalledVersions() {
        installedVersions.clear();
        Path versionsDir = mcBaseDir.resolve("versions");
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(versionsDir)) {
            for (Path vdir : ds) {
                if (Files.isDirectory(vdir)) {
                    Path jar = vdir.resolve(vdir.getFileName().toString() + ".jar");
                    if (Files.exists(jar)) {
                        installedVersions.add(vdir.getFileName().toString());
                    }
                }
            }
        } catch (IOException ignored) { }
    }

    private void loadVersionsAsync() {
        downloadButton.setDisable(true);
        Task<List<String>> t = new Task<>() {
            @Override protected List<String> call() throws Exception {
                versionManager.fetchManifest();
                return versionManager.getVersionsIds();
            }
        };
        t.setOnSucceeded(evt -> {
            List<String> ids = t.getValue();
            versionCombo.getItems().setAll(ids);
            statusLabel.setText("Versiones remotas cargadas: " + ids.size());
        });
        t.setOnFailed(evt -> statusLabel.setText("Error cargando versiones"));
        new Thread(t) {{ setDaemon(true); }}.start();
    }

    /**
     * Gestiona UI al seleccionar una versión:
     * - Si está instalada: habilita lanzamiento directo.
     * - Si no: habilita botón de descarga.
     */
    private void onVersionSelected(String ver) {
        if (ver == null) return;
        if (installedVersions.contains(ver)) {
            statusLabel.setText("Versión " + ver + " ya instalada.");
            downloadButton.setDisable(true);
            ramField.setDisable(false);
            launchButton.setDisable(false);
        } else {
            statusLabel.setText("");
            downloadButton.setDisable(false);
            ramField.setDisable(true);
            launchButton.setDisable(true);
        }
    }

    private void downloadVersionAssets() {
        String ver = versionCombo.getValue();
        if (ver == null) {
            statusLabel.setText("Primero elige una versión");
            return;
        }
        ramField.setDisable(false);

        Task<Void> task = new Task<>() {
            @Override protected Void call() throws Exception {
                updateMessage("Descargando detalles de versión...");
                VersionDetails det = versionManager.fetchVersionDetails(ver);

                // 1) Descargar librerías + cliente
                int libs      = det.getLibraries().size();
                int coreTotal = libs + 1;
                int coreDone  = 0;

                for (var lib : det.getLibraries()) {
                    String url  = lib.getDownloads().getArtifact().getUrl();
                    String sha1 = lib.getDownloads().getArtifact().getSha1();
                    Path target = mcBaseDir.resolve("libraries")
                            .resolve(Paths.get(pathFromUrl(url)));
                    updateMessage("Descargando librería: " + target.getFileName());
                    assetDownloader.downloadAndVerify(url, target, sha1);
                    updateProgress(++coreDone, coreTotal);
                }

                var cd         = det.getClientDownload();
                Path clientPath = mcBaseDir.resolve("versions")
                        .resolve(ver)
                        .resolve(ver + ".jar");
                updateMessage("Descargando cliente: " + ver + ".jar");
                assetDownloader.downloadAndVerify(cd.getUrl(), clientPath, cd.getSha1());
                updateProgress(++coreDone, coreTotal);

                // 2) Descargar assets
                updateMessage("Obteniendo asset index...");
                var aiInfo = det.getAssetIndex();
                AssetsManager.AssetIndex ai =
                        assetsManager.fetchAssetIndex(aiInfo.getUrl(), aiInfo.getId());
                Map<String, AssetsManager.AssetObject> objects = ai.objects;

                int assetsTotal  = objects.size();
                int overallTotal = coreTotal + assetsTotal;
                int overallDone  = coreDone;
                int count        = 0;

                for (var entry : objects.entrySet()) {
                    String key  = entry.getKey();
                    String hash = entry.getValue().hash;
                    updateMessage("Descargando asset " + (++count)
                            + " de " + assetsTotal + ": " + key);
                    assetsManager.downloadSingleAsset(key, hash);
                    updateProgress(++overallDone, overallTotal);
                }

                updateMessage("Descarga completa de librerías, cliente y assets.");
                // Marcar como instalada
                installedVersions.add(ver);
                return null;
            }
        };

        progressBar.progressProperty().bind(task.progressProperty());
        progressBar.setVisible(true);
        statusLabel.textProperty().bind(task.messageProperty());

        task.setOnSucceeded(evt -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("¡Listo para lanzar!");
            launchButton.setDisable(false);
        });
        task.setOnFailed(evt -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("Error durante la descarga");
            task.getException().printStackTrace();
        });

        new Thread(task) {{ setDaemon(true); }}.start();
    }

    private void launchGame() {
        String ver = versionCombo.getValue();
        int ram;
        try {
            ram = Integer.parseInt(ramField.getText().trim());
        } catch (NumberFormatException ex) {
            statusLabel.setText("RAM inválida.");
            return;
        }

        new Thread(() -> {
            Platform.runLater(() -> statusLabel.setText("Lanzando juego…"));
            try {
                launchExecutor.launch(session, ver, mcBaseDir.toFile(), ram);
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> statusLabel.setText("Error al lanzar el juego"));
            }
        }).start();
    }

    /** Extrae la ruta de la URL (sin la barra inicial) */
    private String pathFromUrl(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String p = uri.getPath();
        return p.startsWith("/") ? p.substring(1) : p;
    }

    public static void main(String[] args) {
        launch();
    }
}
