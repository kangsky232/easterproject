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
                    "查看全局态势，管理设备、地图位置、隐患闭环、广播、通知和系统账号。",
                    List.of("monitor", "map", "devices", "hazards", "notifications", "broadcasts", "users", "chat"),
                    List.of("ALERT_HANDLE", "BROADCAST_SEND", "BROADCAST_DELETE", "DEVICE_MANAGE", "MAP_POSITION_MANAGE", "USER_MANAGE",
                            "HAZARD_REPORT", "HAZARD_HANDLE", "HAZARD_REVIEW", "NOTIFICATION_AUDIT"));
            case "COMMUNITY_ADMIN" -> response(
                    roleCode, "小区管理员", "小区消防安全工作台",
                    "负责设备接入、社区态势监控、告警处置、隐患复核和日常安全广播。",
                    List.of("monitor", "map", "devices", "hazards", "notifications", "broadcasts", "chat"),
                    List.of("ALERT_HANDLE", "BROADCAST_SEND", "BROADCAST_DELETE", "DEVICE_MANAGE", "MAP_POSITION_MANAGE",
                            "HAZARD_REPORT", "HAZARD_HANDLE", "HAZARD_REVIEW", "NOTIFICATION_AUDIT"));
            case "FIREFIGHTER" -> response(
                    roleCode, "消防员", "火情应急处置工作台",
                    "聚焦活动告警、3D 空间定位、隐患整改、通知追踪和应急广播，不开放设备与账号配置。",
                    List.of("monitor", "map", "hazards", "notifications", "broadcasts", "chat"),
                    List.of("ALERT_HANDLE", "BROADCAST_SEND", "HAZARD_REPORT", "HAZARD_HANDLE", "NOTIFICATION_AUDIT"));
            default -> response(
                    "RESIDENT", "居民", "我的居住安全",
                    "查看社区设备状态和 3D 位置，可上报安全隐患并跟踪整改结果。",
                    List.of("monitor", "map", "hazards", "chat"),
                    List.of("READ_ONLY", "HAZARD_REPORT"));
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
