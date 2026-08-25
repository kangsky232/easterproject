package com.smoke.service;

import com.smoke.dto.LoginRequest;
import com.smoke.dto.LoginResponse;
import com.smoke.entity.UserAccount;
import com.smoke.mapper.UserAccountMapper;
import com.smoke.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserAccountMapper userAccountMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Test
    void loginReturnsBearerTokenAndUser() {
        UserAccount user = new UserAccount();
        user.setId(1L);
        user.setUsername("admin");
        user.setPasswordHash("encoded-password");
        user.setDisplayName("系统管理员");
        user.setRoleCode("SYSTEM_ADMIN");
        user.setEnabled(1);
        when(userAccountMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.matches("admin123", "encoded-password")).thenReturn(true);
        when(jwtService.createToken(any(), anyString())).thenReturn("signed-token");
        AuthService service = new AuthService(userAccountMapper, passwordEncoder, jwtService);

        LoginResponse response = service.login(new LoginRequest("admin", "admin123"));

        assertEquals("signed-token", response.token());
        assertEquals("Bearer", response.tokenType());
        assertEquals("SYSTEM_ADMIN", response.user().role());
    }
}
