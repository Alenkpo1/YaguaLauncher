package ui;

import com.sun.jna.Native;
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
import javafx.animation.Animation;
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
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.application.Platform;
import javafx.util.Duration;

import java.awt.*;
import java.io.*;
import java.net.*;

import java.net.InetAddress;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.sun.jna.platform.win32.Shell32;
import com.sun.jna.platform.win32.ShellAPI;
import com.sun.jna.platform.win32.WinDef;
import com.sun.jna.platform.win32.ShlObj;
import com.sun.jna.ptr.IntByReference;

import java.io.File;
import java.nio.file.Path;


public class MainWindow extends Application {
    // — Directorio Minecraft (~/.minecraft o %APPDATA%/.minecraft)
    private final Path mcBaseDir = computeMcBaseDir();

    private final String SERVER_HOST = "26.56.106.170";
    private final int    SERVER_PORT = 25565;
    private final String SERVER_NAME = "Server AlenyToti";
    // — Managers
    private AuthManager     authManager;
    private ProfileManager  profileManager;
    private VersionManager  versionManager;
    private AssetDownloader assetDownloader;
    private AssetsManager   assetsManager;
    private LaunchExecutor  launchExecutor;

    // — Estado de sesión y versiones
    private AuthManager.Session session;
    private List<String>        allRemoteVersions = Collections.emptyList();
    private final Set<String>   installedVersions  = new HashSet<>();

    // — Escenas
    private Scene loginScene, mainScene;

    private VBox                   thumbsColumn;
    private ImageView              preview;
    private ObjectProperty<Path>   current;

    // — Login UI
    private TextField usernameField;
    private Button    loginButton;
    private Label     loginStatusLabel;

    private WatchService           screenshotWatcher;
    private Thread                 screenshotWatcherThread;

    private VBox                   screenshotsPane;

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
    private Button           newProfileBtn, saveProfileBtn, deleteProfileBtn;
    private Label serverNameLabel;
    private Circle serverStatusCircle;
    // — Controles sección Versiones
    private CheckBox         showSnapshotsCheckBox;
    private ComboBox<String> versionCombo;
    private Button           downloadButton;
    private ProgressBar      progressBar;
    private Label            statusLabel;

    private TextArea consoleTextArea;
    // — Controles sección Lanzamiento
    private TextField ramField;
    private Button    launchButton;
    private Label  serverLabel;
    private Label  pingLabel;

