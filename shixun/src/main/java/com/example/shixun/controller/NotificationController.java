package com.example.shixun.controller;

import com.example.shixun.mapper.StatisticsMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "消息通知", description = "主动预警：存栏超容量、接种到期提醒")
public class NotificationController {

    private final StatisticsMapper mapper;

    public NotificationController(StatisticsMapper mapper) {
        this.mapper = mapper;
    }

    @GetMapping
    @Operation(summary = "获取当前活跃预警通知列表")
    public List<Map<String, Object>> getNotifications() {
        List<Map<String, Object>> notifications = new ArrayList<>();

        // 超容量圈舍预警
        List<Map<String, Object>> overcapacity = mapper.overcapacityPens();
        for (Map<String, Object> pen : overcapacity) {
            notifications.add(Map.of(
                "type", "warning",
                "category", "overcapacity",
                "title", "圈舍存栏超容",
                "message", pen.get("penName") + " 当前存栏 " + pen.get("currentCount")
                        + " 头，已达设计容量 " + pen.get("capacity") + " 头"
            ));
        }

        // 近30天未接种提醒
        List<Map<String, Object>> dueAnimals = mapper.immunizationDueAnimals();
        if (!dueAnimals.isEmpty()) {
            notifications.add(Map.of(
                "type", "info",
                "category", "immunization",
                "title", "免疫接种提醒",
                "message", "有 " + dueAnimals.size() + " 头在栏动物近30天内未进行免疫接种，请及时安排"
            ));
        }

        return notifications;
    }
}
