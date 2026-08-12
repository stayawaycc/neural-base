package com.neuralbase.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ChatStreamHttpTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token() throws Exception {
        MvcResult r = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"admin123\"}"))
            .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("data").asText();
    }

    @Test
    void streamRequiresAuth() throws Exception {
        mvc.perform(post("/api/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"你好\"}"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void blankQuestionReturns400() throws Exception {
        String tk = token();
        mvc.perform(post("/api/chat/stream")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void streamReturnsSseFraming() throws Exception {
        String tk = token();

        // First dispatch: controller returns Flux<ServerSentEvent>; async processing starts.
        MvcResult result = mvc.perform(post("/api/chat/stream")
                .header("Authorization", "Bearer " + tk)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"你好\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        // Block until the reactive stream finishes writing the SSE body.
        // (MockMvc's asyncDispatch would re-run the security filter chain and, because
        // OncePerRequestFilter skips async dispatches by default, re-denies the already
        // committed response — so we read the body straight off the first dispatch.)
        result.getAsyncResult();

        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentType()).startsWith("text/event-stream");
        assertThat(body).contains("event:meta");
        assertThat(body).contains("data:{\"conversationId\"");
        assertThat(body).contains("event:delta\ndata:mock 回答");
        assertThat(body).contains("event:delta\ndata:片段二");
        assertThat(body).contains("event:done\ndata:");
    }
}
