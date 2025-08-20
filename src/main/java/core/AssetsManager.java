package core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.Map;

/**
 * Gestiona la descarga de assets de Minecraft (sonidos, texturas, etc.).
 * Fuerza HTTP/1.1, reintenta hasta 3 veces y copia a assets/{objectKey}.
 */
public class AssetsManager {
    private static final String BASE_URL = "https://resources.download.minecraft.net/";
    private final ObjectMapper mapper;
    private final HttpClient   http;
    private final Path         assetsRoot;

    /**
     * @param assetsRoot Carpeta .minecraft/assets donde hay subcarpetas objects/ e indexes/
     */
    public AssetsManager(Path assetsRoot) {
        this.mapper = new ObjectMapper();
        this.http = HttpClient.newBuilder()
                .version(Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.assetsRoot = assetsRoot;
    }

    /** Mapea todo el índice JSON de assets */
    public static class AssetIndex {
        public Map<String, AssetObject> objects;
    }
    /** Mapea cada entrada */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetObject {
        public String hash;

        public String getHash() {
            return hash;
        }
        public void setHash(String hash) {
            this.hash = hash;
        }


    }

    /**
     * Descarga y guarda el JSON del índice en assets/indexes/{indexId}.json, luego lo parsea.
     */
    public AssetIndex fetchAssetIndex(String indexUrl, String indexId)
            throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(indexUrl)).GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IOException("Error bajando asset index: HTTP " + resp.statusCode());
        }
        String json = resp.body();

        // Guardar en disco:
        Path idxDir  = assetsRoot.resolve("indexes");
        Files.createDirectories(idxDir);
        Path idxFile = idxDir.resolve(indexId + ".json");
        Files.writeString(idxFile, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Parsear y devolver
        return mapper.readValue(json, AssetIndex.class);
    }

    /**
     * Descarga un único asset, verifica el SHA‑1, y lo copia a assets/{objectKey}.
     *
     * @param objectKey Ruta lógica en assets (p.ej. "minecraft/sounds/ambient/cave/cave1.ogg")
     * @param hash      El hash SHA‑1 del asset, usado como nombre de fichero en objects/
     */
    public void downloadSingleAsset(String objectKey, String hash) throws Exception {
        // 1) Descargar a cache: assets/objects/ab/hash
        Path objectPath = assetsRoot.resolve("objects")
                .resolve(hash.substring(0, 2))
                .resolve(hash);
        Files.createDirectories(objectPath.getParent());

        if (!(Files.exists(objectPath) && verifySha1(objectPath, hash))) {
            IOException lastEx = null;
            for (int attempt = 1; attempt <= 3; attempt++) {
                try {
                    String url = BASE_URL + hash.substring(0, 2) + "/" + hash;
                    HttpRequest r = HttpRequest.newBuilder(URI.create(url)).GET().build();
                    Path tmp = objectPath.resolveSibling(hash + ".tmp");
                    HttpResponse<Path> rp = http.send(r, HttpResponse.BodyHandlers.ofFile(tmp));
                    if (rp.statusCode() != 200) throw new IOException("HTTP " + rp.statusCode());
                    if (!verifySha1(tmp, hash)) throw new IOException("Hash mismatch para " + objectKey);
                    Files.move(tmp, objectPath, StandardCopyOption.REPLACE_EXISTING);
                    break;
                } catch (IOException ioe) {
                    lastEx = ioe;

                }
            }
            if (!Files.exists(objectPath) || lastEx != null && !verifySha1(objectPath, hash)) {
                throw new IOException("No se pudo descargar asset " + objectKey, lastEx);
            }
        }

        // 2) Copiar a assets/{objectKey} para que Minecraft lo cargue
        Path dest = assetsRoot.resolve(objectKey);
        Files.createDirectories(dest.getParent());
        Files.copy(objectPath, dest, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Verifica SHA‑1 de un archivo contra el hash esperado. */
    private boolean verifySha1(Path file, String expected) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream is = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().equalsIgnoreCase(expected);
        } catch (Exception e) {
            throw new IOException("Error verificando SHA-1", e);
        }
    }
}
