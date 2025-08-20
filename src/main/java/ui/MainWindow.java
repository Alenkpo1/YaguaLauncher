package ui;


import core.AuthManager;
import core.AssetDownloader;
import core.AssetsManager;
import core.AssetsManager.AssetIndex;
import core.AssetsManager.AssetObject;
import core.LaunchExecutor;
import core.ProfileManager;
import core.ProfileManager.Profile;
import core.VersionDetails;
import core.VersionManager;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.scene.Cursor;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;

import javafx.util.Duration;

import java.awt.*;
import java.io.*;
import java.net.*;


import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;


import java.io.File;
import java.nio.file.Path;


public class MainWindow extends Application {

    private final Path mcBaseDir = computeMcBaseDir();

    private final String SERVER_HOST = "26.68.85.246";
    private final int SERVER_PORT = 25565;
    private final String SERVER_NAME = "Server AlenyToti";
    // — Managers
    private AuthManager authManager;
    private ProfileManager profileManager;
    private VersionManager versionManager;
    private AssetDownloader assetDownloader;
    private AssetsManager assetsManager;
    private LaunchExecutor launchExecutor;

    // — Estado de sesión y versiones
    private AuthManager.Session session;
    private List<String> allRemoteVersions = Collections.emptyList();
    private final Set<String> installedVersions = new HashSet<>();

    // — Escenas
    private Scene loginScene, mainScene;

    private VBox thumbsColumn;
    private ImageView preview;
    private ObjectProperty<Path> current;

    // — Login UI
    private TextField usernameField;
    private Button loginButton;
    private Label loginStatusLabel;

    private WatchService screenshotWatcher;
    private Thread screenshotWatcherThread;

    private VBox screenshotsPane;

    // — Navegación lateral
    private ToggleButton navHome, navProfiles, navVersions, navLaunch;
    private ToggleButton navConsole;
    private ToggleButton navScreenshots;

    // — Panes de contenido
    private VBox homePane, profilesPane, versionsPane, launchPane;
    private VBox consolePane;

    private Button bigPlay;

    private final Set<Path> currentScreenshots = new HashSet<>();

    // — Controles sección Perfiles
    private ComboBox<String> profileCombo;
    private Button newProfileBtn, saveProfileBtn, deleteProfileBtn;
    private Label serverNameLabel;
    private Circle serverStatusCircle;
    // — Controles sección Versiones
    private CheckBox showSnapshotsCheckBox;
    private ComboBox<String> versionCombo;
    private Button downloadButton;
    private ProgressBar progressBar;
    private Label statusLabel;

    private TextArea consoleTextArea;
    // Controles sección Lanzamiento
    private TextField ramField;
    private Button launchButton;
    private Label serverLabel;
    private Label pingLabel;

