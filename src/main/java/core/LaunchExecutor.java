package core;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

public class LaunchExecutor {
    private final String javaBin;
    private final VersionManager injectedVm;

    public LaunchExecutor(String javaHome, VersionManager injectedVm) {
        this.javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        this.injectedVm = injectedVm;
    }

    public void launch(AuthManager.Session session,
                       String versionId,
                       File gameDir,
                       int ramMb,
                       String serverAddress,
                       int serverPort) throws IOException, InterruptedException {
        launch(session, versionId, gameDir, ramMb, serverAddress, serverPort,
                System.out::println, l -> System.err.println("[ERR] " + l));
    }

    public void launch(AuthManager.Session session,
                       String versionId,
                       File gameDir,
                       int ramMb,
                       String serverAddress,
                       int serverPort,
                       Consumer<String> stdoutListener,
                       Consumer<String> stderrListener) throws IOException, InterruptedException {

        // 1) Cargar detalles (con fallback local para OptiFine/Fabric/etc.)
        VersionDetails det;
        VersionManager vm = (injectedVm != null) ? injectedVm : new VersionManager(gameDir.toPath());
        try {
            det = vm.resolveVersionDetails(versionId, gameDir.toPath());
        } catch (Throwable ignore) {
            det = vm.fetchVersionDetails(versionId);
        }

        // 2) Rutas base
        String assetsDir = new File(gameDir, "assets").getAbsolutePath();
        File librariesRoot = new File(gameDir, "libraries");
        File versionDir = new File(gameDir, "versions" + File.separator + versionId);
        if (!versionDir.isDirectory()) versionDir.mkdirs();

        // 3) mainClass (del JSON si existe; si no, vanilla)
        String mainClass = (det.getMainClass() != null && !det.getMainClass().isBlank())
                ? det.getMainClass()
                : "net.minecraft.client.main.Main";

        // 4) assetIndex o assets (fallback)
        String assetIndexId = (det.getAssetIndex() != null) ? det.getAssetIndex().getId() : null;
        if (assetIndexId == null) {
            assetIndexId = (det.getAssets() != null && !det.getAssets().isBlank())
                    ? det.getAssets()
                    : "legacy";
        }

        // 5) Armar classpath con TODAS las libs + client.jar
        List<String> cp = new ArrayList<>();

        for (VersionDetails.Library lib : det.getLibraries()) {
            VersionDetails.Library.Downloads dls = lib.getDownloads();
            if (dls != null && dls.getArtifact() != null && dls.getArtifact().getUrl() != null) {
                String url = dls.getArtifact().getUrl();
                File libFile = new File(librariesRoot, pathFromUrlSafe(url));
                if (!libFile.isFile()) {
                    ensureParent(libFile);
                    try (var in = new java.net.URL(url).openStream();
                         var out = new FileOutputStream(libFile)) {
                        in.transferTo(out);
                    } catch (IOException ex) {
                        // puede venir por "name" + repo alternativo; lo intentamos abajo
                    }
                }
                if (libFile.isFile()) cp.add(libFile.getAbsolutePath());
            } else if (lib.getName() != null) {
                // coordenada maven "group:artifact:version"
                File libFile = fileFromMavenCoord(librariesRoot, lib.getName());
                if (libFile.isFile()) {
                    cp.add(libFile.getAbsolutePath());
                } else if (lib.getRepositoryUrl() != null && !lib.getRepositoryUrl().isBlank()) {
                    // intentar descarga desde repo base
                    String relPath = mavenPathFromCoord(lib.getName());
                    String base = lib.getRepositoryUrl();
                    if (!base.endsWith("/")) base += "/";
                    String full = base + relPath;
                    try {
                        ensureParent(libFile);
                        try (var in = new java.net.URL(full).openStream();
                             var out = new FileOutputStream(libFile)) {
                            in.transferTo(out);
                        }
                        if (libFile.isFile()) cp.add(libFile.getAbsolutePath());
                    } catch (IOException ignore2) {
                        // omitimos si no está
                    }
                }
            }
        }

        // client.jar
        File clientJar = new File(versionDir, versionId + ".jar");
        if (!clientJar.isFile() && det.getClientDownload() != null && det.getClientDownload().getUrl() != null) {
            ensureParent(clientJar);
            try (var in = new java.net.URL(det.getClientDownload().getUrl()).openStream();
                 var out = new FileOutputStream(clientJar)) {
                in.transferTo(out);
            }
        }
        if (clientJar.isFile()) cp.add(clientJar.getAbsolutePath());

        String classpath = String.join(File.pathSeparator, cp);

        // 6) Nativos
        File nativesDir = new File(versionDir, versionId + "-natives");
        if (!nativesDir.exists()) nativesDir.mkdirs();

        // 7) Comando JVM + main
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-Xmx" + ramMb + "M");
        cmd.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
        cmd.add("-Dorg.lwjgl.librarypath=" + nativesDir.getAbsolutePath());
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(mainClass);

        // 8) ¿JSON legacy (minecraftArguments) o moderno?
        boolean legacyArgs = det.getMinecraftArguments() != null
                && !det.getMinecraftArguments().isBlank();

        if (!legacyArgs) {
            // ---- MODO MODERNO (vanilla 1.13+): agregamos nuestros args base
            cmd.add("--version");      cmd.add(versionId);
            String type = (det.getType()!=null && !det.getType().isBlank())
                    ? det.getType()
                    : (versionId.matches("\\d{2}w\\d{2}[a-z]") ? "snapshot" : "release");
            cmd.add("--versionType");  cmd.add(type);
            cmd.add("--gameDir");      cmd.add(gameDir.getAbsolutePath());
            cmd.add("--assetsDir");    cmd.add(assetsDir);
            cmd.add("--assetIndex");   cmd.add(assetIndexId != null ? assetIndexId : "legacy");
            cmd.add("--uuid");         cmd.add(session.getUuid());
            cmd.add("--accessToken");  cmd.add(session.getUuid()); // offline
            cmd.add("--userProperties"); cmd.add("{}");
            cmd.add("--userType");     cmd.add("legacy");
            cmd.add("--username");     cmd.add(session.getUsername());
        } else {
            // ---- MODO LEGACY (OptiFine/Forge viejos): usar minecraftArguments del JSON
            Map<String, String> vars = buildVarsMap(session, versionId, gameDir, assetsDir,
                    assetIndexId != null ? assetIndexId : "legacy", nativesDir);
            List<String> extra = splitArgsAndSubstitute(det.getMinecraftArguments(), vars);
            cmd.addAll(extra);
        }

        // Auto-conectar y tamaño de ventana (los sumamos en ambos modos)
        if (serverAddress != null && !serverAddress.isBlank()) {
            cmd.add("--server"); cmd.add(serverAddress);
            cmd.add("--port");   cmd.add(String.valueOf(serverPort));
        }
        cmd.add("--width");  cmd.add("854");
        cmd.add("--height"); cmd.add("480");

        // (Opcional) ver el comando final para diagnósticos
        // System.out.println("=== CMD ===\n" + String.join(" ", cmd) + "\n===========");

        // 9) Ejecutar
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDir);

