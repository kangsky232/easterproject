package com.smoke.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleWorkspaceServiceTest {

    private final RoleWorkspaceService service = new RoleWorkspaceService();

    @Test
    void systemAdministratorReceivesUserAndMapManagementModules() {
        var workspace = service.workspace("SYSTEM_ADMIN");

        assertEquals(List.of("monitor", "map", "devices", "hazards", "notifications", "broadcasts", "users", "chat"),
                workspace.modules());
        assertEquals(List.of("ALERT_HANDLE", "BROADCAST_SEND", "BROADCAST_DELETE", "DEVICE_MANAGE",
                "MAP_POSITION_MANAGE", "USER_MANAGE", "HAZARD_REPORT", "HAZARD_HANDLE", "HAZARD_REVIEW", "NOTIFICATION_AUDIT"),
                workspace.permissions());
        assertTrue(workspace.modules().contains("users"));
        assertTrue(workspace.modules().contains("map"));
        assertTrue(workspace.permissions().contains("MAP_POSITION_MANAGE"));
        assertTrue(workspace.permissions().contains("USER_MANAGE"));
        assertTrue(workspace.permissions().contains("NOTIFICATION_AUDIT"));
    }

    @Test
    void residentReceivesReadOnlyInterface() {
        var workspace = service.workspace("RESIDENT");

        assertEquals(List.of("monitor", "map", "hazards", "chat"), workspace.modules());
        assertEquals(List.of("READ_ONLY", "HAZARD_REPORT"), workspace.permissions());
        assertTrue(workspace.modules().contains("map"));
        assertFalse(workspace.modules().contains("devices"));
        assertFalse(workspace.modules().contains("notifications"));
        assertTrue(workspace.permissions().contains("READ_ONLY"));
    }

    @Test
    void communityAdministratorReceivesOperationalManagementModulesInWorkflowOrder() {
        var workspace = service.workspace("COMMUNITY_ADMIN");

        assertEquals(List.of("monitor", "map", "devices", "hazards", "notifications", "broadcasts", "chat"),
                workspace.modules());
        assertFalse(workspace.modules().contains("users"));
        assertTrue(workspace.permissions().contains("DEVICE_MANAGE"));
        assertTrue(workspace.permissions().contains("MAP_POSITION_MANAGE"));
        assertTrue(workspace.permissions().contains("NOTIFICATION_AUDIT"));
    }

    @Test
    void firefighterReceivesResponseModulesWithoutConfigurationPages() {
        var workspace = service.workspace("FIREFIGHTER");

        assertEquals(List.of("monitor", "map", "hazards", "notifications", "broadcasts", "chat"), workspace.modules());
        assertEquals(List.of("ALERT_HANDLE", "BROADCAST_SEND", "HAZARD_REPORT", "HAZARD_HANDLE", "NOTIFICATION_AUDIT"),
                workspace.permissions());
        assertFalse(workspace.modules().contains("devices"));
        assertFalse(workspace.permissions().contains("BROADCAST_DELETE"));
    }
}
