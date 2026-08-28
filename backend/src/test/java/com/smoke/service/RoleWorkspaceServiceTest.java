package com.smoke.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleWorkspaceServiceTest {

    private final RoleWorkspaceService service = new RoleWorkspaceService();

    @Test
    void systemAdministratorReceivesUserAndMapManagementModules() {
        var workspace = service.workspace("SYSTEM_ADMIN");

        assertTrue(workspace.modules().contains("users"));
        assertTrue(workspace.modules().contains("map"));
        assertTrue(workspace.permissions().contains("MAP_POSITION_MANAGE"));
        assertTrue(workspace.permissions().contains("USER_MANAGE"));
    }

    @Test
    void residentReceivesReadOnlyInterface() {
        var workspace = service.workspace("RESIDENT");

        assertTrue(workspace.modules().contains("map"));
        assertFalse(workspace.modules().contains("devices"));
        assertFalse(workspace.modules().contains("notifications"));
        assertTrue(workspace.permissions().contains("READ_ONLY"));
    }
}
