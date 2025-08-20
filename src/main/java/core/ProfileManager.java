package core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

public class ProfileManager {
    private static final String PROFILES_FILE = "profiles.json";

    /** Representa un perfil de lanzamiento */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Profile {
        private String name;
        private String versionId;
        private int    ramMb;


        public Profile() {}

        public Profile(String name, String versionId, int ramMb) {
            this.name = name;
            this.versionId = versionId;
            this.ramMb = ramMb;
        }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getVersionId() { return versionId; }
        public void setVersionId(String versionId) { this.versionId = versionId; }
        public int getRamMb() { return ramMb; }
        public void setRamMb(int ramMb) { this.ramMb = ramMb; }
    }

    private final Path file;
    private final ObjectMapper mapper = new ObjectMapper();
    private Map<String, Profile> profiles = new LinkedHashMap<>();

    public ProfileManager(Path mcBaseDir) {
        this.file = mcBaseDir.resolve(PROFILES_FILE);
        load();
    }

    /** Carga perfiles desde disk, o deja vacío si no existe */
    public void load() {
        try {
            if (Files.exists(file)) {
                profiles = mapper.readValue(file.toFile(),
                        mapper.getTypeFactory()
                                .constructMapType(LinkedHashMap.class, String.class, Profile.class));
            }
        } catch (IOException e) {
            e.printStackTrace();
            profiles = new LinkedHashMap<>();
        }
    }

    /** Guarda el map de perfiles en JSON */
    public void save() {
        try {
            Files.createDirectories(file.getParent());
            mapper.writerWithDefaultPrettyPrinter()
                    .writeValue(file.toFile(), profiles);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** El mapa ordenado de perfiles (clave = profile name) */
    public Map<String, Profile> getProfiles() {
        return profiles;
    }

    public void addOrUpdate(Profile p) {
        profiles.put(p.getName(), p);
        save();
    }

    public void remove(String name) {
        profiles.remove(name);
        save();
    }
}
