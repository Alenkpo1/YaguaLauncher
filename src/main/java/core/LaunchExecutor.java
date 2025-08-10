package core;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Ejecuta el cliente de Minecraft con los parámetros adecuados,
 * opcionalmente conectando a un servidor directamente.
 */
public class LaunchExecutor {
    private final String javaBin;
    private final String mainClass;

    public LaunchExecutor(String javaHome) {
        this.javaBin   = javaHome + File.separator + "bin" + File.separator + "java";
        this.mainClass = "net.minecraft.client.main.Main";
    }

    public void launch(AuthManager.Session session,
                       String versionId,
                       File gameDir,
                       int ramMb,
                       String serverAddress,
                       int serverPort)
            throws IOException, InterruptedException
    {
        launch(session, versionId, gameDir, ramMb,
                serverAddress, serverPort,
                System.out::println,
                line -> System.err.println("[ERR] " + line));
    }

    private static String pathFromUrl(String url) {
        // Convierte https://repo/....../a/b/c.jar  ->  a/b/c.jar
        int idx = url.indexOf("://");
        String p = (idx >= 0) ? url.substring(idx + 3) : url;
        int slash = p.indexOf('/');
        return (slash >= 0) ? p.substring(slash + 1) : p;
    }

    public void launch(AuthManager.Session session,
                       String versionId,
                       File gameDir,
                       int ramMb,
                       String serverAddress,
                       int serverPort,
                       Consumer<String> stdoutListener,
                       Consumer<String> stderrListener)
            throws IOException, InterruptedException
    {
        // 1) Detalles de versión
        VersionDetails det = new VersionManager().fetchVersionDetails(versionId);
        String assetIndexId = det.getAssetIndex().getId();
        String assetsDir    = new File(gameDir, "assets").getAbsolutePath();
        File   librariesRoot = new File(gameDir, "libraries");

        // 2) Asegurar que TODAS las librerías existan (DFU incluído)
        for (VersionDetails.Library lib : det.getLibraries()) {
            VersionDetails.Library.Downloads dls = lib.getDownloads();
            if (dls == null || dls.getArtifact() == null) continue;

            String url = dls.getArtifact().getUrl();
            String sha = dls.getArtifact().getSha1();
            if (url == null || url.isBlank()) continue;

            File libFile = new File(librariesRoot, pathFromUrlSafe(url));
            ensureFile(url, sha, libFile);
        }

        // 3) Classpath SOLO con libs de esta versión (en orden) + client.jar al final
        List<String> cp = new ArrayList<>();
        for (VersionDetails.Library lib : det.getLibraries()) {
            VersionDetails.Library.Downloads dls = lib.getDownloads();
            if (dls == null || dls.getArtifact() == null) continue;
            String url = dls.getArtifact().getUrl();
            if (url == null || url.isBlank()) continue;

            File libJar = new File(librariesRoot, pathFromUrlSafe(url));
            cp.add(libJar.getAbsolutePath());
        }
        File vJar = new File(gameDir, "versions" + File.separator + versionId + File.separator + versionId + ".jar");
        cp.add(vJar.getAbsolutePath());
        String classpath = String.join(File.pathSeparator, cp);

        // 4) Directorio de nativos: <.minecraft>\versions\<ver>\<ver>-natives
        File nativesDir = new File(gameDir,
                "versions" + File.separator + versionId + File.separator + versionId + "-natives");
        if (!nativesDir.exists()) nativesDir.mkdirs();

        // 5) Comando JVM + main + args
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-Xmx" + ramMb + "M");

        // Necesario para que LWJGL encuentre las DLLs
        cmd.add("-Djava.library.path=" + nativesDir.getAbsolutePath());
        cmd.add("-Dorg.lwjgl.librarypath=" + nativesDir.getAbsolutePath());

        // (Opcional) diagnóstico LWJGL:
        // cmd.add("-Dorg.lwjgl.util.Debug=true");
        // cmd.add("-Dorg.lwjgl.util.DebugLoader=true");

        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(mainClass);

        // 6) Flags de Minecraft
        cmd.add("--version");      cmd.add(versionId);
        String type = versionId.matches("\\d{2}w\\d{2}[a-z]") ? "snapshot" : "release";
        cmd.add("--versionType");  cmd.add(type);
        cmd.add("--gameDir");      cmd.add(gameDir.getAbsolutePath());
        cmd.add("--assetsDir");    cmd.add(assetsDir);
        cmd.add("--assetIndex");   cmd.add(assetIndexId);
        cmd.add("--uuid");         cmd.add(session.getUuid());
        // En modo offline usamos uuid como token; cambia cuando tengas auth real
        cmd.add("--accessToken");  cmd.add(session.getUuid());
        cmd.add("--userProperties"); cmd.add("{}");
        cmd.add("--userType");     cmd.add("legacy");
        cmd.add("--username");     cmd.add(session.getUsername());

        if (serverAddress != null && !serverAddress.isBlank()) {
            cmd.add("--server"); cmd.add(serverAddress);
            cmd.add("--port");   cmd.add(String.valueOf(serverPort));
        }

        cmd.add("--width");  cmd.add("854");
        cmd.add("--height"); cmd.add("480");

        // 7) Ejecutar con working dir = .minecraft (para que log4j pueda crear /logs)
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(gameDir);

        Process p = pb.start();

        // Captura stdout
        new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    stdoutListener.accept(line);
                }
            } catch (IOException ignored) {}
        }, "stdout-reader").start();

        // Captura stderr
        new Thread(() -> {
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getErrorStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    stderrListener.accept(line);
                }
            } catch (IOException ignored) {}
        }, "stderr-reader").start();

        // Esperar a que termine
        p.waitFor();
    }

    /** Añade recursivamente todos los JARs que encuentre en 'dir' */
    private void collectJars(File dir, List<String> out) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectJars(f, out);
            } else if (f.getName().endsWith(".jar")) {
                out.add(f.getAbsolutePath());
            }
        }
    }


    private static String pathFromUrlSafe(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            java.net.URI uri = new java.net.URI(url);
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

    private static void ensureFile(String url, String sha1, File target) throws IOException {
        if (target.isFile()) return;
        // crea carpetas
        File parent = target.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("No pude crear directorio: " + parent);
        }
        // descarga
        try (var in = new java.net.URL(url).openStream();
             var out = new java.io.FileOutputStream(target)) {
            in.transferTo(out);
        }
        // verificación simple sha1 (opcional; coméntala si aún no tienes util)
        if (sha1 != null && !sha1.isBlank()) {
            String actual = sha1Hex(target);
            if (!sha1.equalsIgnoreCase(actual)) {
                target.delete();
                throw new IOException("SHA1 mismatch para " + target.getName());
            }
        }
    }

    private static String sha1Hex(File f) throws IOException {
        try (var in = new java.io.FileInputStream(f)) {
            var md = java.security.MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            byte[] dig = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : dig) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
