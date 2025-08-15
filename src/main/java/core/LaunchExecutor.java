package core;

import java.io.*;
import java.net.URI;
import java.util.*;
import java.util.function.Consumer;

public class LaunchExecutor {
    private static final String DEFAULT_LIB_REPO = "https://libraries.minecraft.net/";
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

        // --------- Cargar detalles (herencia + local si es posible) ---------
        VersionDetails det;
        VersionManager vm = (injectedVm != null) ? injectedVm : new VersionManager();
        try {
            det = vm.resolveVersionDetails(versionId, gameDir.toPath());
        } catch (Throwable ignore) {
            det = vm.fetchVersionDetails(versionId);
        }

        // --------- Rutas base ---------
        String assetsDir     = new File(gameDir, "assets").getAbsolutePath();
        File   librariesRoot = new File(gameDir, "libraries");
        File   versionDir    = new File(gameDir, "versions" + File.separator + versionId);
        if (!versionDir.isDirectory()) versionDir.mkdirs();

        // --------- mainClass (del JSON si existe; si no, vanilla) ---------
        String jsonMainClass = (det.getMainClass() != null && !det.getMainClass().isBlank())
                ? det.getMainClass()
                : "net.minecraft.client.main.Main";

        // --------- assetIndex o assets (fallback) ---------
        String assetIndexId = (det.getAssetIndex() != null) ? det.getAssetIndex().getId() : null;
        if (assetIndexId == null) {
            assetIndexId = (det.getAssets() != null && !det.getAssets().isBlank())
                    ? det.getAssets()
                    : "legacy";
        }

        // --------- Classpath: librerías + client.jar ---------
        List<String> cp = new ArrayList<>();

        for (VersionDetails.Library lib : det.getLibraries()) {
            VersionDetails.Library.Downloads dls = lib.getDownloads();

            // Caso 1: URL directa
            if (dls != null && dls.getArtifact() != null && dls.getArtifact().getUrl() != null) {
                String url = dls.getArtifact().getUrl();
                File libFile = new File(librariesRoot, pathFromUrlSafe(url));
                if (!libFile.isFile()) {
                    ensureParent(libFile);
                    try (var in = new java.net.URL(url).openStream();
                         var out = new FileOutputStream(libFile)) {
                        in.transferTo(out);
                    } catch (IOException ex) {
                        // algunas coord. vendrán por "name"
                    }
                }
                if (libFile.isFile()) cp.add(libFile.getAbsolutePath());
                continue;
            }

            // Caso 2: coordenada maven "group:artifact:version" (+ repo opcional)
            if (lib.getName() != null && !lib.getName().isBlank()) {
                File libFile = fileFromMavenCoord(librariesRoot, lib.getName());
                if (!libFile.isFile()) {
                    String base = (lib.getRepositoryUrl() != null && !lib.getRepositoryUrl().isBlank())
                            ? lib.getRepositoryUrl()
                            : DEFAULT_LIB_REPO;
                    if (!base.endsWith("/")) base += "/";
                    String relPath = mavenPathFromCoord(lib.getName());
                    String fullUrl = base + relPath;

                    try {
                        ensureParent(libFile);
                        try (var in = new java.net.URL(fullUrl).openStream();
                             var out = new FileOutputStream(libFile)) {
                            in.transferTo(out);
                        }
                    } catch (IOException ignore2) {
                        // quizás ya esté con otro nombre; lo omitimos si no está
                    }
                }
                if (libFile.isFile()) cp.add(libFile.getAbsolutePath());
            }
        }

        // client jar al final
        File clientJar = new File(versionDir, versionId + ".jar");
        if (!clientJar.isFile()) {
            if (det.getClientDownload() != null && det.getClientDownload().getUrl() != null) {
                ensureParent(clientJar);
                try (var in = new java.net.URL(det.getClientDownload().getUrl()).openStream();
                     var out = new FileOutputStream(clientJar)) {
                    in.transferTo(out);
                }
            }
        }
        if (clientJar.isFile()) cp.add(clientJar.getAbsolutePath());

        String classpath = String.join(File.pathSeparator, cp);

        // --------- Nativos ---------
        File nativesDir = new File(versionDir, versionId + "-natives");
        if (!nativesDir.exists()) nativesDir.mkdirs();

        // --------- TWEAKS (legacy "minecraftArguments") ---------
        List<String> extra = new ArrayList<>();
        boolean hasTweaks = false;

        if (det.getMinecraftArguments() != null && !det.getMinecraftArguments().isBlank()) {
            extra.addAll(splitArgsAndSubstitute(
                    det.getMinecraftArguments(),
                    buildVarsMap(session, versionId, gameDir, assetsDir, assetIndexId, nativesDir)
            ));
        }

