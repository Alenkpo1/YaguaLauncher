package core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


/*
 * Gestiona la lectura y listado de versiones de Minecraft desde el manifiesto oficial de Mojang.
 */
public class VersionManager {
    private static final String MANIFEST_URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest.json";
    private final ObjectMapper objectMapper;
    private VersionManifest manifest;

    public VersionManager() {
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Descarga y parsea el manifesto de versiones
     * @throws IOException si falla la descarga o el parseo
     */

    public void fetchManifest() throws IOException {
        manifest = objectMapper.readValue(new URL(MANIFEST_URL), VersionManifest.class);
    }

    /**
     * Devuelve la versión release más reciente
     */

    public String getLatestRealeaseId(){

        ensureManifestLoaded();
        return manifest.getLatest().getRelease();
    }

    /**
     * Devuelve la lista completa de objetos Version tras parsear el manifiesto.
     * @return lista de Version
     */

    public List<Version> getVersions() {
        ensureManifestLoaded();
        return manifest.getVersions();
    }

    /**
     * Devuelve solo los IDs (String) de todas las versiones.
     * @return lista de IDs de versión
     */

    public List<String> getVersionsIds(){
        return getVersions()
                .stream()
                .map(Version::getId)
                .collect(Collectors.toList());
    }

    /**
     * Descarga y parsea los detalles de la versión especificada.
     * @param versionId ID de la versión (p.ej. "1.20.1")
     * @return VersionDetails con librerías y cliente
     * @throws IOException si falla la descarga o el parseo
     */
    public VersionDetails fetchVersionDetails(String versionId) throws IOException {
        // Aseguramos que el manifiesto esté cargado
        if (manifest == null) {
            fetchManifest();
        }

        // Buscamos la Version con el ID dado
        Optional<Version> match = manifest.getVersions()
                .stream()
                .filter(v -> v.getId().equals(versionId))
                .findFirst();

        Version version = match.orElseThrow(
                () -> new IllegalArgumentException("Versión no encontrada: " + versionId)
        );

        // Cargamos y devolvemos los detalles desde la URL del JSON de versión
        return VersionDetails.loadFromUrl(version.getUrl());
    }


    /**
     * Verifica que el manifiesto ya se haya cargado antes de acceder a él.
     */
    private void ensureManifestLoaded() {
        if (manifest == null) {
            throw new IllegalStateException("Manifest no cargado. Llama primero a fetchManifest().");
        }
    }


    // Clases internas para mapear el JSON del Manifest

    public static class VersionManifest {
        private Latest latest;
        private List<Version> versions;

        public Latest getLatest(){ return latest; }
        public void setLatest(Latest latest){this.latest = latest;}

        public List<Version> getVersions(){ return versions; }
        public void setVersions(List<Version> versions){this.versions = versions;}


    }

    public static class Latest {
        private String release;
        private String snapshot;

        public String getRelease(){ return release; }
        public void setRelease(String release){this.release = release;}

        public String getSnapshot(){ return snapshot; }
        public void setSnapshot(String snapshot){this.snapshot = snapshot;}
    }

    public static class Version {
        private String id;
        private String type;
        private String url;
        private String time;
        private String releaseTime;

        // getters y setters
        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public String getReleaseTime() { return releaseTime; }
        public void setReleaseTime(String releaseTime) { this.releaseTime = releaseTime; }
    }


}
