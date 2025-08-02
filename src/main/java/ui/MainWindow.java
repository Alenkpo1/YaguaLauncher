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
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
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
import java.net.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;



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

    // — Login UI
    private TextField usernameField;
    private Button    loginButton;
    private Label     loginStatusLabel;

    // — Navegación lateral
    private ToggleButton navHome, navProfiles, navVersions, navLaunch;

    // — Panes de contenido
    private VBox homePane, profilesPane, versionsPane, launchPane;

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

    // — Controles sección Lanzamiento
    private TextField ramField;
    private Button    launchButton;
    private Label  serverLabel;
    private Label  pingLabel;


    public static void main(String[] args) {
        launch();
    }

    @Override
    public void start(Stage stage) {
        // Carga opcional de fuente
        stage.initStyle(StageStyle.UNDECORATED);

        var fontUrl = getClass().getResource("/ui/fonts/CeraPro-Regular.otf");
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
        stage.setHeight(480);
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
        usernameField    = new TextField();
        usernameField.setPromptText("Usuario offline");
        loginButton      = new Button("Login Offline");
        loginStatusLabel = new Label();
        loginStatusLabel.setStyle("-fx-text-fill: tomato;");

        VBox overlay = new VBox(15,
                createTitleLabel("YaguaLauncher"),
                usernameField,
                loginButton,
                loginStatusLabel
        );
        overlay.setPadding(new Insets(30));
        overlay.setMaxWidth(300);
        overlay.setStyle(
                "-fx-background-color: rgba(0,0,0,0.6);" +
                        "-fx-border-color: rgba(255,255,255,0.3); -fx-border-width:1;"
        );
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
            ((Stage) loginButton.getScene().getWindow()).setScene(mainScene);
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

        return new Scene(root, 1024, 576);
    }

    private double xOffset, yOffset;
    private Scene buildMainScene(Stage stage) {
        // 1) Botones laterales
        navHome     = makeNavButton("/ui/icons/home.png");
        navProfiles = makeNavButton("/ui/icons/user.png");
        navVersions = makeNavButton("/ui/icons/versions.png");
        navLaunch   = makeNavButton("/ui/icons/play.png");
        ToggleGroup navGroup = new ToggleGroup();
        for (var tb : List.of(navHome, navProfiles, navVersions, navLaunch)) {
            tb.setToggleGroup(navGroup);
            tb.getStyleClass().add("nav-button");
        }
        navHome.setSelected(true);

        // Marco lateral (nav-bar)
        VBox navBar = new VBox(20, navHome, navProfiles, navVersions, navLaunch);
        navBar.setPadding(new Insets(20));
        navBar.getStyleClass().add("nav-bar");

        // 2) Paneles de cada sección
        buildHomePane();      // donde está tu botón JUGAR
        buildProfilesPane();
        buildVersionsPane();
        buildLaunchPane();

        StackPane content = new StackPane(homePane, profilesPane, versionsPane, launchPane);
        showOnly(homePane);
        navHome    .setOnAction(e -> showOnly(homePane));
        navProfiles.setOnAction(e -> showOnly(profilesPane));
        navVersions.setOnAction(e -> showOnly(versionsPane));
        navLaunch  .setOnAction(e -> showOnly(launchPane));

        // 3) Construcción del BorderPane principal
        BorderPane root = new BorderPane();
        root.getStyleClass().add("root");
        root.setLeft(navBar);
        navBar.prefHeightProperty().bind(root.heightProperty());
        root.setCenter(content);

        // ==== Barra de título custom ====
        Label titleLabel = new Label("YaguaLauncher");
        titleLabel.setTextFill(Color.WHITE);
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        Button btnMin = new Button("-");
        btnMin.getStyleClass().add("window-button");
        btnMin.setOnAction(e -> stage.setIconified(true));

        Button btnMax = new Button("▢");
        btnMax.getStyleClass().add("window-button");
        btnMax.setOnAction(e -> stage.setMaximized(!stage.isMaximized()));

        Button btnClose = new Button("×");
        btnClose.getStyleClass().addAll("window-button", "window-close");
        btnClose.setOnAction(e -> stage.close());

        HBox windowControls = new HBox(5, btnMin, btnMax, btnClose);
        windowControls.setAlignment(Pos.CENTER_RIGHT);

        Region dragSpacer = new Region();
        HBox.setHgrow(dragSpacer, Priority.ALWAYS);

        HBox titleBar = new HBox(10, titleLabel, dragSpacer, windowControls);
        titleBar.setPadding(new Insets(5, 10, 5, 10));
        titleBar.getStyleClass().add("window-title-bar");
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

        // ==== Indicador de servidor ====
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
        serverBox.setAlignment(Pos.CENTER);

        // ==== Encabezado combinado ====
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(titleBar, headerSpacer, serverBox);
        header.setAlignment(Pos.CENTER_LEFT);

        root.setTop(header);

        // 4) Crear scena y aplicar CSS
        Scene scene = new Scene(root, 854, 480);
        scene.getStylesheets().add(getClass().getResource("/ui/styles.css").toExternalForm());

        // 5) Iniciar ping periódico
        Timeline pingTimer = new Timeline(
                new KeyFrame(Duration.ZERO,    e -> pingServer()),
                new KeyFrame(Duration.seconds(5))
        );
        pingTimer.setCycleCount(Animation.INDEFINITE);
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

        Button bigPlay = new Button("JUGAR");
        bigPlay.getStyleClass().add("big-play-button");
        bigPlay.setOnAction(e -> {
            String ver = versionCombo.getValue();
            if (ver == null) {
                new Alert(Alert.AlertType.WARNING,
                        "Selecciona primero una versión en la pestaña Versiones")
                        .showAndWait();
                return;
            }
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
        Label h = new Label("Lanzamiento");
        h.getStyleClass().add("section-header");
        ramField     = new TextField("1024");
        ramField.setPrefWidth(100);
        launchButton = new Button("Lanzar Minecraft");
        HBox row = new HBox(8, new Label("RAM (MB):"), ramField, launchButton);
        row.getStyleClass().add("section-row");

        launchPane = new VBox(12, h, row);
        launchPane.setPadding(new Insets(20));
        launchPane.getStyleClass().add("section-pane");

        launchButton.setOnAction(e->launchGame());
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
        lbl.setFont(Font.font("FSP DEMO - Cera Pro",24));
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
            return;
        }
        new Thread(() -> {
            Platform.runLater(() -> statusLabel.setText("Lanzando…"));
            try {
                launchExecutor.launch(session, ver, mcBaseDir.toFile(), ram);
            } catch(Exception ex){
                ex.printStackTrace();
                Platform.runLater(() -> statusLabel.setText("Error al lanzar"));
            }
        }).start();
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
}
