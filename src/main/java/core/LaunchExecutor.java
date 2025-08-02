package core;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.io.*;
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

        // 2) Classpath
        List<String> cp = new ArrayList<>();
        File vJar = new File(gameDir,
                "versions" + File.separator + versionId + File.separator + versionId + ".jar");
        cp.add(vJar.getAbsolutePath());
        collectJars(new File(gameDir, "libraries"), cp);
        String classpath = String.join(File.pathSeparator, cp);

        // 3) Comando
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-Xmx" + ramMb + "M");
        cmd.add("-Djava.library.path=" +
                new File(gameDir,
                        "versions" + File.separator + versionId + File.separator + "natives")
                        .getAbsolutePath());
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(mainClass);

        // 4) Flags de Minecraft
        cmd.add("--version");      cmd.add(versionId);
        String type = versionId.matches("\\d{2}w\\d{2}[a-z]") ? "snapshot" : "release";
        cmd.add("--versionType");  cmd.add(type);
        cmd.add("--gameDir");      cmd.add(gameDir.getAbsolutePath());
        cmd.add("--assetsDir");    cmd.add(assetsDir);
        cmd.add("--assetIndex");   cmd.add(assetIndexId);
        cmd.add("--uuid");         cmd.add(session.getUuid());
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

        // 5) Ejecutar
        ProcessBuilder pb = new ProcessBuilder(cmd);
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
}
