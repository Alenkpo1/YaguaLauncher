package core;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/**
 * Gestiona la autenticación offline, generando una sesión local con UUID.
 */
public class AuthManager {
    private static final String SESSION_FILE = ".minecraft/session.json";
    private final ObjectMapper objectMapper;

    public AuthManager() {
        // ObjectMapper para serializar/deserializar la sesión
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Genera una sesión offline a partir de un nombre de usuario.
     * Se usa UUID.nameUUIDFromBytes con el prefijo "OfflinePlayer:".
     * @param username Nombre de usuario ingresado por el jugador
     * @return objeto Session con username y uuid generados
     */
    public Session loginOffline(String username) {
        // Generar UUID consistente para modo offline
        UUID uuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes());
        Session session = new Session(username, uuid.toString());
        return session;
    }


    /**
     * Guarda la sesión en el archivo local (~/.minecraft/session.json).
     * Crea directorios padre si es necesario.
     * @param session Objeto Session a guardar
     * @throws IOException si falla la escritura
     */
    public void saveSession(Session session) throws IOException {
        Path sessionPath = Path.of(System.getProperty("user.home"), SESSION_FILE);
        Files.createDirectories(sessionPath.getParent());
        String json = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(session);
        Files.writeString(sessionPath, json, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public void clearSession() throws IOException {
        Path home = Paths.get(System.getProperty("user.home"), ".minecraft");
        Path f    = home.resolve("session.json");
        Files.deleteIfExists(f);
    }
    /**
     * Carga la sesión existente desde el archivo local.
     * @return Session cargada, o null si no existe
     * @throws IOException si falla la lectura o parseo
     */
    public Session loadSession() throws IOException {
        Path sessionPath = Path.of(System.getProperty("user.home"), SESSION_FILE);
        File file = sessionPath.toFile();
        if (!file.exists()) {
            return null;
        }
        return objectMapper.readValue(file, Session.class);
    }

    /**
     * Representa los datos de sesión de un jugador (offline).
     */
    public static class Session {
        private String username;
        private String uuid;

        // Jackson necesita constructor sin argumentos
        public Session() {}

        public Session(String username, String uuid) {
            this.username = username;
            this.uuid = uuid;
        }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getUuid() { return uuid; }
        public void setUuid(String uuid) { this.uuid = uuid; }
    }
}
