package core;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Gestiona la lectura y listado de versiones de Minecraft desde el manifiesto oficial de Mojang,
 * y hace fallback a versiones locales (p.ej. OptiFine) leyendo <.minecraft>/versions/<id>/<id>.json.
 */
public class VersionManager {
    private static final String MANIFEST_URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest.json";

    private final ObjectMapper objectMapper;
    private final Path mcBaseDir;              // puede ser null si no querés soporte local
    private VersionManifest manifest;


    public VersionManager(Path mcBaseDir) {
        this.objectMapper = new ObjectMapper();
        this.mcBaseDir = mcBaseDir;
    }

    /** Constructor legacy: sin base dir (NO habrá fallback local). */
    public VersionManager() {
        this(null);
    }

    /** Descarga y parsea el manifiesto de versiones. */
    public void fetchManifest() throws IOException {
        manifest = objectMapper.readValue(new URL(MANIFEST_URL), VersionManifest.class);
    }



    /** Devuelve la lista completa de objetos Version tras parsear el manifiesto. */
    public List<Version> getVersions() {
        ensureManifestLoaded();
        return manifest.getVersions();
    }

    /** Devuelve solo los IDs (String) de todas las versiones del manifiesto. */
    public List<String> getVersionsIds() {
        return getVersions()
                .stream()
                .map(Version::getId)
                .collect(Collectors.toList());
    }

    /**
     * Obtiene los detalles de una versión. Intenta primero con el manifiesto remoto;
     * si no está, hace fallback a JSON local en <mcBaseDir>/versions/<id>/<id>.json.
     *
     * @param versionId ID de la versión (p.ej. "1.20.1" o "1.21.8-OptiFine_HD_U_J6_pre14")
     */
    public VersionDetails fetchVersionDetails(String versionId) throws IOException {
        // 1) Intentamos tener el manifiesto
        if (manifest == null) {
            try {
                fetchManifest();
            } catch (IOException ignored) {

            }
        }

        // 2) Existe en el manifiesto remoto
        if (manifest != null) {
            Optional<Version> match = manifest.getVersions()
                    .stream()
                    .filter(v -> v.getId().equals(versionId))
                    .findFirst();

            if (match.isPresent()) {
                // Cargar desde URL oficial de Mojang
                return VersionDetails.loadFromUrl(match.get().getUrl());
            }
        }


        if (mcBaseDir != null) {
            Path localJson = mcBaseDir
                    .resolve("versions")
                    .resolve(versionId)
                    .resolve(versionId + ".json");

            if (Files.exists(localJson)) {
                try (InputStream in = Files.newInputStream(localJson)) {
                    return objectMapper.readValue(in, VersionDetails.class);
                }
            }
        }


        throw new IllegalArgumentException("Versión no encontrada: " + versionId);
    }


    public VersionDetails resolveVersionDetails(String versionId, Path gameDir) throws IOException {
        VersionDetails d = fetchVersionDetails(versionId);


        String parentId = null;
        try {
            var m = d.getClass().getMethod("getInheritsFrom");
            Object v = m.invoke(d);
            if (v instanceof String s && !s.isBlank()) parentId = s;
        } catch (ReflectiveOperationException ignored) {

        }

        if (parentId == null) {
            return d;
        }


        VersionDetails base = resolveVersionDetails(parentId, gameDir);


        if (base.getLibraries() != null) {
            if (d.getLibraries() == null || d.getLibraries().isEmpty()) {
                d.setLibraries(base.getLibraries());
            } else {
                var merged = new java.util.ArrayList<VersionDetails.Library>(base.getLibraries());
                merged.addAll(d.getLibraries());
                d.setLibraries(merged);
            }
        }


        if (d.getAssetIndex() == null) {
            d.setAssetIndex(base.getAssetIndex());
        }


        // mainClass
        try {
            var getMain = d.getClass().getMethod("getMainClass");
            String main = (String) getMain.invoke(d);
            if (main == null || main.isBlank()) {
                var baseGetMain = base.getClass().getMethod("getMainClass");
                String baseMain = (String) baseGetMain.invoke(base);
                // setMainClass(String) es opcional: si no existe, lo ignoramos
                try {
                    var setMain = d.getClass().getMethod("setMainClass", String.class);
                    setMain.invoke(d, baseMain);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (ReflectiveOperationException ignored) {}

        // assets
        try {
            var getAssets = d.getClass().getMethod("getAssets");
            String assets = (String) getAssets.invoke(d);
            if (assets == null || assets.isBlank()) {
                var baseGetAssets = base.getClass().getMethod("getAssets");
                String baseAssets = (String) baseGetAssets.invoke(base);
                try {
                    var setAssets = d.getClass().getMethod("setAssets", String.class);
                    setAssets.invoke(d, baseAssets);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (ReflectiveOperationException ignored) {}

        // minecraftArguments (tweakClass para OptiFine/Forge)
        try {
            var getArgs = d.getClass().getMethod("getMinecraftArguments");
            String args = (String) getArgs.invoke(d);
            if (args == null || args.isBlank()) {
                var baseGetArgs = base.getClass().getMethod("getMinecraftArguments");
                String baseArgs = (String) baseGetArgs.invoke(base);
                try {
                    var setArgs = d.getClass().getMethod("setMinecraftArguments", String.class);
                    setArgs.invoke(d, baseArgs);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (ReflectiveOperationException ignored) {}

        return d;
    }

    /** Verifica que el manifiesto ya se haya cargado antes de acceder a el. */
    private void ensureManifestLoaded() {
        if (manifest == null) {
            throw new IllegalStateException("Manifest no cargado. Llama primero a fetchManifest().");
        }
    }


    //  Clases de mapeo JSON


    public static class VersionManifest {
        private Latest latest;
        private List<Version> versions;

        public Latest getLatest() { return latest; }
        public void setLatest(Latest latest) { this.latest = latest; }

        public List<Version> getVersions() { return versions; }
        public void setVersions(List<Version> versions) { this.versions = versions; }
    }

    public static class Latest {
        private String release;
        private String snapshot;

        public String getRelease() { return release; }
        public void setRelease(String release) { this.release = release; }

        public String getSnapshot() { return snapshot; }
        public void setSnapshot(String snapshot) { this.snapshot = snapshot; }
    }

    public static class Version {
        private String id;
        private String type;
        private String url;
        private String time;
        private String releaseTime;

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
