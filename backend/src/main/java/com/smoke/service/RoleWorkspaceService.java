package com.smoke.service;

import com.smoke.dto.RoleWorkspaceResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleWorkspaceService {

    public RoleWorkspaceResponse workspace(String roleCode) {
        return switch (roleCode) {
            case "SYSTEM_ADMIN" -> response(
                    roleCode, "系统管理员", "系统运行与安全治理",
                    "查看全局态势，管理设备、地图位置、广播、通知和系统账号。",
                    List.of("monitor", "map", "devices", "notifications", "broadcasts", "users", "chat"),
                    List.of("ALERT_HANDLE", "BROADCAST_SEND", "BROADCAST_DELETE", "DEVICE_MANAGE", "MAP_POSITION_MANAGE", "USER_MANAGE"));
            case "COMMUNITY_ADMIN" -> response(
                    roleCode, "小区管理员", "小区消防安全工作台",
                    "负责设备接入、社区态势监控、告警处置和日常安全广播。",
                    List.of("monitor", "map", "devices", "notifications", "broadcasts", "chat"),
                    List.of("ALERT_HANDLE", "BROADCAST_SEND", "BROADCAST_DELETE", "DEVICE_MANAGE", "MAP_POSITION_MANAGE"));
            case "FIREFIGHTER" -> response(
                    roleCode, "消防员", "火情应急处置工作台",
                    "聚焦活动告警、3D 空间定位、通知追踪和应急广播，不开放设备与账号配置。",
                    List.of("monitor", "map", "notifications", "broadcasts", "chat"),
                    List.of("ALERT_HANDLE", "BROADCAST_SEND"));
            default -> response(
                    "RESIDENT", "居民", "我的居住安全",
                    "以只读方式查看社区设备状态、3D 位置和安全问答，不开放管理与处置操作。",
                    List.of("monitor", "map", "chat"),
                    List.of("READ_ONLY"));
        };
    }

    private RoleWorkspaceResponse response(
            String roleCode,
            String roleLabel,
            String homeTitle,
            String description,
            List<String> modules,
            List<String> permissions) {
        return new RoleWorkspaceResponse(roleCode, roleLabel, homeTitle, description, modules, permissions);
    }
}
