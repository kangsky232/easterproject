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

        assertEquals(List.of("monitor", "map", "devices", "notifications", "broadcasts", "users", "chat"),
                workspace.modules());
        assertEquals(List.of("ALERT_HANDLE", "BROADCAST_SEND", "BROADCAST_DELETE", "DEVICE_MANAGE",
                "MAP_POSITION_MANAGE", "USER_MANAGE"), workspace.permissions());
        assertTrue(workspace.modules().contains("users"));
        assertTrue(workspace.modules().contains("map"));
        assertTrue(workspace.permissions().contains("MAP_POSITION_MANAGE"));
        assertTrue(workspace.permissions().contains("USER_MANAGE"));
    }

    @Test
    void residentReceivesReadOnlyInterface() {
        var workspace = service.workspace("RESIDENT");

        assertEquals(List.of("monitor", "map", "chat"), workspace.modules());
        assertEquals(List.of("READ_ONLY"), workspace.permissions());
        assertTrue(workspace.modules().contains("map"));
        assertFalse(workspace.modules().contains("devices"));
        assertFalse(workspace.modules().contains("notifications"));
        assertTrue(workspace.permissions().contains("READ_ONLY"));
    }

    @Test
    void communityAdministratorReceivesOperationalManagementModulesInWorkflowOrder() {
        var workspace = service.workspace("COMMUNITY_ADMIN");

        assertEquals(List.of("monitor", "map", "devices", "notifications", "broadcasts", "chat"),
                workspace.modules());
        assertFalse(workspace.modules().contains("users"));
        assertTrue(workspace.permissions().contains("DEVICE_MANAGE"));
        assertTrue(workspace.permissions().contains("MAP_POSITION_MANAGE"));
    }

    @Test
    void firefighterReceivesResponseModulesWithoutConfigurationPages() {
        var workspace = service.workspace("FIREFIGHTER");

        assertEquals(List.of("monitor", "map", "notifications", "broadcasts", "chat"), workspace.modules());
        assertEquals(List.of("ALERT_HANDLE", "BROADCAST_SEND"), workspace.permissions());
        assertFalse(workspace.modules().contains("devices"));
        assertFalse(workspace.permissions().contains("BROADCAST_DELETE"));
    }
}