        Process p = pb.start();

        // stdout
        new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) stdoutListener.accept(line);
            } catch (IOException ignored) {}
        }, "mc-stdout").start();

        // stderr
        new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = r.readLine()) != null) stderrListener.accept(line);
            } catch (IOException ignored) {}
        }, "mc-stderr").start();

        p.waitFor();
    }

    // ================== helpers ==================

    private static void ensureParent(File f) throws IOException {
        File parent = f.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("No pude crear " + parent);
        }
    }

    private static String pathFromUrlSafe(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            URI uri = new URI(url);
            String path = uri.getPath();
            if (path == null) return "";
            if (path.startsWith("/")) path = path.substring(1);
            return java.nio.file.Paths.get(path).normalize().toString();
        } catch (Exception ignore) {
            int idx = url.indexOf("://");
            String p = (idx >= 0) ? url.substring(idx + 3) : url;
            int slash = p.indexOf('/');
            String onlyPath = (slash >= 0) ? p.substring(slash + 1) : p;
            return java.nio.file.Paths.get(onlyPath).normalize().toString();
        }
    }

    // "group:artifact:version" -> <libs>/<group/as/path>/<artifact>/<version>/<artifact>-<version>.jar
    private static File fileFromMavenCoord(File librariesRoot, String coord) {
        String rel = mavenPathFromCoord(coord);
        return new File(librariesRoot, rel);
    }

    private static String mavenPathFromCoord(String coord) {
        String[] parts = coord.split(":");
        if (parts.length < 3) return coord;
        String g = parts[0].replace('.', '/');
        String a = parts[1];
        String v = parts[2];
        return g + "/" + a + "/" + v + "/" + a + "-" + v + ".jar";
    }

    private static Map<String, String> buildVarsMap(AuthManager.Session session,
                                                    String versionId,
                                                    File gameDir,
                                                    String assetsDir,
                                                    String assetsIndex,
                                                    File nativesDir) {
        Map<String, String> m = new HashMap<>();
        m.put("${auth_player_name}", session.getUsername());
        m.put("${version_name}", versionId);
        m.put("${game_directory}", gameDir.getAbsolutePath());
        m.put("${assets_root}", assetsDir);
        m.put("${assets_index_name}", assetsIndex != null ? assetsIndex : "legacy");
        m.put("${auth_uuid}", session.getUuid());
        m.put("${auth_access_token}", session.getUuid());
        m.put("${user_type}", "legacy");
        m.put("${user_properties}", "{}");
        m.put("${natives_directory}", nativesDir.getAbsolutePath());
        m.put("${launcher_name}", "YaguaLauncher");
        m.put("${launcher_version}", "1.0");
        return m;
    }

    private static List<String> splitArgsAndSubstitute(String args, Map<String, String> vars) {
        List<String> out = new ArrayList<>();
        for (String tok : args.trim().split("\\s+")) {
            String v = tok;
            for (var e : vars.entrySet()) v = v.replace(e.getKey(), e.getValue());
            out.add(v);
        }
        return out;
    }
}
