package core;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Ejecuta el cliente de Minecraft en modo offline, construyendo el classpath,
 * los argumentos de JVM y de aplicación (username, version, uuid).
 */
public class LaunchExecutor {
    private final String javaBin;
    private final String mainClass;

    /**
     * @param javaHome Ruta al directorio del JDK (contiene bin/java)
     */
    public LaunchExecutor(String javaHome) {
        // Ruta al ejecutable java
        this.javaBin = javaHome + File.separator + "bin" + File.separator + "java";
        // Clase principal del cliente en versiones modernas
        this.mainClass = "net.minecraft.client.main.Main";
    }

    /**
     * Lanza el proceso de Minecraft con los parámetros dados.
     * @param session Sesión offline con username y uuid (de AuthManager.Session)
     * @param versionId ID de la versión (p.ej. "1.20.1")
     * @param gameDir Directorio .minecraft de usuario
     * @param ramMb Cantidad de memoria máxima para JVM (en MB)
     * @throws IOException          si falla la ejecución
     * @throws InterruptedException si el proceso se interrumpe
     */
    public void launch(core.AuthManager.Session session, String versionId, File gameDir, int ramMb)
            throws IOException, InterruptedException {
        // 1) Construir classpath: versión Jar + librerías
        List<String> cpEntries = new ArrayList<>();
        File versionJar = new File(gameDir,
                "versions" + File.separator + versionId + File.separator + versionId + ".jar");
        cpEntries.add(versionJar.getAbsolutePath());

        // Incluir todas las librerías bajo 'libraries'
        File librariesDir = new File(gameDir, "libraries");
        collectJars(librariesDir, cpEntries);

        String classpath = String.join(File.pathSeparator, cpEntries);

        // 2) Construir lista de argumentos para java
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin);
        cmd.add("-Xmx" + ramMb + "M");
        cmd.add("-Djava.library.path=" + new File(gameDir,
                "versions" + File.separator + versionId + File.separator + "natives").getAbsolutePath());
        cmd.add("-cp");
        cmd.add(classpath);
        cmd.add(mainClass);
        cmd.add("--username");
        cmd.add(session.getUsername());
        cmd.add("--uuid");
        cmd.add(session.getUuid());
        // Para uso offline, también es necesario un accessToken (se puede reusar el UUID)
        cmd.add("--accessToken");
        cmd.add(session.getUuid());
        cmd.add("--version");
        cmd.add(versionId);
        cmd.add("--gameDir");
        cmd.add(gameDir.getAbsolutePath());

        // 3) Ejecutar y esperar terminación
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO(); // Conecta stdin/stdout/stderr
        Process proc = pb.start();
        proc.waitFor();
    }

    /**
     * Recorre recursivamente un directorio y añade todos los .jar encontrados.
     */
    private void collectJars(File dir, List<String> collector) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectJars(f, collector);
            } else if (f.getName().endsWith(".jar")) {
                collector.add(f.getAbsolutePath());
            }
        }
    }
}
