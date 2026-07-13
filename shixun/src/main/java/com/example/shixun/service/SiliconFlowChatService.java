package com.example.shixun.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SiliconFlowChatService {
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    @Value("${siliconflow.api.key:}")
    private String apiKey;

    @Value("${siliconflow.chat.model:Qwen/Qwen3-32B}")
    private String model;

    public SiliconFlowChatService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String modelName() {
        return model;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.trim().isEmpty() && !apiKey.contains("YOUR_");
    }

    public String chat(String system, String user) throws Exception {
        return chat(system, user, 0.55, 1200, 20);
    }

    public String chat(String system, String user, double temperature, int maxTokens, int timeoutSeconds) throws Exception {
        if (!configured()) throw new IllegalStateException("未配置 siliconflow.api.key");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("temperature", temperature);
        payload.put("max_tokens", maxTokens);
        payload.put("enable_thinking", false);
        payload.put("messages", List.of(
                Map.of("role", "system", "content", system == null ? "" : system),
                Map.of("role", "user", "content", user == null ? "" : user)
        ));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.siliconflow.cn/v1/chat/completions"))
                .header("Authorization", "Bearer " + apiKey.trim())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("SiliconFlow Chat HTTP " + response.statusCode() + ": " + response.body());
        }
        JsonNode root = mapper.readTree(response.body());
        String content = root.path("choices").path(0).path("message").path("content").asText("");
        if (content.isBlank()) throw new IllegalStateException("SiliconFlow返回内容为空：" + response.body());
        return content;
    }
}
