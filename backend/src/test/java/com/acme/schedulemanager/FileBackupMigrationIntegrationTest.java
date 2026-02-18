package com.acme.schedulemanager;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class FileBackupMigrationIntegrationTest extends IntegrationTestBase {
    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void imageUploadAndBackupAndMigration() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"img@example.com\",\"password\":\"Passw0rd!\"}")).andExpect(status().isOk());

        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"img@example.com\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String token = objectMapper.readTree(login.getResponse().getContentAsString()).get("accessToken").asText();

        var created = mvc.perform(post("/api/workspace/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"이미지항목\"}"))
                .andReturn();
        String itemId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        MockMultipartFile image = new MockMultipartFile("file", "sample.png", "image/png", "PNGDATA".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/files/upload").file(image).param("itemId", itemId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").exists());

        mvc.perform(get("/api/backup/export").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        MockMultipartFile csv = new MockMultipartFile("file", "legacy.zip", "application/zip", new byte[0]);
        mvc.perform(multipart("/api/migration/import").file(csv).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failures").isArray());
    }
}
