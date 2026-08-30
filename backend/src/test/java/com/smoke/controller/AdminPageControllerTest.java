package com.smoke.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdminPageControllerTest {

    @Test
    void adminEntryForwardsWithoutChangingThePublicScheme() {
        assertEquals("forward:/admin/index.html", new AdminPageController().adminPage());
    }
}
