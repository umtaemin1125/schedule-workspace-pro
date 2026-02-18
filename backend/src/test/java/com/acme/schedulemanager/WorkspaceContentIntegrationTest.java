package com.acme.schedulemanager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class WorkspaceContentIntegrationTest extends IntegrationTestBase {
    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void workspaceCrudAndBlockSaveLoad() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"owner@example.com\",\"nickname\":\"오너\",\"password\":\"Passw0rd!\"}")).andExpect(status().isOk());

        var login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"owner@example.com\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(login.getResponse().getContentAsString());
        String token = node.get("accessToken").asText();

        var created = mvc.perform(post("/api/workspace/items")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"항목1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andReturn();
        String itemId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(put("/api/content/" + itemId + "/blocks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blocks\":[{\"sortOrder\":0,\"type\":\"paragraph\",\"content\":\"{\\\"text\\\":\\\"본문\\\"}\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks[0].type").value("paragraph"));

        mvc.perform(get("/api/content/" + itemId + "/blocks").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocks[0].content").exists());
    }
}
