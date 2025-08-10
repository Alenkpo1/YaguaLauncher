package core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VersionDetails {
    //clases internas para mapear librerias y el cliente

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Library {
        private Downloads downloads;

        public Downloads getDownloads() {return downloads;}
        public void setDownloads(Downloads downloads) { this.downloads = downloads; }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Downloads {
            private Artifact artifact;

            // ⬇️⬇️⬇️ NUEVO: mapear los classifiers (nativos)
            @JsonProperty("classifiers")
            private Map<String, Artifact> classifiers;

            public Artifact getArtifact() { return artifact; }
            public void setArtifact(Artifact artifact) { this.artifact = artifact; }

            // ⬇️⬇️⬇️ GETTER/SETTER NUEVOS
            public Map<String, Artifact> getClassifiers() { return classifiers; }
            public void setClassifiers(Map<String, Artifact> classifiers) { this.classifiers = classifiers; }

            @JsonIgnoreProperties(ignoreUnknown = true)
            public static class Artifact {
                private String url;
                private String sha1;

                public String getUrl() { return url; }
                public void setUrl(String url) { this.url = url; }
                public String getSha1() { return sha1; }
                public void setSha1(String sha1) { this.sha1 = sha1; }
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ClientDownload{
        @JsonProperty("url")
        private String url;
        @JsonProperty("sha1")
        private String sha1;
        public String getUrl() {return url;}
        public void setUrl(String url) { this.url = url; }
        public String getSha1() { return sha1; }
        public void setSha1(String sha1) { this.sha1 = sha1; }

    }
    private List<Library> libraries;

    @JsonProperty("downloads")
    private DownloadsWrapper downloads;

    public List<Library> getLibraries() {
        return libraries;
    }
    public void setLibraries(List<Library> libraries) {
        this.libraries = libraries;
    }

    public ClientDownload getClientDownload() {
        return downloads.client;

    }

    /**
     * Wrapper para la seccion de descargas del Json
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DownloadsWrapper {
        @JsonProperty("client")
        private ClientDownload client;
        public ClientDownload getClient() {return client;}
        public void setClient(ClientDownload client) { this.client = client; }
    }

    /**
     * Carga desde la URL dada (campo url de la Version del manifiesto) y parsea en un VersionDetails.
     * @param detailsUrl URL al JSON de detalles de la versión
     * @return instancia de VersionDetails con librerías y client.jar
     * @throws IOException si falla la descarga o parseo
     */
    public static VersionDetails loadFromUrl(String detailsUrl) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new URL(detailsUrl), VersionDetails.class);
    }

    @JsonProperty("assetIndex")
    private AssetIndexInfo assetIndex;
    public AssetIndexInfo getAssetIndex() { return assetIndex; }
    public void setAssetIndex(AssetIndexInfo assetIndex) { this.assetIndex = assetIndex; }

    @JsonProperty("type")
    private String type;
    public String getType() { return type; }


    /** Clase que mapea los campos de assetIndex */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetIndexInfo {
        private String id;
        private String url;
        private String sha1;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getSha1() { return sha1; }
        public void setSha1(String sha1) { this.sha1 = sha1; }
    }

    /** Mapea los objetos dentro del índice de assets */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetObject {
        private String hash;
        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }
    }

    /** Clase para representar todo el asset index cuando lo parseemos */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetIndex {
        private Map<String, AssetObject> objects;
        public Map<String, AssetObject> getObjects() { return objects; }
        public void setObjects(Map<String, AssetObject> objects) { this.objects = objects; }
    }
    /**
     * Clase auxiliar para exponer  los datos de descarga.
     */
    public static class ArtifactInfo {
        public final String url;
        public final String sha1;
        public final Path targetPath;

        public ArtifactInfo(String url, String sha1, Path targetPath) {
            this.url = url;
            this.sha1 = sha1;
            this.targetPath = targetPath;
        }
    }
}
