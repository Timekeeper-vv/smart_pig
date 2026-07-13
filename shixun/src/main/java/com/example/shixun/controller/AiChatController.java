package com.example.shixun.controller;

import com.example.shixun.service.SiliconFlowChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
@CrossOrigin(origins = "*")
public class AiChatController {
    private final SiliconFlowChatService siliconFlow;

    private static final String SYSTEM_PROMPT =
            "你是“之间味道-文创产品智能体平台”的AI业务助手，专注于文创产品业务。" +
            "你可以帮助用户做：文创选品、设计企划、营销文案、客户询盘回复、报价思路、打样流程、生产BOM、物流履约、经营分析。" +
            "回答要务实、简洁、可执行。涉及报价、合同、版权、承诺交期时必须提醒人工复核；避免建议使用未授权IP或明显模仿知名品牌/角色。";

    public AiChatController(SiliconFlowChatService siliconFlow) {
        this.siliconFlow = siliconFlow;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String userMessage = (String) body.get("message");
        if (userMessage == null || userMessage.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "消息不能为空"));
        }
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = (List<Map<String, String>>) body.getOrDefault("history", List.of());
        String historyText = history.stream()
                .limit(12)
                .map(m -> ("assistant".equals(m.get("role")) ? "助手" : "用户") + "：" + m.getOrDefault("content", ""))
                .collect(Collectors.joining("\n"));
        String prompt = (historyText.isBlank() ? "" : "历史对话：\n" + historyText + "\n\n") + "当前问题：\n" + userMessage;
        try {
            String reply = siliconFlow.chat(SYSTEM_PROMPT, prompt, 0.55, 1200, 20);
            return ResponseEntity.ok(Map.of("reply", reply, "source", "siliconflow:" + siliconFlow.modelName()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", "调用硅基流动失败：" + e.getMessage()));
        }
    }
}
