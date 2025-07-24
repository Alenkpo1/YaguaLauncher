package core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Descarga archivos (librerías, assets, clientes) y verifica su integridad mediante SHA-1.
 */
public class AssetDownloader {
    // Cliente HTTP reutilizable
    private final HttpClient httpClient;

    public AssetDownloader() {
        // Inicializa el HttpClient con configuración por defecto
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Descarga un archivo desde la URL dada y lo guarda en la ruta destino.
     * Luego verifica que su SHA-1 coincida con el hash esperado.
     *
     * @param url         URL de descarga del archivo
     * @param destino     Ruta local donde guardar el archivo
     * @param expectedSha1 Hash SHA-1 esperado en formato hexadecimal
     * @throws IOException              si hay errores de E/S
     * @throws InterruptedException     si la descarga es interrumpida
     * @throws NoSuchAlgorithmException si SHA-1 no está disponible
     */
    public void downloadAndVerify(String url, Path destino, String expectedSha1)
            throws IOException, InterruptedException, NoSuchAlgorithmException {
        // Crear directorio padre si no existe
        Files.createDirectories(destino.getParent());

        // B) Construir la petición HTTP
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        //  Ejecutar la petición y guardar directamente en archivo
        HttpResponse<Path> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofFile(destino));

        if (response.statusCode() != 200) {
            throw new IOException("Error al descargar archivo: " + response.statusCode());
        }

        // D) Verificar integridad SHA-1
        if (!verifySha1(destino, expectedSha1)) {
            throw new IOException("Integridad fallida (SHA-1) para " + destino);
        }
    }

    /**
     * Calcula el hash SHA-1 de un archivo y lo compara con el hash esperado.
     *
     * @param file        Ruta del archivo a verificar
     * @param expectedSha1 Hash esperado en hexadecimal
     * @return true si coincide, false en caso contrario
     * @throws IOException              si hay errores de lectura
     * @throws NoSuchAlgorithmException si SHA-1 no está disponible
     */
    private boolean verifySha1(Path file, String expectedSha1)
            throws IOException, NoSuchAlgorithmException {
        // Inicializa el MessageDigest para SHA-1
        MessageDigest md = MessageDigest.getInstance("SHA-1");

        // Leer el archivo en bloques y actualizar digest
        try (InputStream is = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
        }

        // Convertir a cadena hexadecimal
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        String calculated = sb.toString();

        return calculated.equalsIgnoreCase(expectedSha1);
    }
}