    private Button openDirButton;
    private Button updateButton;

    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) throws URISyntaxException {
        // Carga opcional de fuente
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

        // en tu clase MainWindow o en tu inicializador de UI
        String fontPath = "/ui/fonts/Rubik-Bold.ttf"; // ruta en src/main/resources/fonts
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
        authManager     = new AuthManager();
        profileManager  = new ProfileManager(mcBaseDir);
        versionManager  = new VersionManager();
        assetDownloader = new AssetDownloader();
        assetsManager   = new AssetsManager(mcBaseDir.resolve("assets"));
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) javaHome = System.getProperty("java.home");
        launchExecutor  = new LaunchExecutor(javaHome);

        // Construye escenas
        loginScene = buildLoginScene(stage);
        mainScene  = buildMainScene(stage);

        // Muestra login
        stage.setScene(loginScene);
        stage.setTitle("YaguaLauncher");
        stage.setWidth(854);
        stage.setHeight(500);
        stage.centerOnScreen();
        stage.setResizable(true);
        stage.show();
    }

    public static class Server {
        private final String name;
        private final String address;
        public final SimpleStringProperty latency = new SimpleStringProperty("…");

        public Server(String name, String address) {
            this.name = name;
            this.address = address;
        }
        public String getName()    { return name; }
        public String getAddress() { return address; }
    }

    private ObservableList<Server> loadServers() {
        // Aquí defines tus servidores fijos o los lees de un archivo
        return FXCollections.observableArrayList(
                new Server("Servidor A", "mc.hypixel.net")
        );
    }

    private void buildConsolePane() {
        consoleTextArea = new TextArea();
        consoleTextArea.setEditable(false);
        consoleTextArea.getStyleClass().add("console-text-area");
        // permitir que crezca al máximo
        consoleTextArea.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        ScrollPane sp = new ScrollPane(consoleTextArea);
        sp.getStyleClass().add("console-scroll-pane");
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        // permitir que crezca al máximo
        sp.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(sp, Priority.ALWAYS);

        consolePane = new VBox(sp);
        consolePane.getStyleClass().add("console-pane");
        consolePane.setPadding(new Insets(10));
        // si lo metes en un VBox y quieres también que lo expanda:
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
        usernameField = new TextField();
        usernameField.setPromptText("Usuario");
        usernameField.getStyleClass().add("login-field");

        loginButton = new Button("Login");
        loginButton.getStyleClass().add("login-button");
        loginStatusLabel = new Label();
        loginStatusLabel.getStyleClass().add("login-status");
        loginStatusLabel.setStyle("-fx-text-fill: tomato;");

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
        loginButton.setOnAction(e -> {
            String user = usernameField.getText().trim();
            if (user.isEmpty()) {
                loginStatusLabel.setText("Ingresa un nombre de usuario.");
                return;
            }
            session = authManager.loginOffline(user);
            try { authManager.saveSession(session); } catch (IOException ex) { ex.printStackTrace(); }
            Stage st = (Stage) loginButton.getScene().getWindow();
            st.setScene(mainScene);
            st.setWidth(854);
            st.setHeight(520);
            st.centerOnScreen();
            postLoginInit();
        });

        InputStream bgStream = getClass().getResourceAsStream("/ui/images/login_bg.jpg");
        ImageView bgView = (bgStream != null)
                ? new ImageView(new Image(bgStream))
                : new ImageView();
        bgView.setPreserveRatio(false);

        StackPane root = new StackPane(bgView, overlay);
        bgView.fitWidthProperty().bind(root.widthProperty());
        bgView.fitHeightProperty().bind(root.heightProperty());
        StackPane.setAlignment(overlay, Pos.CENTER_LEFT);
        StackPane.setMargin(overlay, new Insets(0,0,0,50));

        Scene scene = new Scene(root, 1024, 520);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());
        return scene;
    }


    private double xOffset, yOffset;
    private Scene buildMainScene(Stage stage) {
        // -> Quitar decoración nativa
        stage.initStyle(StageStyle.UNDECORATED);

        // 1) Botones laterales
        navHome     = makeNavButton("/ui/icons/home.png");
        navProfiles = makeNavButton("/ui/icons/user.png");
        navVersions = makeNavButton("/ui/icons/versions.png");
        navLaunch   = makeNavButton("/ui/icons/play.png");
        navConsole  = makeNavButton("/ui/icons/console.png");
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

        // 2) Contenido central
        buildHomePane();
        buildProfilesPane();
        buildVersionsPane();
        buildLaunchPane();
        buildConsolePane();
        buildScreenshotsPane();
        StackPane content = new StackPane(homePane, profilesPane, versionsPane, launchPane, consolePane, screenshotsPane);
        showOnly(homePane);
        navHome    .setOnAction(e -> showOnly(homePane));
        navProfiles.setOnAction(e -> showOnly(profilesPane));
        navVersions.setOnAction(e -> showOnly(versionsPane));
        navLaunch  .setOnAction(e -> showOnly(launchPane));
        navConsole .setOnAction(e -> showOnly(consolePane));
        navScreenshots.setOnAction(e -> showOnly(screenshotsPane));


        // 3) Indicador de servidor arriba, dentro del layout principal
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

        // 4) Layout principal con BorderPane
        BorderPane mainPane = new BorderPane();
        mainPane.getStyleClass().add("content-pane");
        mainPane.setLeft(navBar);
        navBar.prefHeightProperty().bind(mainPane.heightProperty());
        mainPane.setCenter(content);
        mainPane.setTop(serverBox);

        // 5) Barra de título custom (flotando encima)
        Label titleLabel = new Label("YaguaLauncher");
        titleLabel.getStyleClass().add("window-title");

        Button btnMin = new Button("–");
        btnMin.getStyleClass().add("window-button");
        btnMin.setOnAction(e -> stage.setIconified(true));

        Button btnMax = new Button("▢");
        btnMax.getStyleClass().add("window-button");
        btnMax.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));

        Button btnClose = new Button("✕");
        btnClose.getStyleClass().addAll("window-button", "window-close");
        btnClose.setOnAction(e -> stage.close());

        HBox windowControls = new HBox(5, btnMin, btnMax, btnClose);
        windowControls.setAlignment(Pos.CENTER_RIGHT);

        Region dragSpacer = new Region();
        HBox.setHgrow(dragSpacer, Priority.ALWAYS);

        HBox titleBar = new HBox(15, titleLabel, dragSpacer, windowControls);
        titleBar.getStyleClass().add("window-title-bar");
        titleBar.setPadding(new Insets(5, 10, 5, 10));
        // Arrastrar ventana por el titleBar
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

        // 6) Contenedor raíz: VBox con titleBar arriba y mainPane abajo
        VBox root = new VBox(titleBar, mainPane);
        root.getStyleClass().add("root");
        VBox.setVgrow(mainPane, Priority.ALWAYS);

        // 7) Escena y CSS
        Scene scene = new Scene(root, 1024, 520);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());

        // 8) Ping periódico cada 5s
        Timeline pingTimer = new Timeline(
                new KeyFrame(Duration.ZERO,    e -> pingServer()),
                new KeyFrame(Duration.seconds(5))
        );
        pingTimer.setCycleCount(Timeline.INDEFINITE);
        pingTimer.play();

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
                        Path changed = (Path)ev.context();
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

        if (Files.exists(escritorio)) return; // ya existe

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
        btn.setPrefSize(48,48);
        return btn;
    }

    private void showOnly(Region pane){
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
        Node banner = (b!=null)
                ? new ImageView(new Image(b))
                : createPlaceholder(600,200);
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
        profileCombo     = new ComboBox<>();
        newProfileBtn    = new Button("Nuevo perfil");
        saveProfileBtn   = new Button("Guardar perfil");
        deleteProfileBtn = new Button("Borrar perfil");
        HBox row = new HBox(8, profileCombo, newProfileBtn, saveProfileBtn, deleteProfileBtn);
        row.getStyleClass().add("section-row");

        profilesPane = new VBox(12, h, row);
        profilesPane.setPadding(new Insets(20));
        profilesPane.getStyleClass().add("section-pane");

        newProfileBtn   .setOnAction(e->createNewProfile());
        saveProfileBtn  .setOnAction(e->saveCurrentProfile());
        deleteProfileBtn.setOnAction(e->deleteSelectedProfile());
        profileCombo    .setOnAction(e->applySelectedProfile());
    }

    private void buildVersionsPane() {
        Label h = new Label("Versiones");
        h.getStyleClass().add("section-header");
        showSnapshotsCheckBox = new CheckBox("Mostrar snapshots");
        versionCombo          = new ComboBox<>();
        downloadButton        = new Button("Descargar versión");
        HBox row = new HBox(8, showSnapshotsCheckBox, versionCombo, downloadButton);
        row.getStyleClass().add("section-row");

        progressBar = new ProgressBar(0);
        statusLabel = new Label(" ");

        versionsPane = new VBox(12, h, row, progressBar, statusLabel);
        versionsPane.setPadding(new Insets(20));
        versionsPane.getStyleClass().add("section-pane");

        showSnapshotsCheckBox.setOnAction(e->refreshVersionList());
        versionCombo.getSelectionModel().selectedItemProperty()
                .addListener((obs,o,n)-> onVersionSelected(n));
        downloadButton.setOnAction(e->downloadVersionAssets());
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

        // Botón abrir carpeta .minecraft
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

        // Botón comprobar actualizaciones
        updateButton = new Button("Comprobar actualizaciones");
        updateButton.getStyleClass().add("update-button");
        updateButton.setOnAction(e -> checkForUpdates());

        // spacer vertical para llevar botones abajo
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        // fila inferior: botón izq y botón der
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
        bigPlay    .setText("Jugando…");
        bigPlay    .setDisable(true);

        launchButton.setText("Jugando…");
        launchButton.setDisable(true);
    }

    private void enablePlayButtons() {
        bigPlay    .setText("JUGAR");
        bigPlay    .setDisable(false);

        launchButton.setText("Lanzar Minecraft");
        launchButton.setDisable(false);
    }

    private Node createPlaceholder(int w, int h) {
        Region ph = new Region();
        ph.setPrefSize(w,h);
        ph.setStyle("-fx-background-color:#333;");
        return ph;
    }

    private Label createTitleLabel(String text) {
        Label lbl = new Label(text);
        lbl.setTextFill(javafx.scene.paint.Color.WHITE);
        lbl.setFont(Font.font("Roboto",24));
        return lbl;
    }

    private void postLoginInit(){
        scanInstalledVersions();
        profileCombo.getItems().setAll(profileManager.getProfiles().keySet());
        loadVersionsAsync();
    }

    private void scanInstalledVersions(){
        installedVersions.clear();
        try(DirectoryStream<Path> ds = Files.newDirectoryStream(mcBaseDir.resolve("versions"))){
            for(Path v: ds){
                if(Files.isDirectory(v) && Files.exists(v.resolve(v.getFileName()+".jar"))){
                    installedVersions.add(v.getFileName().toString());
                }
            }
        }catch(IOException ignored){}
    }

    private void createNewProfile(){
        TextInputDialog dlg = new TextInputDialog();
        dlg.setHeaderText("Nombre del nuevo perfil:");
        dlg.showAndWait().ifPresent(name->{
            if(!name.isBlank()){
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

    private void saveCurrentProfile(){
        String name = profileCombo.getValue();
        if(name!=null){
            Profile p = new Profile(
                    name,
                    versionCombo.getValue(),
                    Integer.parseInt(ramField.getText().trim())
            );
            profileManager.addOrUpdate(p);
        }
    }

    private void deleteSelectedProfile(){
        String name = profileCombo.getValue();
        if(name!=null){
            profileManager.remove(name);
            profileCombo.getItems().remove(name);
            profileCombo.getSelectionModel().clearSelection();
        }
    }

    private void applySelectedProfile(){
        String name = profileCombo.getValue();
        if(name!=null){
            Profile p = profileManager.getProfiles().get(name);
            versionCombo.getSelectionModel().select(p.getVersionId());
            ramField.setText(String.valueOf(p.getRamMb()));
        }
    }

    private void loadVersionsAsync(){
        versionCombo.setDisable(true);
        downloadButton.setDisable(true);
        showSnapshotsCheckBox.setDisable(true);

        Task<List<String>> t = new Task<>() {
            @Override protected List<String> call() throws Exception {
                versionManager.fetchManifest();
                return versionManager.getVersionsIds();
            }
        };
        t.setOnSucceeded(evt->{
            allRemoteVersions = t.getValue();
            showSnapshotsCheckBox.setDisable(false);
            refreshVersionList();
        });
        t.setOnFailed(evt->statusLabel.setText("Error cargando versiones"));
        new Thread(t){{ setDaemon(true); }}.start();
    }

    private void refreshVersionList(){
        List<String> items = new ArrayList<>();
        boolean snap = showSnapshotsCheckBox.isSelected();
        for(String id : allRemoteVersions){
            if(id.matches("\\d+(?:\\.\\d+)*") ||
                    (snap && id.matches("\\d{2}w\\d{2}[a-z]"))) {
                items.add(id);
            }
        }
        for(String loc : installedVersions){
            if(!items.contains(loc)) items.add(loc);
        }
        versionCombo.getItems().setAll(items);
        if(!items.isEmpty()) {
            versionCombo.getSelectionModel().selectFirst();
        }
        versionCombo.setDisable(false);
    }

    private void onVersionSelected(String ver){
        if(ver==null) return;
        boolean inst = installedVersions.contains(ver);
        downloadButton.setDisable(inst);
        launchButton .setDisable(!inst);
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
                // 1) Prepara un directorio "limpio" para la versión
                Path versionDir = mcBaseDir.resolve("versions").resolve(ver);
                if (Files.exists(versionDir)) {
                    // Borra recursivamente cualquier resto de descargas previas
                    try (Stream<Path> walk = Files.walk(versionDir)) {
                        walk.sorted(Comparator.reverseOrder())
                                .forEach(p -> {
                                    try { Files.delete(p); }
                                    catch (IOException ignored) {}
                                });
                    }
                }
                // Crea el directorio de la versión de nuevo
                Files.createDirectories(versionDir);

                // 2) Obtiene los detalles de la versión
                VersionDetails det = versionManager.fetchVersionDetails(ver);

                // 3) Descarga librerías
                int libs      = det.getLibraries().size();
                int coreTotal = libs + 1;
                int coreDone  = 0;
                for (var lib : det.getLibraries()) {
                    String url = lib.getDownloads().getArtifact().getUrl();
                    String sha = lib.getDownloads().getArtifact().getSha1();
                    Path tgt   = mcBaseDir.resolve("libraries")
                            .resolve(Paths.get(pathFromUrl(url)));
                    updateMessage("Librería: " + tgt.getFileName());
                    assetDownloader.downloadAndVerify(url, tgt, sha);
                    updateProgress(++coreDone, coreTotal);
                }

                // 4) Descarga el JAR del cliente
                var cd = det.getClientDownload();
                Path clientJar = versionDir.resolve(ver + ".jar");
                updateMessage("Cliente: " + ver + ".jar");
                assetDownloader.downloadAndVerify(cd.getUrl(), clientJar, cd.getSha1());
                updateProgress(++coreDone, coreTotal);

                // 5) Descarga los assets
                VersionDetails.AssetIndexInfo aiInfo = det.getAssetIndex();
                AssetIndex ai = assetsManager.fetchAssetIndex(aiInfo.getUrl(), aiInfo.getId());
                int totalA = ai.objects.size(), doneA = 0;
                for (Map.Entry<String, AssetObject> e : ai.objects.entrySet()) {
                    String objectKey = e.getKey();
                    String hash      = e.getValue().getHash();
                    updateMessage("Asset " + (++doneA) + "/" + totalA + ": " + objectKey);
                    assetsManager.downloadSingleAsset(objectKey, hash);
                    updateProgress(coreDone + doneA, coreTotal + totalA);
                }

                // 6) Marca la versión como instalada
                installedVersions.add(ver);
                updateMessage("¡Descarga completa!");
                return null;
            }
        };

        // Bindings con la UI
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
            statusLabel.setText("Error durante descarga");
            task.getException().printStackTrace();
        });

        new Thread(task) {{ setDaemon(true); }}.start();
    }


    private void launchGame(){
        String ver = versionCombo.getValue();
        if(ver==null){
            statusLabel.setText("Selecciona una versión primero.");
            return;
        }
        int ram;
        try { ram = Integer.parseInt(ramField.getText().trim()); }
        catch(NumberFormatException ex){
            statusLabel.setText("RAM inválida.");
            enablePlayButtons();
            return;
        }

        new Thread(() -> {
            Platform.runLater(() -> statusLabel.setText("Lanzando…"));
            try {
                // Llama a la versión que vuelca logs a consola (si la usas)
                launchExecutor.launch(
                        session,
                        ver,
                        mcBaseDir.toFile(),
                        ram,
                        null, 0,
                        line -> Platform.runLater(() -> consoleTextArea.appendText(line + "\n")),
                        err  -> Platform.runLater(() -> consoleTextArea.appendText("[ERR] " + err + "\n"))
                );
            } catch(Exception ex){
                ex.printStackTrace();
                Platform.runLater(() -> statusLabel.setText("Error al lanzar"));
            } finally {
                // Rehabilitamos los botones cuando el proceso termine (o falle)
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

        // Preview
        preview = new ImageView();
        preview.setPreserveRatio(true);
        preview.setFitWidth(600);
        preview.setFitHeight(400);
        preview.getStyleClass().add("screenshot-preview");

        current = new SimpleObjectProperty<>();

        // Doble-click abre en Windows
        preview.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && current.get() != null) {
                try { Desktop.getDesktop().open(current.get().toFile()); }
                catch (IOException ex) { ex.printStackTrace(); }
            }
        });

        // Columnita de thumbs
        thumbsColumn = new VBox(10);
        thumbsColumn.setPadding(new Insets(10));
        thumbsColumn.getStyleClass().add("screenshot-thumbs-column");

        thumbsColumn.setStyle(null);
        // Carga inicial
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

        // Arranca el timer que refresca cada 5s
        startScreenshotsTimer(folder.toPath());
    }



    private void refreshScreenshotsOnce(Path ssDir) {
        File[] files = ssDir.toFile().listFiles((d,n)->{
            String ln = n.toLowerCase();
            return ln.endsWith(".png")||ln.endsWith(".jpg")||ln.endsWith(".jpeg");
        });
        currentScreenshots.clear();
        thumbsColumn.getChildren().clear();

        if (files == null || files.length == 0) {
            Label none = new Label("No se encontraron screenshots");
            none.getStyleClass().add("section-status");
            thumbsColumn.getChildren().add(none);
        } else {
            // Previsualizamos la primera
            currentScreenshots.clear();
            for (File f : files) currentScreenshots.add(f.toPath());
            Path first = files[0].toPath();
            current.set(first);
            preview.setImage(new Image(first.toUri().toString(), 600,0,true,true));

            // Miniaturas
            for (Path p : currentScreenshots) {
                addScreenshotThumbnail(p);
            }
        }
    }

    private void refreshScreenshots(Path ssDir) {
        // Listado actual en disco
        Set<Path> found = new HashSet<>();
        File[] files = ssDir.toFile().listFiles((d,n)->{
            String ln = n.toLowerCase();
            return ln.endsWith(".png")||ln.endsWith(".jpg");
        });
        if (files != null) for (File f : files) found.add(f.toPath());

        // 1) Nuevos
        for (Path p : found) {
            if (!currentScreenshots.contains(p)) {
                currentScreenshots.add(p);
                addScreenshotThumbnail(p);
            }
        }
        // 2) Borrados
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

            // Lanzamos un hilo demonio que vigile la carpeta
            screenshotWatcherThread = new Thread(() -> {
                try {
                    while (true) {
                        WatchKey key = screenshotWatcher.take();
                        for (WatchEvent<?> ev : key.pollEvents()) {
                            WatchEvent.Kind<?> kind = ev.kind();
                            Path filename = ((WatchEvent<Path>)ev).context();
                            Path fullPath = dir.resolve(filename);

                            // Solo png/jpg
                            String ln = filename.toString().toLowerCase();
                            if (!(ln.endsWith(".png")||ln.endsWith(".jpg"))) continue;

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
                } catch (InterruptedException ignored) {}
            }, "Screenshots-Watcher");
            screenshotWatcherThread.setDaemon(true);
            screenshotWatcherThread.start();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void addScreenshotThumbnail(Path imgPath) {
        try {
            // 1) Cargar imagen en miniatura
            Image thumbImg = new Image(
                    imgPath.toUri().toString(),
                    100,     // ancho
                    0,       // auto-height
                    true,    // preserve ratio
                    true     // smooth
            );

            // 2) Crear ImageView
            ImageView thumb = new ImageView(thumbImg);
            thumb.setPreserveRatio(true);
            thumb.setFitWidth(100);
            thumb.getStyleClass().add("screenshot-thumb");

            // 3) Envolver en un StackPane para poder pintar el fondo
            StackPane thumbContainer = new StackPane(thumb);
            thumbContainer.getStyleClass().add("screenshot-thumb-container");
            thumbContainer.setPadding(new Insets(4));
            thumbContainer.setCursor(Cursor.HAND);

            // 4) Click handler: seleccionar y actualizar preview
            thumbContainer.setOnMouseClicked(e -> {
                // a) desmarcar cualquier otro seleccionado
                thumbsColumn.getChildren().forEach(node ->
                        node.getStyleClass().remove("selected-thumb")
                );
                // b) marcar este contenedor
                thumbContainer.getStyleClass().add("selected-thumb");

                // c) actualizar la ruta actual y la vista previa
                current.set(imgPath);
                preview.setImage(new Image(
                        imgPath.toUri().toString(),
                        600, 0, true, true
                ));
            });

            // 5) Añadir al VBox de miniaturas
            thumbsColumn.getChildren().add(thumbContainer);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }


    // Llamado al borrar un archivo
    private void removeScreenshotThumbnail(Path imgPath) {
        thumbsColumn.getChildren().removeIf(node -> {
            if (node instanceof ImageView iv) {
                return iv.getImage().getUrl().equals(imgPath.toUri().toString());
            }
            return false;
        });
        // Si la que estamos viendo en preview fue borrada, limpia la vista:
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

    private static Path computeMcBaseDir(){
        String os = System.getProperty("os.name").toLowerCase();
        if(os.contains("win")){
            String appdata = System.getenv("APPDATA");
            return Paths.get(appdata!=null?appdata:System.getProperty("user.home"),".minecraft");
        } else {
            return Paths.get(System.getProperty("user.home"),".minecraft");
        }
    }


    private String getCurrentVersion() {
        try (InputStream in = getClass().getResourceAsStream("/version.properties")) {
            Properties props = new Properties();
            props.load(in);
            return props.getProperty("launcher.version", "0.0.0");
        } catch (IOException e) {
            e.printStackTrace();
            return "0.0.0";
        }
    }


    private String fetchLatestTagName() throws IOException {
        URL url = new URL("https://api.github.com/repos/TU_USUARIO/YaguaLauncher/releases/latest");
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
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

    /** Compara semánticamente dos versiones X.Y.Z */
    private boolean isNewer(String current, String latest) {
        String[] a = current.replaceFirst("^v","").split("\\.");
        String[] b = latest .replaceFirst("^v","").split("\\.");
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int ai = i < a.length ? Integer.parseInt(a[i]) : 0;
            int bi = i < b.length ? Integer.parseInt(b[i]) : 0;
            if (bi > ai) return true;
            if (bi < ai) return false;
        }
        return false;
    }
    private record ReleaseInfo(String tag, String assetUrl) {}


    private ReleaseInfo fetchLatestReleaseInfo() throws IOException {
        // 1) Conecta a GitHub API
        URL apiUrl = new URL("https://api.github.com/repos/TU_USUARIO/YaguaLauncher/releases/latest");
        HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new IOException("GitHub API responded with HTTP " + status);
        }

        // 2) Lee todo el JSON en un StringBuilder
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            conn.disconnect();
        }
        String json = sb.toString();

        // 3) Extrae el campo "tag_name"
        Pattern tagPattern = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
        Matcher tagMatcher = tagPattern.matcher(json);
        if (!tagMatcher.find()) {
            throw new IOException("No se encontró el campo tag_name en la respuesta de GitHub");
        }
        String tag = tagMatcher.group(1);

        // 4) Extrae la primera "browser_download_url" (suele apuntar al JAR)
        Pattern urlPattern = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+\\.exe)\"");
        Matcher urlMatcher = urlPattern.matcher(json);
        if (!urlMatcher.find()) {
            throw new IOException("No se encontró ningún asset .jar en la última release");
        }
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
                        scheduleReplaceAndRestart(downloadTask.getValue());
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        new Alert(Alert.AlertType.ERROR,
                                "Error al actualizar:\n" + ex.getMessage()
                        ).showAndWait();
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
        Path tmp = Files.createTempFile("YaguaLauncher-update-", ".jar");
        try (var in = url.openStream();
             var out = Files.newOutputStream(tmp,
                     StandardOpenOption.CREATE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {
            in.transferTo(out);
        }
        return tmp;
    }

    /**
     * Crea y ejecuta un .bat que:
     *  - espera a que este proceso termine
     *  - mueve el JAR descargado sobre el JAR actual
     *  - relanza el launcher
     */
    private void scheduleReplaceAndRestart(Path newExe) throws Exception {
        Path currentExe = Paths.get(getExePath());

        // Crea un script .bat temporal para reemplazar el EXE
        Path script = Files.createTempFile("update-launcher-", ".bat");
        String bat = String.join("\r\n",
                "@echo off",
                "echo Esperando que YaguaLauncher cierre…",
                "ping 127.0.0.1 -n 3 >nul",
                "move /Y \"" + newExe.toString() + "\" \"" + currentExe.toString() + "\"",
                "start \"\" \"" + currentExe.toString() + "\"",
                "exit"
        );
        Files.writeString(script, bat, StandardOpenOption.TRUNCATE_EXISTING);

        // Ejecuta el script
        new ProcessBuilder("cmd", "/C", "start", "\"\"", script.toString())
                .inheritIO()
                .start();

        // Cierra este launcher
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
        // 1. App instalada: usar JPACKAGE_APP_PATH
        String jpackagePath = System.getenv("JPACKAGE_APP_PATH");
        if (jpackagePath != null && !jpackagePath.isEmpty()) {
            return jpackagePath;
        }

        // 2. App en desarrollo (ejecutando desde IDE o gradle)
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


    private Path extraerRecursoComoArchivoTemporal(String resourcePath, String nombreArchivo) throws IOException {
        InputStream in = getClass().getResourceAsStream(resourcePath);
        if (in == null)
            throw new FileNotFoundException("No se encontró el recurso: " + resourcePath);

        Path tempFile = Files.createTempFile(nombreArchivo, null);
        Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile;
    }
}


