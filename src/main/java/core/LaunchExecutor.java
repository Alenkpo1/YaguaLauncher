package core;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * Lanza Minecraft sin conexión automática a servidor.
     */
    public void launch(AuthManager.Session session,
                       String versionId,
                       File gameDir,
                       int ramMb)
            throws IOException, InterruptedException
    {
        // Delegamos a la versión con parámetros de servidor nulos
        launch(session, versionId, gameDir, ramMb, null, 25565);
    }

    /**
     * Lanza Minecraft y, si se proporcionan, conecta automáticamente
     * al servidor indicado por host/port.
     */
    public void launch(AuthManager.Session session,
                       String versionId,
                       File gameDir,
                       int ramMb,
                       String serverAddress,
                       int serverPort)
            throws IOException, InterruptedException
    {
        // 1) Cargo detalles de la versión
        VersionDetails det = new VersionManager().fetchVersionDetails(versionId);
        String assetIndexId = det.getAssetIndex().getId();
        String assetsDir    = new File(gameDir, "assets").getAbsolutePath();

        // 2) Armo el classpath (versión Jar + todas las librerías)
        List<String> cp = new ArrayList<>();
        File vJar = new File(gameDir,
                "versions" + File.separator + versionId + File.separator + versionId + ".jar");
        cp.add(vJar.getAbsolutePath());
        collectJars(new File(gameDir, "libraries"), cp);
        String classpath = String.join(File.pathSeparator, cp);

        // 3) Construyo el comando
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
        cmd.add("--version");
        cmd.add(versionId);

        // versionType: "snapshot" o "release"
        String type = versionId.matches("\\d{2}w\\d{2}[a-z]") ? "snapshot" : "release";
        cmd.add("--versionType");
        cmd.add(type);

        cmd.add("--gameDir");
        cmd.add(gameDir.getAbsolutePath());

        cmd.add("--assetsDir");
        cmd.add(assetsDir);

        cmd.add("--assetIndex");
        cmd.add(assetIndexId);

        cmd.add("--uuid");
        cmd.add(session.getUuid());

        cmd.add("--accessToken");
        cmd.add(session.getUuid());;

        cmd.add("--userProperties");
        cmd.add("{}");

        cmd.add("--userType");
        cmd.add("legacy");

        cmd.add("--username");
        cmd.add(session.getUsername());

        // Si queremos autoconectar a un servidor
        if (serverAddress != null && !serverAddress.isBlank()) {
            cmd.add("--server");
            cmd.add(serverAddress);
            cmd.add("--port");
            cmd.add(String.valueOf(serverPort));
        }

        // Tamaño de ventana (puedes parametrizarlo si quieres)
        cmd.add("--width");
        cmd.add("854");
        cmd.add("--height");
        cmd.add("480");

        // Debug: mostrar en consola
        System.out.println("=== Minecraft CMD ===");
        System.out.println(String.join(" ", cmd));
        System.out.println("=====================");

        // 5) Ejecuto el proceso y espero a que termine
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        Process p = pb.start();
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
