package ui;

import core.AssetDownloader;
import core.VersionDetails;
import core.VersionManager;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Ventana principal del launcher con selección de versión y descarga de librerías/assets.
 */
public class MainWindow extends Application {
    private ComboBox<String> versionCombo;    // Desplegable de IDs de versión
    private Button downloadButton;            // Botón para iniciar descarga
    private ProgressBar progressBar;          // Barra de progreso de descarga
    private Label statusLabel;                // Etiqueta de estado/mensajes

    private VersionManager versionManager;
    private AssetDownloader assetDownloader;  // Campo para usar el downloader en toda la clase

    // Directorio base de Minecraft (en la carpeta del usuario)
    private final Path mcBaseDir = Paths.get(System.getProperty("user.home"), ".minecraft");

    @Override
    public void start(Stage stage) {
        // 1) Crear y configurar el ComboBox para versiones
        versionCombo = new ComboBox<>();
        versionCombo.setPromptText("Selecciona una versión...");

        // 2) Crear botón de descarga y deshabilitarlo hasta seleccionar una versión
        downloadButton = new Button("Descargar");
        downloadButton.disableProperty().bind(
                versionCombo.getSelectionModel().selectedItemProperty().isNull()
        );

        // 3) Crear barra de progreso (invisible al inicio)
        progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        progressBar.setVisible(false);

        // 4) Crear etiqueta de estado
        statusLabel = new Label("Cargando versiones...");

        // 5) Layout vertical con padding
        VBox root = new VBox(10, versionCombo, downloadButton, progressBar, statusLabel);
        root.setPadding(new Insets(20));

        // 6) Configurar y mostrar la escena
        Scene scene = new Scene(root, 400, 200);
        stage.setTitle("MC Yagua Launcher");
        stage.setScene(scene);
        stage.show();

        // 7) Inicializar gestores de versiones y downloader
        versionManager = new VersionManager();
        assetDownloader = new AssetDownloader(); // Asignar al campo

        // 8) Cargar versiones en segundo plano y luego habilitar descarga
        loadVersionsAsync();

        // 9) Acción del botón para descargar la versión seleccionada
        downloadButton.setOnAction(e -> downloadVersionAssets());
    }

    /**
     * Carga el manifiesto de versiones y actualiza el ComboBox con IDs.
     * Se ejecuta en un Task para no bloquear la UI.
     */
    private void loadVersionsAsync() {
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                versionManager.fetchManifest();
                return versionManager.getVersionsIds();  // Nombre correcto del método
            }
        };

        task.setOnSucceeded(evt -> {
            List<String> ids = task.getValue();
            versionCombo.getItems().setAll(ids);
            statusLabel.setText("Versiones cargadas: " + ids.size());
        });
        task.setOnFailed(evt -> statusLabel.setText("Error al cargar versiones"));

        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Inicia la descarga de librerías y cliente para la versión seleccionada.
     */
    private void downloadVersionAssets() {
        String selected = versionCombo.getValue();
        if (selected == null) {
            statusLabel.setText("Primero elige una versión");
            return;
        }

        Task<Void> downloadTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                updateMessage("Obteniendo detalles de versión...");
                VersionDetails details = versionManager.fetchVersionDetails(selected);

                int total = details.getLibraries().size() + 1;
                int count = 0;

                // Descargar cada librería y verificar SHA-1
                for (VersionDetails.Library lib : details.getLibraries()) {
                    String url = lib.getDownloads().getArtifact().getUrl();
                    String sha1 = lib.getDownloads().getArtifact().getSha1();

                    Path target = mcBaseDir.resolve("libraries")
                            .resolve(Paths.get(pathFromUrl(url)));

                    updateMessage("Descargando librería: " + target.getFileName());
                    assetDownloader.downloadAndVerify(url, target, sha1);

                    count++;
                    updateProgress(count, total);
                }

                // Descargar el client.jar
                VersionDetails.ClientDownload client = details.getClientDownload();
                Path clientTarget = mcBaseDir.resolve("versions")
                        .resolve(selected)
                        .resolve(selected + ".jar");

                updateMessage("Descargando cliente: " + clientTarget.getFileName());
                assetDownloader.downloadAndVerify(client.getUrl(), clientTarget, client.getSha1());
                count++;
                updateProgress(count, total);

                updateMessage("Descarga completada");
                return null;
            }
        };

        // Enlazar barra de progreso y etiqueta a la tarea
        progressBar.progressProperty().bind(downloadTask.progressProperty());
        progressBar.setVisible(true);
        statusLabel.textProperty().bind(downloadTask.messageProperty());

        downloadTask.setOnSucceeded(evt -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("Todos los archivos descargados correctamente");
        });
        downloadTask.setOnFailed(evt -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("Error durante la descarga");
            downloadTask.getException().printStackTrace();
        });

        Thread thread = new Thread(downloadTask);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Extrae la ruta del recurso desde su URL, p.ej.:
     * https://.../com/mojang/xyz/1.0/xyz-1.0.jar -> com/mojang/xyz/1.0/xyz-1.0.jar
     */
    private String pathFromUrl(String url) {
        return url.substring(url.indexOf("/com/"));
    }

    public static void main(String[] args) {
        launch();
    }
}
