package com.jmeternext.web.api.controller;

import com.jmeternext.web.api.controller.dto.PluginDto;
import com.jmeternext.web.api.service.PluginService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for plugin management.
 *
 * <p>All endpoints are under {@code /api/v1/plugins}.  Upload and delete
 * are admin-only operations; when JWT authentication is active the
 * {@code SecurityConfig} enforces a {@code ROLE_ADMIN} check.  In single-user
 * desktop mode all requests are permitted.
 *
 * <p>Plugin lifecycle:
 * <pre>
 *   POST /plugins         → upload JAR  (admin only, max 50 MB) → PENDING
 *   POST /plugins/{id}/activate → promote to ACTIVE              (admin only)
 *   DELETE /plugins/{id}  → remove plugin                        (admin only)
 *   GET  /plugins         → list all installed plugins
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/plugins")
public class PluginController {

    private final PluginService service;

    public PluginController(PluginService service) {
        this.service = service;
    }

    /**
     * Returns the list of all installed plugins.
     *
     * @return 200 with the list of plugin DTOs (possibly empty)
     */
    @GetMapping
    public List<PluginDto> listPlugins() {
        return service.listAll();
    }

    /**
     * Uploads a plugin JAR.
     *
     * <p>The uploaded file must:
     * <ul>
     *   <li>have a {@code .jar} extension</li>
     *   <li>not exceed 50 MB</li>
     * </ul>
     * The plugin enters {@code PENDING} state; an admin must activate it via
     * {@code POST /plugins/{id}/activate} before the engine loads it.
     *
     * @param file the multipart JAR file; form-field name must be {@code file}
     * @return 201 Created with the new plugin DTO, 400 if the file is invalid,
     *         or 413 Payload Too Large if the file exceeds 50 MB
     */
    @PostMapping
    public ResponseEntity<PluginDto> uploadPlugin(
            @RequestParam("file") MultipartFile file) {
        try {
            PluginDto plugin = service.upload(file);
            return ResponseEntity.status(HttpStatus.CREATED).body(plugin);
        } catch (PluginService.FileTooLargeException e) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).build();
        } catch (PluginService.InvalidFileException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Permanently removes a plugin.
     *
     * <p>Deletes both the database row and the stored JAR file.
     * Admin-only: returns 403 for non-admin callers when auth is active.
     *
     * @param id the plugin identifier
     * @return 204 No Content if removed, or 404 if the plugin does not exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlugin(@PathVariable String id) {
        boolean removed = service.delete(id);
        return removed
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    /**
     * Promotes a {@code PENDING} or {@code QUARANTINED} plugin to {@code ACTIVE}.
     *
     * <p>Admin-only: returns 403 for non-admin callers when auth is active.
     *
     * @param id the plugin identifier
     * @return 200 with the updated plugin DTO, or 404 if the plugin does not exist
     */
    @PostMapping("/{id}/activate")
    public ResponseEntity<PluginDto> activatePlugin(@PathVariable String id) {
        return service.activate(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