    private Button openDirButton;
    private Button updateButton;

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) throws URISyntaxException {

        System.out.println("Path final del .exe: " + getExePath());
        try {
            Path iconoTemp = extraerRecursoComoArchivoTemporal("/ui/icon.ico", "icono");

            ShortcutCreator.crearAccesoDirecto(
                    "YaguaLauncher",
                    Path.of(getExePath()),
                    iconoTemp
            );
        } catch (Exception e) {
            e.printStackTrace();
        }


        String fontPath = "/ui/fonts/Rubik-Bold.ttf";
        InputStream fontStream = getClass().getResourceAsStream(fontPath);
        if (fontStream == null) {
            System.err.println("¡No hallo la fuente en: " + fontPath + "!");
        } else {
            Font loadedFont = Font.loadFont(fontStream, 12);
            System.out.println("Cargada familia: " + loadedFont.getFamily());
        }

        stage.initStyle(StageStyle.UNDECORATED);

        var fontUrl = getClass().getResource("/ui/fonts/Rubik-Bold.ttf");
        if (fontUrl != null) Font.loadFont(fontUrl.toExternalForm(), 12);

        // Inicializa managers
        authManager = new AuthManager();
        profileManager = new ProfileManager(mcBaseDir);
        versionManager = new VersionManager(mcBaseDir);
        assetDownloader = new AssetDownloader();
        assetsManager = new AssetsManager(mcBaseDir.resolve("assets"));
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) javaHome = System.getProperty("java.home");
        launchExecutor = new LaunchExecutor(javaHome, versionManager);

        // Construye escenas
        loginScene = buildLoginScene(stage);
        mainScene = buildMainScene(stage);

        stage.setWidth(1024);
        stage.setHeight(600);
        // Muestra login
        stage.setScene(loginScene);
        stage.setTitle("YaguaLauncher");
        stage.setWidth(854);
        stage.setHeight(500);
        stage.centerOnScreen();
        stage.setResizable(true);
        stage.show();
        maybeShowUpdatedToast();
    }

    public static class Server {
        private final String name;
        private final String address;
        public final SimpleStringProperty latency = new SimpleStringProperty("…");

        public Server(String name, String address) {
            this.name = name;
            this.address = address;
        }


    }

    private ObservableList<Server> loadServers() {

        return FXCollections.observableArrayList(
                new Server("Servidor A", "mc.hypixel.net")
        );
    }

    private void buildConsolePane() {
        consoleTextArea = new TextArea();
        consoleTextArea.setEditable(false);
        consoleTextArea.getStyleClass().add("console-text-area");

        consoleTextArea.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        ScrollPane sp = new ScrollPane(consoleTextArea);
        sp.getStyleClass().add("console-scroll-pane");
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);

        sp.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(sp, Priority.ALWAYS);

        consolePane = new VBox(sp);
        consolePane.getStyleClass().add("console-pane");
        consolePane.setPadding(new Insets(10));

        consolePane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(consolePane, Priority.ALWAYS);
    }

    private void pingServer() {
        new Thread(() -> {
            try (var socket = new Socket()) {
                long start = System.currentTimeMillis();
                socket.connect(new InetSocketAddress(SERVER_HOST, SERVER_PORT), 1000);
                long ms = System.currentTimeMillis() - start;
                Platform.runLater(() -> {
                    pingLabel.setText(ms + " ms");
                    serverStatusCircle.setFill(Color.LIMEGREEN);
                });
            } catch (IOException ex) {
                Platform.runLater(() -> {
                    pingLabel.setText("offline");
                    serverStatusCircle.setFill(Color.RED);
                });
            }
        }, "Ping-Server").start();
    }

    private Scene buildLoginScene(Stage stage) {
        // Preferencias para recordar el último usuario
        java.util.prefs.Preferences prefs =
                java.util.prefs.Preferences.userNodeForPackage(MainWindow.class);

        usernameField = new TextField();
        usernameField.setPromptText("Usuario");
        usernameField.getStyleClass().add("login-field");

        // Prefill con el último usuario
        String lastUser = prefs.get("lastUsername", "");
        if (!lastUser.isBlank()) {
            usernameField.setText(lastUser);
        }

        loginButton = new Button("Login");
        loginButton.getStyleClass().add("login-button");
        loginButton.setDefaultButton(true);

        loginStatusLabel = new Label();
        loginStatusLabel.getStyleClass().add("login-status");
        loginStatusLabel.setStyle("-fx-text-fill: tomato;");

        // Acción de login
        Runnable doLogin = () -> {
            String user = (usernameField.getText() == null) ? "" : usernameField.getText().trim();
            if (user.isEmpty()) {
                loginStatusLabel.setText("Ingresa un nombre de usuario.");
                return;
            }

            // Login offline
            session = authManager.loginOffline(user);
            try {
                authManager.saveSession(session);
            } catch (IOException ex) {
                ex.printStackTrace();
            }

            // Guardar último usuario
            prefs.put("lastUsername", user);

            // Cambiar a la escena principal
            Stage st = (Stage) loginButton.getScene().getWindow();
            if (mainScene == null) {
                mainScene = buildMainScene(st);
            }
            st.setScene(mainScene);


            st.setHeight(520);
            st.centerOnScreen();

            postLoginInit();
        };

        // Click y Enter
        loginButton.setOnAction(e -> doLogin.run());
        usernameField.setOnAction(e -> doLogin.run());

        // Layout del login
        VBox overlay = new VBox(15,
                createTitleLabel("YaguaLauncher"),
                usernameField,
                loginButton,
                loginStatusLabel
        );
        overlay.getStyleClass().add("login-overlay");
        overlay.setPadding(new Insets(30));
        overlay.setMaxWidth(320);
        overlay.setAlignment(Pos.CENTER_LEFT);

        loginButton.setMaxWidth(Double.MAX_VALUE);

        // Fondo
        InputStream bgStream = getClass().getResourceAsStream("/ui/images/login_bg.jpg");
        ImageView bgView = (bgStream != null)
                ? new ImageView(new Image(bgStream))
                : new ImageView();
        bgView.setPreserveRatio(false);

        // Root
        StackPane root = new StackPane(bgView, overlay);
        bgView.fitWidthProperty().bind(root.widthProperty());
        bgView.fitHeightProperty().bind(root.heightProperty());
        StackPane.setAlignment(overlay, Pos.CENTER_LEFT);
        StackPane.setMargin(overlay, new Insets(0, 0, 0, 50));

        Scene scene = new Scene(root, 1024, 520);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        return scene;
    }

    private final java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(MainWindow.class);
    private double xOffset, yOffset;

    private Scene buildMainScene(Stage stage) {

        stage.initStyle(StageStyle.UNDECORATED);


        navHome = makeNavButton("/ui/icons/home.png");
        navProfiles = makeNavButton("/ui/icons/user.png");
        navVersions = makeNavButton("/ui/icons/versions.png");
        navLaunch = makeNavButton("/ui/icons/play.png");
        navConsole = makeNavButton("/ui/icons/console.png");
        navScreenshots = makeNavButton("/ui/icons/screenshots.png");
        ToggleGroup navGroup = new ToggleGroup();
        for (var tb : List.of(navHome, navProfiles, navVersions, navLaunch, navConsole, navScreenshots)) {
            tb.setToggleGroup(navGroup);
            tb.getStyleClass().add("nav-button");
        }
        navHome.setSelected(true);

        VBox navBar = new VBox(20, navHome, navProfiles, navVersions, navLaunch, navConsole, navScreenshots);
        navBar.setPadding(new Insets(20));
        navBar.getStyleClass().add("nav-bar");


        buildHomePane();
        buildProfilesPane();
        buildVersionsPane();
        buildLaunchPane();
        buildConsolePane();
        buildScreenshotsPane();
        StackPane content = new StackPane(homePane, profilesPane, versionsPane, launchPane, consolePane, screenshotsPane);
        showOnly(homePane);
        navHome.setOnAction(e -> showOnly(homePane));
        navProfiles.setOnAction(e -> showOnly(profilesPane));
        navVersions.setOnAction(e -> showOnly(versionsPane));
        navLaunch.setOnAction(e -> showOnly(launchPane));
        navConsole.setOnAction(e -> showOnly(consolePane));
        navScreenshots.setOnAction(e -> showOnly(screenshotsPane));



        serverLabel = new Label(SERVER_NAME);
        serverLabel.getStyleClass().add("server-name");
        serverLabel.setCursor(Cursor.DEFAULT);

        pingLabel = new Label("…");
        pingLabel.getStyleClass().add("server-ping");

        serverStatusCircle = new Circle(6);
        serverStatusCircle.getStyleClass().add("server-circle");
        serverStatusCircle.setStroke(Color.WHITE);
        serverStatusCircle.setStrokeWidth(1);
        serverStatusCircle.setFill(Color.GRAY);

        HBox serverBox = new HBox(5, serverStatusCircle, serverLabel, pingLabel);
        serverBox.getStyleClass().add("server-status-box");
        serverBox.setAlignment(Pos.CENTER_RIGHT);
        serverBox.setPadding(new Insets(5, 15, 5, 15));


        BorderPane mainPane = new BorderPane();
        mainPane.getStyleClass().add("content-pane");
        mainPane.setLeft(navBar);
        navBar.prefHeightProperty().bind(mainPane.heightProperty());
        mainPane.setCenter(content);
        mainPane.setTop(serverBox);


        Label titleLabel = new Label("YaguaLauncher");
        titleLabel.getStyleClass().add("window-title");

        Button btnMin = new Button("–");
        btnMin.getStyleClass().add("window-button");
        btnMin.setOnAction(e -> stage.setIconified(true));

        Button btnMax = new Button("▢");
        btnMax.getStyleClass().addAll("window-button", "window-button-disabled");
        btnMax.setDisable(true);
        btnMax.setOnAction(null);

        Button btnClose = new Button("✕");
        btnClose.getStyleClass().addAll("window-button", "window-close");
        btnClose.setOnAction(e -> stage.close());

        HBox windowControls = new HBox(5, btnMin, btnClose);
        windowControls.setAlignment(Pos.CENTER_RIGHT);

        Region dragSpacer = new Region();
        HBox.setHgrow(dragSpacer, Priority.ALWAYS);

        HBox titleBar = new HBox(15, titleLabel, dragSpacer, windowControls);
        titleBar.getStyleClass().add("window-title-bar");
        titleBar.setPadding(new Insets(5, 10, 5, 10));

        titleBar.setOnMousePressed(ev -> {
            xOffset = ev.getSceneX();
            yOffset = ev.getSceneY();
        });
        titleBar.setOnMouseDragged(ev -> {
            if (!stage.isMaximized()) {
                stage.setX(ev.getScreenX() - xOffset);
                stage.setY(ev.getScreenY() - yOffset);
            }
        });


        VBox root = new VBox(titleBar, mainPane);
        root.getStyleClass().add("root");
        VBox.setVgrow(mainPane, Priority.ALWAYS);


        Scene scene = new Scene(root, 1024, 600);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());


        Timeline pingTimer = new Timeline(
                new KeyFrame(Duration.ZERO,
                        e -> pingServer()),
                new KeyFrame(Duration.seconds(5))
        );
        pingTimer.setCycleCount(Timeline.INDEFINITE);
        pingTimer.play();

        LauncherPrefs prefs = loadPrefs();
        applyPrefsToUI(prefs);

        if (profileCombo != null)
            profileCombo.valueProperty().addListener((obs,o,n) -> savePrefs(collectPrefsFromUI()));
        if (versionCombo != null)
            versionCombo.valueProperty().addListener((obs,o,n) -> savePrefs(collectPrefsFromUI()));
        if (showSnapshotsCheckBox != null)
            showSnapshotsCheckBox.selectedProperty().addListener((obs,o,n) -> savePrefs(collectPrefsFromUI()));
        if (ramField != null)
            ramField.textProperty().addListener((obs,o,n) -> savePrefs(collectPrefsFromUI()));


        stage.setOnCloseRequest(e -> savePrefs(collectPrefsFromUI()));

        stage.setResizable(false);
        stage.setFullScreen(false);

        stage.fullScreenProperty().addListener((obs, oldV, newV) -> {
            if (newV) stage.setFullScreen(false);
        });
        return scene;
    }


    private void startCssWatcher(Path cssFile, Scene scene) {
        Thread watcher = new Thread(() -> {
            try {
                WatchService ws = FileSystems.getDefault().newWatchService();
                cssFile.getParent().register(ws,
                        StandardWatchEventKinds.ENTRY_MODIFY);
                while (true) {
                    WatchKey key = ws.take();
                    for (var ev : key.pollEvents()) {
                        Path changed = (Path) ev.context();
                        if (changed.equals(cssFile.getFileName())) {
                            Platform.runLater(() -> {
                                scene.getStylesheets().clear();
                                scene.getStylesheets().add(cssFile.toUri().toString());
                            });
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "CSS-Watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void crearAccesoDirectoEscritorio() {
        String nombre = "YaguaLauncher";
        String userHome = System.getProperty("user.home");
        Path escritorio = Paths.get(userHome, "Desktop", nombre + ".lnk");

        if (Files.exists(escritorio)) return;

        try {
            String exePath = new File(MainWindow.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI()).getParent() + "\\" + nombre + ".exe";

            String vbs = """
                        Set oWS = WScript.CreateObject("WScript.Shell")
                        sLinkFile = "%s"
                        Set oLink = oWS.CreateShortcut(sLinkFile)
                        oLink.TargetPath = "%s"
                        oLink.WindowStyle = 1
                        oLink.Save
                    """.formatted(escritorio.toString(), exePath);

            Path tempVbs = Files.createTempFile("shortcut", ".vbs");
            Files.writeString(tempVbs, vbs);
            new ProcessBuilder("wscript", tempVbs.toString()).start();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private ToggleButton makeNavButton(String path) {
        InputStream is = getClass().getResourceAsStream(path);
        ImageView iv = new ImageView();
        if (is != null) {
            Image img = new Image(is);
            iv.setImage(img);
            iv.setFitWidth(32);
            iv.setFitHeight(32);
        } else {
            System.err.println("⚠️ Icono no encontrado: " + path);
        }
        ToggleButton btn = new ToggleButton();
        btn.setGraphic(iv);
        btn.setPrefSize(48, 48);
        return btn;
    }

    private void showOnly(Region pane) {
        homePane.setVisible(false);
        profilesPane.setVisible(false);
        versionsPane.setVisible(false);
        launchPane.setVisible(false);
        consolePane.setVisible(false);
        screenshotsPane.setVisible(false);
        pane.setVisible(true);
    }

    private void buildHomePane() {
        InputStream b = getClass().getResourceAsStream("/ui/images/home_banner.jpg");
        Node banner = (b != null)
                ? new ImageView(new Image(b))
                : createPlaceholder(600, 200);
        if (banner instanceof ImageView iv) {
            iv.setPreserveRatio(true);
            iv.setFitWidth(600);
        }

        bigPlay = new Button("JUGAR");
        bigPlay.getStyleClass().add("big-play-button");
        bigPlay.setOnAction(e -> {
            String ver = versionCombo.getValue();
            if (ver == null) {
                new Alert(Alert.AlertType.WARNING,
                        "Selecciona primero una versión en la pestaña Versiones")
                        .showAndWait();
                return;
            }
            disablePlayButtons();
            launchGame();
        });

        VBox v = new VBox(20, banner, bigPlay);
        v.setAlignment(Pos.CENTER);
        v.setPadding(new Insets(20));

        homePane = new VBox(v);
        homePane.setAlignment(Pos.CENTER);
        homePane.getStyleClass().add("home-pane");
    }

    private void buildProfilesPane() {
        Label h = new Label("Perfiles");
        h.getStyleClass().add("section-header");
        profileCombo = new ComboBox<>();
        newProfileBtn = new Button("Nuevo perfil");
        saveProfileBtn = new Button("Guardar perfil");
        deleteProfileBtn = new Button("Borrar perfil");
        HBox row = new HBox(8, profileCombo, newProfileBtn, saveProfileBtn, deleteProfileBtn);
        row.getStyleClass().add("section-row");

        profilesPane = new VBox(12, h, row);
        profilesPane.setPadding(new Insets(20));
        profilesPane.getStyleClass().add("section-pane");

        newProfileBtn.setOnAction(e -> createNewProfile());
        saveProfileBtn.setOnAction(e -> saveCurrentProfile());
        deleteProfileBtn.setOnAction(e -> deleteSelectedProfile());
        profileCombo.setOnAction(e -> applySelectedProfile());

        profileCombo.valueProperty().addListener((obs,o,n) -> {
            savePrefs(collectPrefsFromUI());
        });
    }

    private void buildVersionsPane() {
        Label h = new Label("Versiones");
        h.getStyleClass().add("section-header");
        showSnapshotsCheckBox = new CheckBox("Mostrar snapshots");
        versionCombo = new ComboBox<>();
        downloadButton = new Button("Descargar versión");
        HBox row = new HBox(8, showSnapshotsCheckBox, versionCombo, downloadButton);
        row.getStyleClass().add("section-row");

        progressBar = new ProgressBar(0);
        statusLabel = new Label(" ");

        versionsPane = new VBox(12, h, row, progressBar, statusLabel);
        versionsPane.setPadding(new Insets(20));
        versionsPane.getStyleClass().add("section-pane");

        showSnapshotsCheckBox.setOnAction(e -> refreshVersionList());
        versionCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs, o, n) -> onVersionSelected(n));
        downloadButton.setOnAction(e -> downloadVersionAssets());
    }

    private void buildLaunchPane() {
        Label h = new Label("Seleccionar RAM y lanzar el juego");
        h.getStyleClass().add("section-header");

        Label ramLabel = new Label("RAM (MB):");
        ramLabel.getStyleClass().add("section-label");

        ramField = new TextField("1024");
        ramField.setPrefWidth(100);
        ramField.getStyleClass().add("text-field");

        launchButton = new Button("Lanzar Minecraft");
        launchButton.getStyleClass().add("launch-button");

        HBox row = new HBox(8, ramLabel, ramField, launchButton);
        row.getStyleClass().add("section-row");
        row.setAlignment(Pos.CENTER_LEFT);


        openDirButton = new Button("Abrir carpeta de juego");
        openDirButton.getStyleClass().add("open-dir-button");
        openDirButton.setOnAction(e -> {
            try {
                Desktop.getDesktop().open(mcBaseDir.toFile());
            } catch (IOException ex) {
                ex.printStackTrace();
                new Alert(Alert.AlertType.ERROR,
                        "No pude abrir la carpeta:\n" + ex.getMessage()).showAndWait();
            }
        });


        updateButton = new Button("Comprobar actualizaciones");
        updateButton.getStyleClass().add("update-button");
        updateButton.setOnAction(e -> checkForUpdates());


        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);


        Region bottomSpacer = new Region();
        HBox.setHgrow(bottomSpacer, Priority.ALWAYS);

        HBox bottomRow = new HBox(10,
                openDirButton,
                bottomSpacer,
                updateButton
        );
        bottomRow.getStyleClass().add("section-row");
        bottomRow.setAlignment(Pos.CENTER_LEFT);

        // montamos todo
        launchPane = new VBox(12,
                h,
                row,
                spacer,
                bottomRow
        );
        launchPane.setPadding(new Insets(20));
        launchPane.getStyleClass().add("section-pane");

        launchButton.setOnAction(e -> {
            disablePlayButtons();
            launchGame();
        });
    }


    private void disablePlayButtons() {
        bigPlay.setText("Jugando…");
        bigPlay.setDisable(true);

        launchButton.setText("Jugando…");
        launchButton.setDisable(true);
    }

    private void enablePlayButtons() {
        bigPlay.setText("JUGAR");
        bigPlay.setDisable(false);

        launchButton.setText("Lanzar Minecraft");
        launchButton.setDisable(false);
    }

    private Node createPlaceholder(int w, int h) {
        Region ph = new Region();
        ph.setPrefSize(w, h);
        ph.setStyle("-fx-background-color:#333;");
        return ph;
    }

    private Label createTitleLabel(String text) {
        Label lbl = new Label(text);
        lbl.setTextFill(javafx.scene.paint.Color.WHITE);
        lbl.setFont(Font.font("Roboto", 24));
        return lbl;
    }

    private void selectDefaultProfileFromPrefs() {
        LauncherPrefs prefs = loadPrefs();
        String last = (prefs != null) ? prefs.lastProfile : null;

        if (last != null && profileCombo.getItems().contains(last)) {
            profileCombo.getSelectionModel().select(last);
        } else if (!profileCombo.getItems().isEmpty()) {
            profileCombo.getSelectionModel().selectFirst();
        }
        applySelectedProfile(); // para que actualice versión/RAM en la UI
    }

    private void postLoginInit() {
        scanInstalledVersions();
        profileCombo.getItems().setAll(profileManager.getProfiles().keySet());
        selectDefaultProfileFromPrefs();
        profileCombo.valueProperty().addListener((obs,o,n) -> savePrefs(collectPrefsFromUI()));
        loadVersionsAsync();
    }

    private void scanInstalledVersions() {
        installedVersions.clear();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(mcBaseDir.resolve("versions"))) {
            for (Path v : ds) {
                if (Files.isDirectory(v) && Files.exists(v.resolve(v.getFileName() + ".jar"))) {
                    installedVersions.add(v.getFileName().toString());
                }
            }
        } catch (IOException ignored) {
        }
    }

    private void createNewProfile() {
        TextInputDialog dlg = new TextInputDialog();
        dlg.setHeaderText("Nombre del nuevo perfil:");
        dlg.showAndWait().ifPresent(name -> {
            if (!name.isBlank()) {
                Profile p = new Profile(
                        name,
                        versionCombo.getValue(),
                        Integer.parseInt(ramField.getText().trim())
                );
                profileManager.addOrUpdate(p);
                profileCombo.getItems().add(name);
                profileCombo.getSelectionModel().select(name);
            }
        });
    }

    private void saveCurrentProfile() {
        String name = profileCombo.getValue();
        if (name != null) {
            Profile p = new Profile(
                    name,
                    versionCombo.getValue(),
                    Integer.parseInt(ramField.getText().trim())
            );
            profileManager.addOrUpdate(p);
        }
    }

    private void deleteSelectedProfile() {
        String name = profileCombo.getValue();
        if (name != null) {
            profileManager.remove(name);
            profileCombo.getItems().remove(name);
            profileCombo.getSelectionModel().clearSelection();
        }
    }

    private void applySelectedProfile() {
        String name = profileCombo.getValue();
        if (name != null) {
            Profile p = profileManager.getProfiles().get(name);
            versionCombo.getSelectionModel().select(p.getVersionId());
            ramField.setText(String.valueOf(p.getRamMb()));
        }
    }

    private void loadVersionsAsync() {
        versionCombo.setDisable(true);
        downloadButton.setDisable(true);
        showSnapshotsCheckBox.setDisable(true);

        Task<List<String>> t = new Task<>() {
            @Override
            protected List<String> call() throws Exception {
                versionManager.fetchManifest();
                return versionManager.getVersionsIds();
            }
        };
        t.setOnSucceeded(evt -> {
            allRemoteVersions = t.getValue();
            showSnapshotsCheckBox.setDisable(false);
            refreshVersionList();
        });
        t.setOnFailed(evt -> statusLabel.setText("Error cargando versiones"));
        new Thread(t) {{
            setDaemon(true);
        }}.start();
    }

    private void refreshVersionList() {
        List<String> items = new ArrayList<>();
        boolean snap = showSnapshotsCheckBox.isSelected();
        for (String id : allRemoteVersions) {
            if (id.matches("\\d+(?:\\.\\d+)*") ||
                    (snap && id.matches("\\d{2}w\\d{2}[a-z]"))) {
                items.add(id);
            }
        }
        for (String loc : installedVersions) {
            if (!items.contains(loc)) items.add(loc);
        }
        versionCombo.getItems().setAll(items);
        if (!items.isEmpty()) {
            versionCombo.getSelectionModel().selectFirst();
        }
        versionCombo.setDisable(false);
    }

    private void onVersionSelected(String ver) {
        if (ver == null) return;
        boolean inst = installedVersions.contains(ver);
        downloadButton.setDisable(inst);
        launchButton.setDisable(!inst);
    }

    private void downloadVersionAssets() {
        String ver = versionCombo.getValue();
        if (ver == null) {
            statusLabel.setText("Primero elige una versión.");
            return;
        }

        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {

                if (versionManager.getVersions() == null || versionManager.getVersions().isEmpty()) {
                    versionManager.fetchManifest();
                }

                // Prepara un directorio "limpio" para la versión
                Path versionDir = mcBaseDir.resolve("versions").resolve(ver);
                if (Files.exists(versionDir)) {
                    try (java.util.stream.Stream<Path> walk = Files.walk(versionDir)) {
                        walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                            try { Files.delete(p); } catch (IOException ignored) {}
                        });
                    }
                }
                Files.createDirectories(versionDir);

                // 2) Localiza la URL del JSON de la versión en el manifest y lo guarda como <ver>.json
                var versionEntry = versionManager.getVersions().stream()
                        .filter(v -> v.getId().equals(ver))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException("Versión no encontrada en manifest: " + ver));

                String detailsUrl = versionEntry.getUrl();
                if (detailsUrl == null || detailsUrl.isBlank()) {
                    throw new IllegalStateException("No se encontró la URL del JSON para la versión " + ver);
                }

                Path jsonFile = versionDir.resolve(ver + ".json");
                updateMessage("Guardando " + ver + ".json");
                try (InputStream in = new URL(detailsUrl).openStream()) {
                    Files.copy(in, jsonFile, StandardCopyOption.REPLACE_EXISTING);
                }

                // obtiene los detalles de la versión (para libs, jar y assets)
                VersionDetails det = new VersionManager(mcBaseDir).resolveVersionDetails(ver, mcBaseDir);


                int libs = det.getLibraries().size();
                int coreTotal = libs + 1;
                int coreDone = 0;

                for (var lib : det.getLibraries()) {
                    var dls = lib.getDownloads();
                    if (dls == null || dls.getArtifact() == null) continue;

                    String url = dls.getArtifact().getUrl();
                    String sha = dls.getArtifact().getSha1();
                    if (url == null || sha == null) continue;

                    Path tgt = mcBaseDir.resolve("libraries")
                            .resolve(Paths.get(pathFromUrl(url)));

                    updateMessage("Librería: " + tgt.getFileName());
                    assetDownloader.downloadAndVerify(url, tgt, sha);
                    updateProgress(++coreDone, coreTotal);
                }

                // Descarga y extracción de Nativos LWJGL (Windows)

                Path nativesDir = versionDir.resolve(ver + "-natives");
                if (Files.exists(nativesDir)) {
                    try (java.util.stream.Stream<Path> walk = Files.walk(nativesDir)) {
                        walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                            try { Files.delete(p); } catch (IOException ignored) {}
                        });
                    }
                }
                Files.createDirectories(nativesDir);

                String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
                boolean isWindows = os.contains("win");

                for (var lib : det.getLibraries()) {
                    var dls = lib.getDownloads();
                    if (dls == null || dls.getClassifiers() == null) continue;

                    for (var entry : dls.getClassifiers().entrySet()) {
                        String key = entry.getKey();
                        if (key == null) continue;

                        String k = key.toLowerCase(Locale.ROOT);

                        if (!isWindows || !k.contains("natives-windows")) continue;

                        var nat = entry.getValue();
                        if (nat == null) continue;

                        String url = nat.getUrl();
                        String sha = nat.getSha1();
                        if (url == null || sha == null) continue;

                        // Descargamos el jar de nativos a la carpeta de libraries
                        Path natJar = mcBaseDir.resolve("libraries")
                                .resolve(Paths.get(pathFromUrl(url)));

                        updateMessage("Nativos: " + natJar.getFileName());
                        assetDownloader.downloadAndVerify(url, natJar, sha);


                        try (java.util.zip.ZipInputStream zin =
                                     new java.util.zip.ZipInputStream(Files.newInputStream(natJar))) {
                            java.util.zip.ZipEntry ze;
                            while ((ze = zin.getNextEntry()) != null) {
                                if (ze.isDirectory()) continue;
                                String name = ze.getName();
                                if (name.startsWith("META-INF/")) continue;

                                Path out = nativesDir.resolve(Paths.get(name).getFileName().toString());
                                Files.copy(zin, out, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                }


                var cd = det.getClientDownload();
                Path clientJar = versionDir.resolve(ver + ".jar");
                updateMessage("Cliente: " + ver + ".jar");
                assetDownloader.downloadAndVerify(cd.getUrl(), clientJar, cd.getSha1());
                updateProgress(++coreDone, coreTotal);


                VersionDetails.AssetIndexInfo aiInfo = det.getAssetIndex();
                AssetIndex ai = assetsManager.fetchAssetIndex(aiInfo.getUrl(), aiInfo.getId());
                int totalA = ai.objects.size(), doneA = 0;
                for (Map.Entry<String, AssetObject> e : ai.objects.entrySet()) {
                    String objectKey = e.getKey();
                    String hash = e.getValue().getHash();
                    updateMessage("Asset " + (++doneA) + "/" + totalA + ": " + objectKey);
                    assetsManager.downloadSingleAsset(objectKey, hash);
                    updateProgress(coreDone + doneA, coreTotal + totalA);
                }


                installedVersions.add(ver);
                updateMessage("¡Descarga completa!");
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
            onVersionSelected(ver);
        });
        task.setOnFailed(evt -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("Error durante descarga");
            task.getException().printStackTrace();
        });

        new Thread(task) {{ setDaemon(true); }}.start();
    }



    private void launchGame() {
        String ver = versionCombo.getValue();
        if (ver == null) {
            statusLabel.setText("Selecciona una versión primero.");
            return;
        }
        int ram;
        try {
            ram = Integer.parseInt(ramField.getText().trim());
        } catch (NumberFormatException ex) {
            statusLabel.setText("RAM inválida.");
            enablePlayButtons();
            return;
        }

        new Thread(() -> {
            Platform.runLater(() -> statusLabel.setText("Lanzando…"));
            try {
                // Llama a la versión que vuelca logs a consola
                launchExecutor.launch(
                        session,
                        ver,
                        mcBaseDir.toFile(),
                        ram,
                        null, 0,
                        line -> Platform.runLater(() -> consoleTextArea.appendText(line + "\n")),
                        err -> Platform.runLater(() -> consoleTextArea.appendText("[ERR] " + err + "\n"))
                );
            } catch (Exception ex) {
                ex.printStackTrace();
                Platform.runLater(() -> statusLabel.setText("Error al lanzar"));
            } finally {

                Platform.runLater(() -> {
                    statusLabel.setText("¡Juego cerrado!");
                    enablePlayButtons();
                });
            }
        }, "Launcher-Thread").start();
    }


    private void buildScreenshotsPane() {
        Path ssDir = mcBaseDir.resolve("screenshots");
        File folder = ssDir.toFile();
        if (!folder.exists()) folder.mkdirs();


        preview = new ImageView();
        preview.setPreserveRatio(true);
        preview.setFitWidth(600);
        preview.setFitHeight(400);
        preview.getStyleClass().add("screenshot-preview");

        current = new SimpleObjectProperty<>();


        preview.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && current.get() != null) {
                try {
                    Desktop.getDesktop().open(current.get().toFile());
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        });


        thumbsColumn = new VBox(10);
        thumbsColumn.setPadding(new Insets(10));
        thumbsColumn.getStyleClass().add("screenshot-thumbs-column");

        thumbsColumn.setStyle(null);

        refreshScreenshotsOnce(folder.toPath());

        ScrollPane scrollThumbs = new ScrollPane(thumbsColumn);
        scrollThumbs.setFitToWidth(true);
        scrollThumbs.setPrefWidth(130);
        scrollThumbs.setStyle("-fx-background: transparent;");
        VBox.setVgrow(scrollThumbs, Priority.ALWAYS);

        Label header = new Label("Screenshots");
        header.getStyleClass().add("section-header");

        HBox hbox = new HBox(15, preview, scrollThumbs);
        hbox.setAlignment(Pos.CENTER);
        HBox.setHgrow(preview, Priority.ALWAYS);

        screenshotsPane = new VBox(10, header, hbox);
        screenshotsPane.setPadding(new Insets(20));
        screenshotsPane.getStyleClass().add("section-pane");
        screenshotsPane.setVisible(false);
        VBox.setVgrow(screenshotsPane, Priority.ALWAYS);


        startScreenshotsTimer(folder.toPath());
    }


    private void refreshScreenshotsOnce(Path ssDir) {
        File[] files = ssDir.toFile().listFiles((d, n) -> {
            String ln = n.toLowerCase();
            return ln.endsWith(".png") || ln.endsWith(".jpg") || ln.endsWith(".jpeg");
        });
        currentScreenshots.clear();
        thumbsColumn.getChildren().clear();

        if (files == null || files.length == 0) {
            Label none = new Label("No se encontraron screenshots");
            none.getStyleClass().add("section-status");
            thumbsColumn.getChildren().add(none);
        } else {

            currentScreenshots.clear();
            for (File f : files) currentScreenshots.add(f.toPath());
            Path first = files[0].toPath();
            current.set(first);
            preview.setImage(new Image(first.toUri().toString(), 600, 0, true, true));


            for (Path p : currentScreenshots) {
                addScreenshotThumbnail(p);
            }
        }
    }

    private void refreshScreenshots(Path ssDir) {
        // Listado actual en disco
        Set<Path> found = new HashSet<>();
        File[] files = ssDir.toFile().listFiles((d, n) -> {
            String ln = n.toLowerCase();
            return ln.endsWith(".png") || ln.endsWith(".jpg");
        });
        if (files != null) for (File f : files) found.add(f.toPath());


        for (Path p : found) {
            if (!currentScreenshots.contains(p)) {
                currentScreenshots.add(p);
                addScreenshotThumbnail(p);
            }
        }

        Iterator<Path> it = currentScreenshots.iterator();
        while (it.hasNext()) {
            Path p = it.next();
            if (!found.contains(p)) {
                removeScreenshotThumbnail(p);
                it.remove();
            }
        }
    }

    private void startScreenshotsTimer(Path ssDir) {
        Timeline timer = new Timeline(
                new KeyFrame(Duration.ZERO, e -> refreshScreenshots(ssDir)),
                new KeyFrame(Duration.seconds(5))
        );
        timer.setCycleCount(Timeline.INDEFINITE);
        timer.play();
    }


    private void startScreenshotWatcher() {
        try {
            Path dir = mcBaseDir.resolve("screenshots");
            screenshotWatcher = FileSystems.getDefault().newWatchService();
            dir.register(screenshotWatcher, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE);


            screenshotWatcherThread = new Thread(() -> {
                try {
                    while (true) {
                        WatchKey key = screenshotWatcher.take();
                        for (WatchEvent<?> ev : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = ev.kind();
                            Path filename = ((WatchEvent<Path>) ev).context();
                            Path fullPath = dir.resolve(filename);


                            String ln = filename.toString().toLowerCase();
                            if (!(ln.endsWith(".png") || ln.endsWith(".jpg"))) continue;

                            Platform.runLater(() -> {
                                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                                    addScreenshotThumbnail(fullPath);
                                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                                    removeScreenshotThumbnail(fullPath);
                                }
                            });
                        }
                        if (!key.reset()) break;
                    }
                } catch (InterruptedException ignored) {
                }
            }, "Screenshots-Watcher");
            screenshotWatcherThread.setDaemon(true);
            screenshotWatcherThread.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addScreenshotThumbnail(Path imgPath) {
        try {

            Image thumbImg = new Image(
                    imgPath.toUri().toString(),
                    100,
                    0,
                    true,
                    true
            );


            ImageView thumb = new ImageView(thumbImg);
            thumb.setPreserveRatio(true);
            thumb.setFitWidth(100);
            thumb.getStyleClass().add("screenshot-thumb");


            StackPane thumbContainer = new StackPane(thumb);
            thumbContainer.getStyleClass().add("screenshot-thumb-container");
            thumbContainer.setPadding(new Insets(4));
            thumbContainer.setCursor(Cursor.HAND);


            thumbContainer.setOnMouseClicked(e -> {

                thumbsColumn.getChildren().forEach(node ->
                        node.getStyleClass().remove("selected-thumb")
                );

                thumbContainer.getStyleClass().add("selected-thumb");

                // actualizar la ruta actual y la vista previa
                current.set(imgPath);
                preview.setImage(new Image(
                        imgPath.toUri().toString(),
                        600, 0, true, true
                ));
            });

            // Añadir al VBox de miniaturas
            thumbsColumn.getChildren().add(thumbContainer);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }



    private void removeScreenshotThumbnail(Path imgPath) {
        thumbsColumn.getChildren().removeIf(node -> {
            if (node instanceof ImageView iv) {
                return iv.getImage().getUrl().equals(imgPath.toUri().toString());
            }
            return false;
        });

        if (current.get() != null && current.get().equals(imgPath)) {
            current.set(null);
            preview.setImage(null);
        }
    }

    private String pathFromUrl(String url) throws URISyntaxException {
        URI uri = new URI(url);
        String p = uri.getPath();
        return p.startsWith("/") ? p.substring(1) : p;
    }

    private static Path computeMcBaseDir() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            String appdata = System.getenv("APPDATA");
            return Paths.get(appdata != null ? appdata : System.getProperty("user.home"), ".minecraft");
        } else {
            return Paths.get(System.getProperty("user.home"), ".minecraft");
        }
    }


    private String getCurrentVersion() {

        String sys = System.getProperty("yagua.version");
        if (sys != null && !sys.isBlank()) return normalizeVersion(sys);


        Package pkg = getClass().getPackage();
        if (pkg != null) {
            String impl = pkg.getImplementationVersion();
            if (impl != null && !impl.isBlank()) return normalizeVersion(impl);
        }


        try (InputStream in = getClass().getResourceAsStream("/version.properties")) {
            if (in != null) {
                Properties props = new Properties();
                props.load(in);
                String v = props.getProperty("launcher.version");
                if (v != null && !v.isBlank()) return normalizeVersion(v);
            }
        } catch (IOException ignored) {}


        return "0.0.0";
    }

    private String normalizeVersion(String raw) {
        if (raw == null) return "0.0.0";
        String s = raw.trim().toLowerCase();

        if (s.startsWith("v")) s = s.substring(1);

        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^(\\d+(?:\\.\\d+)*)")
                .matcher(s);
        if (m.find()) return m.group(1);
        return "0.0.0";
    }

    private String fetchLatestTagName() throws IOException {
        URL url = new URL("https://api.github.com/repos/Alenkpo1/YaguaLauncher/releases/latest");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            String json = sb.toString();
            String key = "\"tag_name\":\"";
            int i = json.indexOf(key);
            if (i >= 0) {
                int start = i + key.length();
                int end = json.indexOf("\"", start);
                if (end >= 0) {
                    return json.substring(start, end);
                }
            }
        }
        throw new IOException("No pude obtener tag_name de la última release");
    }

    /**
     * Compara semánticamente dos versiones X.Y.Z
     */
    private boolean isNewer(String currentRaw, String latestRaw) {
        String current = normalizeVersion(currentRaw);
        String latest  = normalizeVersion(latestRaw);

        String[] a = current.split("\\.");
        String[] b = latest.split("\\.");

        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int ai = (i < a.length) ? parseOrZero(a[i]) : 0;
            int bi = (i < b.length) ? parseOrZero(b[i]) : 0;
            if (bi > ai) return true;
            if (bi < ai) return false;
        }
        return false;
    }
    private int parseOrZero(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private record ReleaseInfo(String tag, String assetUrl) {
    }

    private ReleaseInfo fetchLatestReleaseInfo() throws IOException {
        URL apiUrl = new URL("https://api.github.com/repos/Alenkpo1/YaguaLauncher/releases/latest");
        HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setConnectTimeout(7000);
        conn.setReadTimeout(7000);

        int status = conn.getResponseCode();
        if (status != 200) throw new IOException("GitHub API respondió HTTP " + status);

        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
        } finally {
            conn.disconnect();
        }
        String json = sb.toString();


        Matcher tagMatcher = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").matcher(json);
        if (!tagMatcher.find()) throw new IOException("No se encontró tag_name");
        String tag = tagMatcher.group(1);


        Matcher urlMatcher = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.exe)\"").matcher(json);
        if (!urlMatcher.find()) throw new IOException("No se encontró ningún asset .exe en la última release");
        String assetUrl = urlMatcher.group(1);

        return new ReleaseInfo(tag, assetUrl);
    }







    private void checkForUpdates() {
        updateButton.setDisable(true);
        Task<ReleaseInfo> t = new Task<>() {
            @Override protected ReleaseInfo call() throws Exception {
                String current = getCurrentVersion();
                updateMessage("Buscando actualizaciones…");
                ReleaseInfo info = fetchLatestReleaseInfo();
                System.out.println("[Update] current=" + current + " latest=" + info.tag());
                if (isNewer(current, info.tag())) return info;
                else return new ReleaseInfo("", "");
            }
        };
        t.setOnSucceeded(e -> {
            updateButton.setDisable(false);
            ReleaseInfo info = t.getValue();
            if (!info.tag().isEmpty()) {
                promptUpdate(info.tag(), info.assetUrl());
            } else {
                new Alert(Alert.AlertType.INFORMATION,
                        "Ya tienes la última versión (" + getCurrentVersion() + ")"
                ).showAndWait();
            }
        });
        t.setOnFailed(e -> {
            updateButton.setDisable(false);
            new Alert(Alert.AlertType.ERROR,
                    "Error al buscar actualizaciones:\n" + t.getException().getMessage()
            ).showAndWait();
        });
        new Thread(t, "Check-Updates").start();
    }

    private void promptUpdate(String latestTag, String assetUrl) {
        Alert dlg = new Alert(Alert.AlertType.CONFIRMATION,
                "Hay una nueva versión: " + latestTag + "\n¿Descargar y reiniciar ahora?",
                ButtonType.YES, ButtonType.NO
        );
        dlg.setTitle("Actualización disponible");
        dlg.setHeaderText(null);
        dlg.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.YES) {
                // Descarga en background
                Task<Path> downloadTask = new Task<>() {
                    @Override protected Path call() throws Exception {
                        updateButton.setDisable(true);
                        updateMessage("Descargando actualización…");
                        return downloadNewVersion(assetUrl);
                    }
                };
                downloadTask.setOnSucceeded(ev -> {
                    try {

                        scheduleInteractiveInstallAndRestart(downloadTask.getValue(), latestTag);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        new Alert(Alert.AlertType.ERROR, "Error al actualizar:\n" + ex.getMessage()).showAndWait();
                        updateButton.setDisable(false);
                    }
                });
                downloadTask.setOnFailed(ev -> {
                    new Alert(Alert.AlertType.ERROR,
                            "Error al descargar actualización:\n"
                                    + downloadTask.getException().getMessage()
                    ).showAndWait();
                    updateButton.setDisable(false);
                });
                new Thread(downloadTask, "Download-Update").start();
            }
        });
    }






    private Path downloadNewVersion(String assetUrl) throws IOException {
        URL url = new URL(assetUrl);
        String fileName = new File(url.getPath()).getName();
        Path tmp = Files.createTempFile("YaguaLauncher-update-", "-" + fileName);

        try (InputStream in = url.openStream();
             OutputStream out = Files.newOutputStream(tmp, StandardOpenOption.TRUNCATE_EXISTING)) {
            in.transferTo(out);
        }
        return tmp;
    }


    private void scheduleInteractiveInstallAndRestart(java.nio.file.Path installerExe, String latestTag) throws Exception {
        String exePath = getExePath();
        String exeName = new java.io.File(exePath).getName();

        String localAppData = System.getenv("LOCALAPPDATA");
        java.nio.file.Path flagDir  = java.nio.file.Paths.get(localAppData, "YaguaLauncher");
        java.nio.file.Path flagFile = flagDir.resolve("updated-" + latestTag + ".flag");
        java.nio.file.Files.createDirectories(flagDir);

        boolean needsElevation = exePath.toLowerCase().startsWith("c:\\program files")
                || exePath.toLowerCase().startsWith("c:\\program files (x86)");

        // Script .bat temporal que:
        // 1) espera a que cierre el launcher
        // 2) ejecuta el instalador con UI y ESPERA a que termine (elevado si hace falta)
        // 3) deja una bandera y relanza el EXE
        java.nio.file.Path script = java.nio.file.Files.createTempFile("yagua-update-", ".bat");

        String installer = installerExe.toString().replace("\"","\"\""); // escapar comillas
        String exeEsc    = exePath.replace("\"","\"\"");
        String runInstallerBlock =
                needsElevation

                        ? "powershell -NoProfile -ExecutionPolicy Bypass -Command \"Start-Process -FilePath '" + installer.replace("\\","\\\\") + "' -Verb RunAs -Wait\""

                        : "start /wait \"\" \"" + installer + "\"";

        String logFile = flagDir.resolve("update.log").toString();

        String bat = String.join("\r\n",
                "@echo off",
                "setlocal",
                "echo [YAGUA] Esperando a que cierre el launcher... > \"" + logFile + "\"",
                ":waitclose",
                "tasklist /FI \"IMAGENAME eq " + exeName + "\" | find /I \"" + exeName + "\" >nul",
                "if %ERRORLEVEL%==0 (",
                "  timeout /t 1 >nul",
                "  goto waitclose",
                ")",
                "",
                "echo [YAGUA] Ejecutando instalador... >> \"" + logFile + "\"",
                runInstallerBlock,
                "set EC=%ERRORLEVEL%",
                "echo [YAGUA] Instalador terminó con código %EC% >> \"" + logFile + "\"",
                "",
                "echo ok > \"" + flagFile.toString() + "\"",
                "start \"\" \"" + exeEsc + "\"",
                "endlocal",
                "exit"
        );
        java.nio.file.Files.writeString(script, bat, java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);


        new ProcessBuilder("cmd", "/C", "start", "\"YaguaUpdater\"", script.toString())
                .inheritIO()
                .start();


        javafx.application.Platform.exit();
    }
    /**
     * Crea y ejecuta un .bat que:
     *  - espera a que este proceso termine
     *  - mueve el JAR descargado sobre el JAR actual
     *  - relanza el launcher
     */
    private void scheduleExeInstallAndRestart(Path exeInstallerPath, String latestTag) throws Exception {
        String exePath = getExePath(); // tu método existente que devuelve el EXE actual instalado
        String exeName = new File(exePath).getName();

        String localAppData = System.getenv("LOCALAPPDATA");
        Path flagDir  = Paths.get(localAppData, "YaguaLauncher");
        Path flagFile = flagDir.resolve("updated-" + latestTag + ".flag");

        Path script = Files.createTempFile("update-yagua-", ".bat");
        String bat = String.join("\r\n",
                "@echo off",
                "set EXE=\"" + exePath + "\"",
                "set INSTALLER=\"" + exeInstallerPath.toString() + "\"",
                "set FLAGDIR=\"" + flagDir.toString() + "\"",
                "set FLAGFILE=\"" + flagFile.toString() + "\"",
                "",
                ":waitclose",
                "tasklist /FI \"IMAGENAME eq " + exeName + "\" | find /I \"" + exeName + "\" >nul",
                "if %ERRORLEVEL%==0 (",
                "  timeout /t 1 >nul",
                "  goto waitclose",
                ")",
                "",
                "if not exist %FLAGDIR% mkdir %FLAGDIR%",
                "",

                "\"%INSTALLER%\" /S /silent /quiet",
                "if %ERRORLEVEL% NEQ 0 (",
                "  echo Instalador devolvio %ERRORLEVEL%, probando sin silencioso...",
                "  start /wait \"\" \"%INSTALLER%\"",
                ")",
                "",
                "echo ok > %FLAGFILE%",
                "start \"\" %EXE%",
                "exit"
        );
        Files.writeString(script, bat, StandardOpenOption.TRUNCATE_EXISTING);

        new ProcessBuilder("cmd", "/C", "start", "\"\"", script.toString())
                .inheritIO()
                .start();

        Platform.exit();
    }

    public class ShortcutCreator {

        public static void crearAccesoDirecto(String nombre, Path destinoExe, Path icono) {
            try {
                // Ruta al escritorio del usuario
                String escritorio = System.getProperty("user.home") + "\\Desktop";
                File accesoDirecto = new File(escritorio, nombre + ".lnk");

                String comando = String.format(
                        "powershell -NoProfile -ExecutionPolicy Bypass -Command \""
                                + "$s=(New-Object -COM WScript.Shell).CreateShortcut('%s');"
                                + "$s.TargetPath='%s';"
                                + "$s.IconLocation='%s';"
                                + "$s.Save()\"",
                        accesoDirecto.getAbsolutePath().replace("\\", "\\\\"),
                        destinoExe.toAbsolutePath().toString().replace("\\", "\\\\"),
                        icono.toAbsolutePath().toString().replace("\\", "\\\\")
                );

                Runtime.getRuntime().exec(comando);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private String getExePath() {

        String jpackagePath = System.getenv("JPACKAGE_APP_PATH");
        if (jpackagePath != null && !jpackagePath.isEmpty()) {
            return jpackagePath;
        }


        try {
            File jarFile = new File(MainWindow.class
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());

            Path jarPath = jarFile.getAbsoluteFile().toPath();
            Path exePath = jarPath.getParent().getParent().resolve("YaguaLauncher.exe");

            System.out.println("DEBUG exe path (modo dev): " + exePath);
            return exePath.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }


    private void maybeShowUpdatedToast() {
        try {
            String localAppData = System.getenv("LOCALAPPDATA");
            Path dir = Paths.get(localAppData, "YaguaLauncher");
            if (!Files.isDirectory(dir)) return;

            try (Stream<Path> files = Files.list(dir)) {
                Optional<Path> flagOpt = files
                        .filter(p -> p.getFileName().toString().startsWith("updated-") && p.getFileName().toString().endsWith(".flag"))
                        .findFirst();

                if (flagOpt.isPresent()) {
                    String fname = flagOpt.get().getFileName().toString();
                    String tag = fname.substring("updated-".length(), fname.length() - ".flag".length());
                    new Alert(Alert.AlertType.INFORMATION, "Actualizado a " + tag + " correctamente.").showAndWait();
                    Files.deleteIfExists(flagOpt.get());
                }
            }
        } catch (Exception ignored) {}
    }

    private Path extraerRecursoComoArchivoTemporal(String resourcePath, String nombreArchivo) throws IOException {
        InputStream in = getClass().getResourceAsStream(resourcePath);
        if (in == null)
            throw new FileNotFoundException("No se encontró el recurso: " + resourcePath);

        Path tempFile = Files.createTempFile(nombreArchivo, null);
        Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile;
    }

    private static class LauncherPrefs {
        public String lastProfile;
        public String lastProfileId;
        public String lastVersionId;
        public boolean showSnapshots;
        public Integer ramMb;
    }

    // Ruta
    private Path prefsPath() {
        return mcBaseDir.resolve("launcher_prefs.json");
    }

    private LauncherPrefs loadPrefs() {
        try {
            Path p = prefsPath();
            if (Files.isRegularFile(p)) {
                return new com.fasterxml.jackson.databind.ObjectMapper().readValue(p.toFile(), LauncherPrefs.class);
            }
        } catch (Exception ignored) {}
        return new LauncherPrefs();
    }

    private void savePrefs(LauncherPrefs prefs) {
        try {
            Path p = prefsPath();
            Files.createDirectories(p.getParent());
            new com.fasterxml.jackson.databind.ObjectMapper()
                    .writerWithDefaultPrettyPrinter()
                    .writeValue(p.toFile(), prefs);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Captura el estado actual de la UI
    private LauncherPrefs collectPrefsFromUI() {
        LauncherPrefs prefs = new LauncherPrefs();
        prefs.lastProfileId = (profileCombo != null) ? profileCombo.getValue() : null;
        prefs.lastVersionId = (versionCombo != null) ? versionCombo.getValue() : null;
        prefs.lastProfile = (profileCombo != null) ? profileCombo.getValue() : null;
        prefs.showSnapshots = (showSnapshotsCheckBox != null) && showSnapshotsCheckBox.isSelected();
        try {
            prefs.ramMb = (ramField != null) ? Integer.parseInt(ramField.getText().trim()) : null;
        } catch (NumberFormatException ignored) {}
        return prefs;
    }

    // Aplica preferenciass a la UI si los ítems existen
    private void applyPrefsToUI(LauncherPrefs prefs) {
        if (prefs == null) return;

        if (showSnapshotsCheckBox != null)
            showSnapshotsCheckBox.setSelected(prefs.showSnapshots);

        if (ramField != null && prefs.ramMb != null)
            ramField.setText(String.valueOf(prefs.ramMb));

        // poblacion de combos
        Platform.runLater(() -> {
            if (profileCombo != null && prefs.lastProfileId != null &&
                    profileCombo.getItems().contains(prefs.lastProfileId)) {
                profileCombo.setValue(prefs.lastProfileId);
            }
            if (versionCombo != null && prefs.lastVersionId != null &&
                    versionCombo.getItems().contains(prefs.lastVersionId)) {
                versionCombo.setValue(prefs.lastVersionId);
            }
        });
    }

}