        // ¿hay --tweakClass ... ?
        for (int i = 0; i < extra.size(); i++) {
            String tok = extra.get(i);
            if ("--tweakClass".equals(tok) || tok.startsWith("--tweakClass=")) {
                hasTweaks = true;
                break;
            }
        }

        // Si el JSON ya pone LaunchWrapper como mainClass, pero NO hay --tweakClass,
        // agregamos el de OptiFine si detectamos launchwrapper-of en libraries.
        boolean usingLaunchWrapperFromJson = jsonMainClass.startsWith("net.minecraft.launchwrapper");
        boolean hasOFLaunchwrapper = containsLaunchwrapperOf(librariesRoot);

        if (usingLaunchWrapperFromJson && !hasTweaks) {
            if (hasOFLaunchwrapper) {
                extra.add("--tweakClass");
                extra.add("optifine.OptiFineTweaker");
                hasTweaks = true;
            } else {
                // último recurso para compat: VanillaTweaker (por si estuviera el launchwrapper clásico)
                extra.add("--tweakClass");
                extra.add("net.minecraft.launchwrapper.VanillaTweaker");
                hasTweaks = true;
            }
        }

        // Si está el fork de OptiFine, quitamos VanillaTweaker para evitar el CNF
        if (hasOFLaunchwrapper && !extra.isEmpty()) {
            // pares "--tweakClass" <valor>
            for (int i = 0; i < extra.size() - 1; ) {
                if ("--tweakClass".equals(extra.get(i))
                        && "net.minecraft.launchwrapper.VanillaTweaker".equals(extra.get(i + 1))) {
                    extra.remove(i + 1);
                    extra.remove(i);
                } else {
                    i++;
                }
            }
            // variante en un solo token
            extra.removeIf(s ->
                    "net.minecraft.launchwrapper.VanillaTweaker".equals(s)
                            || s.equals("--tweakClass=net.minecraft.launchwrapper.VanillaTweaker"));
        }

        // main class efectiva: si hay tweakers, aseguramos LaunchWrapper
        String effectiveMainClass = jsonMainClass;
        if (hasTweaks && !effectiveMainClass.startsWith("net.minecraft.launchwrapper")) {
            effectiveMainClass = "net.minecraft.launchwrapper.Launch";
        }

        // --------- Comando ---------
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-Xmx" + ramMb + "M");
        cmd.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
        cmd.add("-Dorg.lwjgl.librarypath=" + nativesDir.getAbsolutePath());
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(effectiveMainClass);

        // Args básicos de Minecraft
        cmd.add("--version");      cmd.add(versionId);
        String type = (det.getType() != null && !det.getType().isBlank()) ? det.getType()
                : (versionId.matches("\\d{2}w\\d{2}[a-z]") ? "snapshot" : "release");
        cmd.add("--versionType");  cmd.add(type);
        cmd.add("--gameDir");      cmd.add(gameDir.getAbsolutePath());
        cmd.add("--assetsDir");    cmd.add(assetsDir);
        cmd.add("--assetIndex");   cmd.add(assetIndexId);
        cmd.add("--uuid");         cmd.add(session.getUuid());
        cmd.add("--accessToken");  cmd.add(session.getUuid()); // offline
        cmd.add("--userProperties"); cmd.add("{}");
        cmd.add("--userType");     cmd.add("legacy");
        cmd.add("--username");     cmd.add(session.getUsername());

        if (serverAddress != null && !serverAddress.isBlank()) {
            cmd.add("--server"); cmd.add(serverAddress);
            cmd.add("--port");   cmd.add(String.valueOf(serverPort));
        }

        cmd.add("--width");  cmd.add("854");
        cmd.add("--height"); cmd.add("480");

        // Añadimos los tweaks finales
        if (!extra.isEmpty()) cmd.addAll(extra);

        // --------- Ejecutar ---------
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

    /** ¿Existe el jar de OptiFine launchwrapper-of en libraries? */
    private static boolean containsLaunchwrapperOf(File librariesRoot) {
        File dir = new File(librariesRoot, "optifine/launchwrapper-of");
        if (!dir.isDirectory()) return false;
        File[] vers = dir.listFiles(File::isDirectory);
        if (vers == null) return false;
        for (File v : vers) {
            File[] jars = v.listFiles((d, name) -> name.startsWith("launchwrapper-of-") && name.endsWith(".jar"));
            if (jars != null && jars.length > 0) return true;
        }
        return false;
    }
}
