package ui;

import core.VersionManager;
import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

/**
 * Ventana principal del launcher con selección de versión utilizando el método getVersionIds().
 */
public class MainWindow extends Application {
    // ComboBox para mostrar IDs de versión (Strings)
    private ComboBox<String> versionCombo;

    private Label statusLabel;

    private VersionManager versionManager;

    @Override
    public void start(Stage stage) {
        // 1) Crear y configurar el ComboBox para versiones
        versionCombo = new ComboBox<>();
        versionCombo.setPromptText("Selecciona una versión...");

        // 2) Crear la etiqueta de estado mientras se cargan versiones
        statusLabel = new Label("Cargando versiones...");

        // 3) Organizar ambos componentes en un layout vertical
        VBox root = new VBox(10, versionCombo, statusLabel);
        root.setPadding(new Insets(20)); // Padding de 20px alrededor

        // 4) Configurar la escena y mostrar la ventana
        Scene scene = new Scene(root, 400, 150);
        stage.setTitle("MC Yagua Launcher");
        stage.setScene(scene);
        stage.show();

        // 5) Iniciar la carga de versiones de forma asíncrona
        loadVersionsAsync();
    }

    /**
     * Carga el manifiesto de versiones y actualiza el ComboBox con IDs.
     * Utiliza un Task para no bloquear la interfaz.
     */
    private void loadVersionsAsync() {

        versionManager = new VersionManager();

        // Definimos un Task genérico que retorna lista de IDs
        Task<List<String>> task = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                // Descargar y parsear el manifiesto JSON
                versionManager.fetchManifest();
                // Retornar directamente los IDs
                return versionManager.getVersionsIds();
            }
        };

        // Handler cuando la tarea finaliza correctamente
        task.setOnSucceeded(event -> {
            List<String> versiones = task.getValue();              // Obtener resultado
            versionCombo.getItems().setAll(versiones);              // Poblar ComboBox
            statusLabel.setText("Versiones cargadas: " + versiones.size()); // Actualizar estado
        });

        // Handler en caso de que ocurra un error
        task.setOnFailed(event -> {
            statusLabel.setText("Error al cargar versiones");        // Mostrar mensaje de error
            task.getException().printStackTrace();                    // Log de excepción
        });

        // Ejecutar la tarea en un hilo daemon para no bloquear la UI
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
    }

    public static void main(String[] args) {

        launch();
    }
}
