package com.smoke.controller;

import com.smoke.entity.AlertRecord;
import com.smoke.service.AlertService;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertControllerTest {

    @Test
    void confirmUsesAuthenticatedUsernameAsOperator() {
        AlertService alertService = mock(AlertService.class);
        Authentication authentication = mock(Authentication.class);
        when(authentication.getName()).thenReturn("security-user");
        when(alertService.confirm(10L, "security-user")).thenReturn(new AlertRecord());
        AlertController controller = new AlertController(alertService);

        controller.confirm(10L, authentication);

        verify(alertService).confirm(10L, "security-user");
    }
}
