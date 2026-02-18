package com.acme.schedulemanager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class AuthFlowIntegrationTest extends IntegrationTestBase {
    @Autowired
    MockMvc mvc;

    @Test
    void registerLoginRefreshLogout() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk());

        var login = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.com\",\"password\":\"Passw0rd!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn();

        String cookies = String.join(";", login.getResponse().getHeaders("Set-Cookie"));
        String csrf = login.getResponse().getHeaders("Set-Cookie").stream().filter(c -> c.startsWith("csrf_token")).findFirst().orElse("").split(";")[0].split("=")[1];

        mvc.perform(post("/api/auth/refresh")
                        .header("Cookie", cookies)
                        .header("X-CSRF-TOKEN", csrf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
    }

    @Test
    void unauthorizedBlocked() throws Exception {
        mvc.perform(get("/api/workspace/items")).andExpect(status().isForbidden());
    }
}
