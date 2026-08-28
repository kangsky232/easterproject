package com.smoke.dto;

import java.util.List;

public record RoleWorkspaceResponse(
        String roleCode,
        String roleLabel,
        String homeTitle,
        String description,
        List<String> modules,
        List<String> permissions) {
}
