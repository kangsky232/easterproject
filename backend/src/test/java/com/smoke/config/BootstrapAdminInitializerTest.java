package com.smoke.config;

import com.smoke.entity.UserAccount;
import com.smoke.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BootstrapAdminInitializerTest {

    @Mock
    private UserAccountMapper userAccountMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void explicitRecoverySwitchResetsExistingBootstrapAccountPassword() throws Exception {
        BootstrapAdminInitializer initializer = new BootstrapAdminInitializer(userAccountMapper, passwordEncoder);
        ReflectionTestUtils.setField(initializer, "enabled", true);
        ReflectionTestUtils.setField(initializer, "username", "local-admin");
        ReflectionTestUtils.setField(initializer, "password", "new-password");
        ReflectionTestUtils.setField(initializer, "resetPassword", true);
        UserAccount user = new UserAccount();
        user.setId(7L);
        user.setUsername("local-admin");
        user.setPasswordHash("old-hash");
        when(userAccountMapper.selectCount(any())).thenReturn(1L);
        when(userAccountMapper.selectOne(any())).thenReturn(user);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        initializer.run(new DefaultApplicationArguments());

        assertEquals("new-hash", user.getPasswordHash());
        verify(userAccountMapper).updateById(user);
    }
}
