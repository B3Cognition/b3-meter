package com.jmeternext.web.api.service;

import com.jmeternext.web.api.controller.dto.PluginDto;
import com.jmeternext.web.api.repository.PluginEntity;
import com.jmeternext.web.api.repository.PluginRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.jar.JarInputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Business logic for plugin lifecycle management.
 *
 * <p>Handles upload (with MIME-type and size validation), listing, removal,
 * and activation of quarantined plugins.
 *
 * <p>Upload flow:
 * <ol>
 *   <li>Validate MIME type — must be {@code application/java-archive} or
 *       {@code application/octet-stream} (browsers differ on JAR content-type).</li>
 *   <li>Validate file extension — must end in {@code .jar}.</li>
 *   <li>Validate size — must not exceed {@link #MAX_JAR_SIZE_BYTES} (50 MB).</li>
 *   <li>Read {@code MANIFEST.MF} for {@code Plugin-Name} / {@code Plugin-Version}
 *       attributes; fall back to the original filename and {@code "unknown"} if absent.</li>
 *   <li>Save the JAR to {@link #pluginsDir} under a UUID-based filename.</li>
 *   <li>Persist a {@code PENDING} row — an admin must activate it via
 *       {@link #activate(String)}.</li>
 * </ol>
 */
@Service
public class PluginService {

    private static final Logger LOG = Logger.getLogger(PluginService.class.getName());

    /** Maximum accepted JAR size: 50 MB. */
    public static final long MAX_JAR_SIZE_BYTES = 50L * 1024 * 1024;

    /** Directory where uploaded JARs are stored. */
    private static final String DEFAULT_PLUGINS_DIR = "plugins";

    /** JAR manifest attribute for the human-readable plugin name. */
    private static final String MANIFEST_PLUGIN_NAME = "Plugin-Name";

    /** JAR manifest attribute for the plugin version string. */
    private static final String MANIFEST_PLUGIN_VERSION = "Plugin-Version";

    /** MIME types accepted for JAR uploads. */
    private static final List<String> ACCEPTED_MIME_TYPES = List.of(
            "application/java-archive",
            "application/x-java-archive",
            "application/octet-stream"
    );

    /** Default owner when none is specified in the request context. */
    private static final String DEFAULT_OWNER = "system";

    private final PluginRepository repository;
    private final Path pluginsDir;

    @Autowired
    public PluginService(PluginRepository repository) {
        this(repository, Paths.get(DEFAULT_PLUGINS_DIR));
    }

    /** Package-visible constructor for testing with a custom plugins directory. */
    PluginService(PluginRepository repository, Path pluginsDir) {
        this.repository = repository;
        this.pluginsDir = pluginsDir;
    }

    // -------------------------------------------------------------------------
    // Upload
    // -------------------------------------------------------------------------

    /**
     * Validates and stores an uploaded plugin JAR.
     *
     * <p>The returned DTO reflects the persisted {@code PENDING} state.
     * Callers should use {@link #activate(String)} to promote a plugin to
     * {@code ACTIVE} after reviewing it.
     *
     * @param file the uploaded multipart file; must be a valid JAR
     * @return the persisted plugin DTO in {@code PENDING} state
     * @throws FileTooLargeException  if the file exceeds 50 MB
     * @throws InvalidFileException   if the file is not a valid JAR
     * @throws PluginStorageException if the JAR cannot be saved to disk
     */
    public PluginDto upload(MultipartFile file) {
        validateFile(file);

        String pluginName;
        String pluginVersion;
        try (InputStream in = file.getInputStream()) {
            ManifestInfo info = readManifest(in, file.getOriginalFilename());
            pluginName    = info.name();
            pluginVersion = info.version();
        } catch (IOException e) {
            throw new InvalidFileException("Cannot read JAR file: " + e.getMessage());
        }

        String id          = UUID.randomUUID().toString();
        String storedName  = id + ".jar";
        Path   targetPath  = ensurePluginsDir().resolve(storedName);

        try {
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new PluginStorageException("Failed to save plugin JAR: " + e.getMessage());
        }

        PluginEntity entity = new PluginEntity(
                id,
                pluginName,
                pluginVersion,
                targetPath.toString(),
                PluginStatus.PENDING.name(),
                DEFAULT_OWNER,
                Instant.now()
        );
        repository.create(entity);

        LOG.info("Plugin uploaded: " + pluginName + " v" + pluginVersion + " [id=" + id + "]");
        return toDto(entity);
    }

    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    /**
     * Returns all installed plugins.
     *
     * @return list of all plugin DTOs, possibly empty
     */
    public List<PluginDto> listAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Removes a plugin by identifier.
     *
     * <p>Attempts to delete the stored JAR file; a missing file is logged but
     * does not prevent row removal.
     *
     * @param id the plugin identifier; must not be null
     * @return {@code true} if the plugin existed and was removed, {@code false} otherwise
     */
    public boolean delete(String id) {
        Optional<PluginEntity> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return false;
        }
        PluginEntity entity = existing.get();
        if (entity.jarPath() != null) {
            try {
                Files.deleteIfExists(Paths.get(entity.jarPath()));
            } catch (IOException e) {
                LOG.log(Level.WARNING, "Could not delete JAR file for plugin " + id, e);
            }
        }
        repository.delete(id);
        LOG.info("Plugin removed: " + entity.name() + " [id=" + id + "]");
        return true;
    }

    // -------------------------------------------------------------------------
    // Activate
    // -------------------------------------------------------------------------

    /**
     * Transitions a {@code PENDING} or {@code QUARANTINED} plugin to {@code ACTIVE}.
     *
     * @param id the plugin identifier; must not be null
     * @return the updated plugin DTO, or empty if the plugin does not exist
     */
    public Optional<PluginDto> activate(String id) {
        Optional<PluginEntity> existing = repository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }
        repository.updateStatus(id, PluginStatus.ACTIVE.name());
        PluginEntity updated = new PluginEntity(
                existing.get().id(),
                existing.get().name(),
                existing.get().version(),
                existing.get().jarPath(),
                PluginStatus.ACTIVE.name(),
                existing.get().installedBy(),
                existing.get().installedAt()
        );
        LOG.info("Plugin activated: " + updated.name() + " [id=" + id + "]");
        return Optional.of(toDto(updated));
    }

    // -------------------------------------------------------------------------
    // Validation helpers
    // -------------------------------------------------------------------------

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("No file provided");
        }

        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".jar")) {
            throw new InvalidFileException("Only .jar files are accepted");
        }

        String contentType = file.getContentType();
        if (contentType != null && !ACCEPTED_MIME_TYPES.contains(contentType)) {
            throw new InvalidFileException(
                    "Invalid content type: " + contentType + ". Expected a JAR file.");
        }

        if (file.getSize() > MAX_JAR_SIZE_BYTES) {
            throw new FileTooLargeException(
                    "Plugin JAR exceeds maximum size of 50 MB (got " + file.getSize() + " bytes)");
        }
    }

    private ManifestInfo readManifest(InputStream in, String originalFilename) throws IOException {
        try (JarInputStream jar = new JarInputStream(in)) {
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String name    = manifest.getMainAttributes().getValue(MANIFEST_PLUGIN_NAME);
                String version = manifest.getMainAttributes().getValue(MANIFEST_PLUGIN_VERSION);
                if (name != null && !name.isBlank()) {
                    return new ManifestInfo(
                            name.trim(),
                            version != null && !version.isBlank() ? version.trim() : "unknown"
                    );
                }
            }
        }
        // Fallback: derive name from filename, version unknown
        String baseName = originalFilename != null
                ? originalFilename.replaceAll("\\.jar$", "")
                : "unknown-plugin";
        return new ManifestInfo(baseName, "unknown");
    }

    private Path ensurePluginsDir() {
        try {
            Files.createDirectories(pluginsDir);
        } catch (IOException e) {
            throw new PluginStorageException("Cannot create plugins directory: " + e.getMessage());
        }
        return pluginsDir;
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private PluginDto toDto(PluginEntity entity) {
        return new PluginDto(
                entity.id(),
                entity.name(),
                entity.version(),
                entity.status(),
                entity.installedBy(),
                entity.installedAt()
        );
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    /** Plugin status values stored in the database. */
    public enum PluginStatus {
        PENDING, ACTIVE, QUARANTINED
    }

    /** Extracted plugin metadata from a JAR manifest. */
    private record ManifestInfo(String name, String version) {}

    /** Thrown when the uploaded file exceeds the size limit. */
    public static class FileTooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public FileTooLargeException(String message) { super(message); }
    }

    /** Thrown when the uploaded file is not a valid JAR. */
    public static class InvalidFileException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public InvalidFileException(String message) { super(message); }
    }

    /** Thrown when the JAR cannot be persisted to the file system. */
    public static class PluginStorageException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        public PluginStorageException(String message) { super(message); }
    }
}
