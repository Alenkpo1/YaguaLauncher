package ui;

import core.VersionManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Ventana principal del launcher, ahora con selección de versión.
 */
public class MainWindow extends Application {
    private ComboBox<String> versionCombo;    // Lista desplegable para versiones
    private Label statusLabel;                // Etiqueta para mostrar estado de carga
    private VersionManager versionManager;    // Gestor de versiones de Minecraft

    @Override
    public void start(Stage stage) {
        // Inicializamos componentes básicos de la UI
        versionCombo = new ComboBox<>();
        versionCombo.setPromptText("Elegí una versión...");

        statusLabel = new Label("Cargando versiones...");

        VBox root = new VBox(10, versionCombo, statusLabel);
        root.setPadding(new javafx.geometry.Insets(20));

        // Configuramos y mostramos la escena
        Scene scene = new Scene(root, 400, 150);
        stage.setTitle("MC Yagua Launcher");
        stage.setScene(scene);
        stage.show();

        // Arrancamos la carga de versiones en segundo plano
        loadVersionsAsync();
    }

    /**
     * Carga el manifiesto de versiones y actualiza el ComboBox
     * Se ejecuta Hilo en paralelo para no bloquear la UI
     */

    private void loadVersionsAsync() {
        versionManager = new VersionManager();

        // Creamos una tarea que retorna la lista de IDs de versión
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                // Descarga y parseo del manifiesto JSON
                versionManager.fetchManifest();
                // Extraemos solo los IDs de cada versión
                return versionManager.listVersions()
                        .stream()
                        .map(v -> v.getId())
                        .collect(Collectors.toList());
            }
        };
    }


    public static void main(String[] args) {
        launch();
    }
}