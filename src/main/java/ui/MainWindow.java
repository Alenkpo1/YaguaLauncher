package ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class MainWindow extends Application {
    @Override
    public void start(Stage stage) {
        Label lbl = new Label("Yagua Minecraft launcher listo!");
        Scene scene = new Scene(lbl, 400, 200);
        stage.setTitle("Yagua Launcher");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}