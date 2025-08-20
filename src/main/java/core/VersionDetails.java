package core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class VersionDetails {
    // ===== Campos para compatibilidad con OptiFine/Fabric/Forge =====
    @JsonProperty("inheritsFrom")
    private String inheritsFrom;

    @JsonProperty("assets")
    private String assets;

    @JsonProperty("mainClass")
    private String mainClass;

    public String getInheritsFrom() { return inheritsFrom; }
    public void setInheritsFrom(String inheritsFrom) { this.inheritsFrom = inheritsFrom; }

    public String getAssets() { return assets; }
    public void setAssets(String assets) { this.assets = assets; }

    public String getMainClass() { return mainClass; }
    public void setMainClass(String mainClass) { this.mainClass = mainClass; }

    // arguments
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Arguments {
        @JsonProperty("game")
        private List<Object> game;   // mezcla de String y objetos
        @JsonProperty("jvm")
        private List<Object> jvm;


    }

    @JsonProperty("arguments")
    private Arguments arguments;
    public Arguments getArguments() { return arguments; }
    public void setArguments(Arguments arguments) { this.arguments = arguments; }


    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Library {
        private Downloads downloads;

        @JsonProperty("name")
        private String name;

        @JsonProperty("url")
        private String repositoryUrl;

        public String getName() { return name; }
        public void setName(String n) { this.name = n; }
        public String getRepositoryUrl() { return repositoryUrl; }
        public void setRepositoryUrl(String u) { this.repositoryUrl = u; }
        public Downloads getDownloads() {return downloads;}
        public void setDownloads(Downloads downloads) { this.downloads = downloads; }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Downloads {
            private Artifact artifact;

            @JsonProperty("classifiers")
            private Map<String, Artifact> classifiers;

            public Artifact getArtifact() { return artifact; }
            public void setArtifact(Artifact artifact) { this.artifact = artifact; }

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

    @JsonProperty("minecraftArguments")
    private String minecraftArguments;
    public String getMinecraftArguments() { return minecraftArguments; }
    public void setMinecraftArguments(String s) { this.minecraftArguments = s; }

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

    public List<Library> getLibraries() { return libraries; }
    public void setLibraries(List<Library> libraries) { this.libraries = libraries; }

    public ClientDownload getClientDownload() {
        return (downloads != null) ? downloads.client : null;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DownloadsWrapper {
        @JsonProperty("client")
        private ClientDownload client;
        public ClientDownload getClient() {return client;}
        public void setClient(ClientDownload client) { this.client = client; }
    }

    @JsonProperty("assetIndex")
    private AssetIndexInfo assetIndex;
    public AssetIndexInfo getAssetIndex() { return assetIndex; }
    public void setAssetIndex(AssetIndexInfo assetIndex) { this.assetIndex = assetIndex; }

    @JsonProperty("type")
    private String type;
    public String getType() { return type; }

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetObject {
        private String hash;
        public String getHash() { return hash; }
        public void setHash(String hash) { this.hash = hash; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AssetIndex {
        private Map<String, AssetObject> objects;
        public Map<String, AssetObject> getObjects() { return objects; }
        public void setObjects(Map<String, AssetObject> objects) { this.objects = objects; }
    }

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

    //Helpers de carga
    public static VersionDetails loadFromUrl(String detailsUrl) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(new URL(detailsUrl), VersionDetails.class);
    }

    public static VersionDetails loadFromFile(Path jsonPath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (var in = Files.newInputStream(jsonPath)) {
            return mapper.readValue(in, VersionDetails.class);
        }
    }
}
