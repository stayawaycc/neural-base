package com.neuralbase.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Transactional
class KbControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        JsonNode node = objectMapper.readTree(r.getResponse().getContentAsString());
        return node.get("data").asText();
    }

    @Test
    void kbCrudRoundtrip() throws Exception {
        // create
        MvcResult create = mvc.perform(post("/api/kb")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Java 面试\",\"description\":\"八股文合集\",\"color\":\"#fbbf24\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").isNumber())
            .andExpect(jsonPath("$.data.name").value("Java 面试"))
            .andReturn();
        long id = objectMapper.readTree(create.getResponse().getContentAsString())
            .get("data").get("id").asLong();

        // list contains created
        mvc.perform(get("/api/kb").header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[?(@.name=='Java 面试')]").exists());

        // update
        mvc.perform(put("/api/kb/{id}", id)
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Java 面试 v2\",\"description\":\"更新\",\"color\":\"#22d3ee\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Java 面试 v2"));

        // delete, then get returns empty
        mvc.perform(delete("/api/kb/{id}", id)
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk());
        mvc.perform(get("/api/kb/{id}", id)
                .header("Authorization", "Bearer " + token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").doesNotExist());
    }
}
