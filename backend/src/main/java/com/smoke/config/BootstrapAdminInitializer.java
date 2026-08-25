package com.smoke.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smoke.entity.UserAccount;
import com.smoke.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${bootstrap-admin.enabled:true}")
    private boolean enabled;

    @Value("${bootstrap-admin.username:admin}")
    private String username;

    @Value("${bootstrap-admin.password:000000}")
    private String password;

    /**
     * Explicit local recovery switch. It is false by default and rejected by the prod validator.
     */
    @Value("${bootstrap-admin.reset-password:false}")
    private boolean resetPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        Long count = userAccountMapper.selectCount(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, username));
        if (count > 0) {
            if (resetPassword) {
                UserAccount admin = userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                        .eq(UserAccount::getUsername, username));
                admin.setPasswordHash(passwordEncoder.encode(password));
                userAccountMapper.updateById(admin);
            }
            return;
        }
        UserAccount admin = new UserAccount();
        admin.setUsername(username);
        admin.setPasswordHash(passwordEncoder.encode(password));
        admin.setDisplayName("系统管理员");
        admin.setRoleCode("SYSTEM_ADMIN");
        admin.setEnabled(1);
        userAccountMapper.insert(admin);
    }
}
