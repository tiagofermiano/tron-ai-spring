package com.clout.tron.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class GptService {

    @Value("${openai.api.key:}")
    private String apiKey;

    @Value("${openai.api.model:gpt-4o-mini}")
    private String model;

    private final ObjectMapper objectMapper;

    private RestClient restClient;

    private RestClient getClient() {
        if (restClient == null) {
            restClient = RestClient.builder()
                    .baseUrl("https://api.openai.com/v1/chat/completions")
                    .build();
        }
        return restClient;
    }

    /**
     * Gera um movimento usando OpenAI GPT (backup do Gemini).
     * Retorna a string bruta (UP/DOWN/LEFT/RIGHT ou qualquer coisa que o modelo mandar).
     */
    public String gerarMovimento(String prompt) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY não configurada. Pulando GPT.");
            return null;
        }

        try {
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", List.of(message));
            body.put("temperature", 0.4);

            String response = getClient()
                    .post()
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                log.warn("Resposta GPT sem choices: {}", response);
                return null;
            }

            String content = choices.get(0).get("message").get("content").asText();
            log.debug("Resposta GPT (raw): {}", content);
            return content;

        } catch (Exception e) {
            log.error("Erro ao chamar OpenAI GPT.", e);
            return null;
        }
    }
}
