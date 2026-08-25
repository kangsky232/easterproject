package com.smoke.service;

import com.smoke.dto.CreateUserRequest;
import com.smoke.dto.UpdateUserRequest;
import com.smoke.dto.UserResponse;
import com.smoke.entity.UserAccount;
import com.smoke.mapper.UserAccountMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserAccountMapper userAccountMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createHashesPasswordAndDoesNotExposeHash() {
        when(passwordEncoder.encode("security123")).thenReturn("bcrypt-hash");
        UserService service = new UserService(userAccountMapper, passwordEncoder);

        UserResponse response = service.create(new CreateUserRequest(
                "security-user",
                "security123",
                "安保人员",
                "COMMUNITY_ADMIN",
                "13800000000"));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountMapper).insert(captor.capture());
        assertEquals("bcrypt-hash", captor.getValue().getPasswordHash());
        assertEquals("security-user", response.username());
        assertTrue(response.enabled());
    }

    @Test
    void updateStatusRejectsDisablingLastSystemAdmin() {
        UserAccount administrator = new UserAccount();
        administrator.setId(7L);
        administrator.setUsername("admin");
        administrator.setRoleCode("SYSTEM_ADMIN");
        administrator.setEnabled(1);
        when(userAccountMapper.selectById(7L)).thenReturn(administrator);
        when(userAccountMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        UserService service = new UserService(userAccountMapper, passwordEncoder);

        assertThrows(com.smoke.exception.BusinessException.class,
                () -> service.updateStatus(7L, 0, "another-admin"));
    }

    @Test
    void updateRetainsRoleAndNormalizesOptionalPhone() {
        UserAccount user = new UserAccount();
        user.setId(8L);
        user.setUsername("resident");
        user.setRoleCode("RESIDENT");
        user.setEnabled(1);
        when(userAccountMapper.selectById(8L)).thenReturn(user);
        UserService service = new UserService(userAccountMapper, passwordEncoder);

        UserResponse response = service.update(8L,
                new UpdateUserRequest(" 居民用户 ", "RESIDENT", " 13800000000 "), "admin");

        verify(userAccountMapper).updateById(user);
        assertEquals("居民用户", response.displayName());
        assertEquals("13800000000", response.phone());
    }

    @Test
    void deleteRejectsRemovingLastEnabledSystemAdmin() {
        UserAccount administrator = new UserAccount();
        administrator.setId(7L);
        administrator.setUsername("admin");
        administrator.setRoleCode("SYSTEM_ADMIN");
        administrator.setEnabled(1);
        when(userAccountMapper.selectById(7L)).thenReturn(administrator);
        when(userAccountMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(1L);
        UserService service = new UserService(userAccountMapper, passwordEncoder);

        assertThrows(com.smoke.exception.BusinessException.class,
                () -> service.delete(7L, "another-admin"));
    }

    @Test
    void deleteRemovesAnotherUser() {
        UserAccount user = new UserAccount();
        user.setId(8L);
        user.setUsername("legacy-admin");
        user.setRoleCode("SYSTEM_ADMIN");
        user.setEnabled(1);
        when(userAccountMapper.selectById(8L)).thenReturn(user);
        when(userAccountMapper.selectCount(org.mockito.ArgumentMatchers.any())).thenReturn(2L);
        UserService service = new UserService(userAccountMapper, passwordEncoder);

        service.delete(8L, "current-admin");

        verify(userAccountMapper).deleteById(8L);
    }
}
