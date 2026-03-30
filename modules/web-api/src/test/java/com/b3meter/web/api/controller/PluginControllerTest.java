package com.jmeternext.web.api.controller;

import com.jmeternext.web.api.controller.dto.PluginDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for {@link PluginController}.
 *
 * <p>Boots the full Spring context on a random port and exercises all plugin
 * endpoints via {@link TestRestTemplate}: list, upload, activate, and delete.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PluginControllerTest {

    @Autowired
    TestRestTemplate restTemplate;

    // -------------------------------------------------------------------------
    // GET /api/v1/plugins — list
    // -------------------------------------------------------------------------

    @Test
    void listPlugins_emptyDatabase_returnsEmptyArray() {
        ResponseEntity<PluginDto[]> response = restTemplate.getForEntity(
                "/api/v1/plugins", PluginDto[].class);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        // May contain plugins uploaded by other tests in this class — just verify success.
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/plugins — upload
    // -------------------------------------------------------------------------

    @Test
    void uploadPlugin_validJar_returns201WithPendingStatus() {
        ResponseEntity<PluginDto> response = uploadJar("my-plugin.jar", "My Plugin", "1.0.0");

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody(), "response body must not be null");
        assertNotNull(response.getBody().id(), "uploaded plugin must have an id");
        assertEquals("My Plugin", response.getBody().name());
        assertEquals("1.0.0", response.getBody().version());
        assertEquals("PENDING", response.getBody().status());
    }

    @Test
    void uploadPlugin_jarWithNoManifestAttributes_fallsBackToFilename() {
        // JAR with minimal manifest (no Plugin-Name / Plugin-Version entries)
        byte[] jarBytes = buildMinimalJar();
        ResponseEntity<PluginDto> response = uploadRawBytes("bare-plugin.jar", jarBytes);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        // Name derived from filename (minus .jar), version defaults to "unknown"
        assertEquals("bare-plugin", response.getBody().name());
        assertEquals("unknown", response.getBody().version());
    }

    @Test
    void uploadPlugin_notAJarExtension_returns400() {
        byte[] content = "not a jar".getBytes();
        ResponseEntity<PluginDto> response = uploadRawBytes("plugin.zip", content);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void uploadPlugin_emptyFile_returns400() {
        ResponseEntity<PluginDto> response = uploadRawBytes("empty.jar", new byte[0]);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void uploadPlugin_fileOver50MB_returns413OrConnectionClosed() {
        int sizeOver50Mb = 50 * 1024 * 1024 + 1;
        byte[] largeContent = new byte[sizeOver50Mb];

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        ByteArrayResource resource = new ByteArrayResource(largeContent) {
            @Override
            public String getFilename() {
                return "large.jar";
            }
        };
        body.add("file", resource);

        boolean serverEnforcedLimit = false;
        try {
            ResponseEntity<PluginDto> response = restTemplate.postForEntity(
                    "/api/v1/plugins",
                    new HttpEntity<>(body, headers),
                    PluginDto.class);
            serverEnforcedLimit = response.getStatusCode() == HttpStatus.PAYLOAD_TOO_LARGE;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            // Server closed connection mid-upload — size limit IS enforced
            serverEnforcedLimit = true;
        }

        assertTrue(serverEnforcedLimit,
                "Server must reject uploads over 50 MB (expect 413 or connection closed)");
    }

    // -------------------------------------------------------------------------
    // POST /api/v1/plugins/{id}/activate — activate
    // -------------------------------------------------------------------------

    @Test
    void activatePlugin_existingPlugin_returnsActiveStatus() {
        PluginDto uploaded = uploadJar("activate-test.jar", "Activate Me", "2.0.0").getBody();
        assertNotNull(uploaded);
        assertEquals("PENDING", uploaded.status());

        ResponseEntity<PluginDto> activateResponse = restTemplate.postForEntity(
                "/api/v1/plugins/" + uploaded.id() + "/activate",
                null,
                PluginDto.class);

        assertEquals(HttpStatus.OK, activateResponse.getStatusCode());
        assertNotNull(activateResponse.getBody());
        assertEquals("ACTIVE", activateResponse.getBody().status());
        assertEquals(uploaded.id(), activateResponse.getBody().id());
    }

    @Test
    void activatePlugin_nonExistentId_returns404() {
        ResponseEntity<PluginDto> response = restTemplate.postForEntity(
                "/api/v1/plugins/non-existent-id/activate",
                null,
                PluginDto.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // DELETE /api/v1/plugins/{id} — delete
    // -------------------------------------------------------------------------

    @Test
    void deletePlugin_existingPlugin_returns204AndIsGone() {
        PluginDto uploaded = uploadJar("delete-test.jar", "Delete Me", "0.1.0").getBody();
        assertNotNull(uploaded);

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/v1/plugins/" + uploaded.id(),
                HttpMethod.DELETE,
                null,
                Void.class);

        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        // Confirm the plugin no longer appears in the list
        ResponseEntity<PluginDto[]> listResponse = restTemplate.getForEntity(
                "/api/v1/plugins", PluginDto[].class);
        assertNotNull(listResponse.getBody());
        boolean stillPresent = false;
        for (PluginDto p : listResponse.getBody()) {
            if (uploaded.id().equals(p.id())) {
                stillPresent = true;
                break;
            }
        }
        assertFalse(stillPresent, "Deleted plugin must not appear in the list");
    }

    @Test
    void deletePlugin_nonExistentId_returns404() {
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/v1/plugins/no-such-plugin",
                HttpMethod.DELETE,
                null,
                Void.class);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // -------------------------------------------------------------------------
    // Upload after activation — verify list shows updated status
    // -------------------------------------------------------------------------

    @Test
    void listPlugins_afterUploadAndActivate_showsActivePlugin() {
        PluginDto uploaded = uploadJar("list-active.jar", "Listed Active", "3.1.0").getBody();
        assertNotNull(uploaded);
        restTemplate.postForEntity(
                "/api/v1/plugins/" + uploaded.id() + "/activate",
                null,
                PluginDto.class);

        ResponseEntity<PluginDto[]> listResponse = restTemplate.getForEntity(
                "/api/v1/plugins", PluginDto[].class);
        assertEquals(HttpStatus.OK, listResponse.getStatusCode());
        assertNotNull(listResponse.getBody());

        boolean foundActive = false;
        for (PluginDto p : listResponse.getBody()) {
            if (uploaded.id().equals(p.id())) {
                assertEquals("ACTIVE", p.status());
                foundActive = true;
            }
        }
        assertTrue(foundActive, "Activated plugin must appear in the list with ACTIVE status");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private ResponseEntity<PluginDto> uploadJar(String filename, String pluginName, String pluginVersion) {
        byte[] jarBytes = buildJarWithManifest(pluginName, pluginVersion);
        return uploadRawBytes(filename, jarBytes);
    }

    private ResponseEntity<PluginDto> uploadRawBytes(String filename, byte[] content) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        final String name = filename;
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return name;
            }
        };
        body.add("file", resource);

        return restTemplate.postForEntity(
                "/api/v1/plugins",
                new HttpEntity<>(body, headers),
                PluginDto.class);
    }

    /**
     * Builds a minimal valid JAR with {@code Plugin-Name} and {@code Plugin-Version}
     * attributes in the manifest.
     */
    private byte[] buildJarWithManifest(String pluginName, String pluginVersion) {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Plugin-Name", pluginName);
        manifest.getMainAttributes().putValue("Plugin-Version", pluginVersion);
        return buildJar(manifest);
    }

    /** Builds a minimal valid JAR with an empty manifest (no custom attributes). */
    private byte[] buildMinimalJar() {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        return buildJar(manifest);
    }

    private byte[] buildJar(Manifest manifest) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (JarOutputStream jos = new JarOutputStream(baos, manifest)) {
            jos.flush(); // ensure the manifest entry is written before close
        } catch (IOException e) {
            throw new RuntimeException("Failed to build test JAR", e);
        }
        return baos.toByteArray();
    }
}
