package com.neuralbase.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import jakarta.servlet.http.Cookie;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class CookieAuthTest {

    @Autowired
    private MockMvc mvc;

    private String loginAndExtractCookie() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk())
            .andReturn();
        String setCookie = r.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).contains("nb_token=");
        return setCookie.substring(setCookie.indexOf("nb_token=") + "nb_token=".length(),
            setCookie.indexOf(";"));
    }

    @Test
    void meWithCookieOnlyReturnsUser() throws Exception {
        String token = loginAndExtractCookie();
        mvc.perform(get("/api/auth/me").cookie(new Cookie("nb_token", token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void protectedEndpointWithInvalidCookieReturns401() throws Exception {
        mvc.perform(get("/api/kb").cookie(new Cookie("nb_token", "invalid-token")))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void headerStillTakesPrecedence() throws Exception {
        String token = loginAndExtractCookie();
        mvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .cookie(new Cookie("nb_token", "stale-but-ignored")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.username").value("admin"));
    }
}
