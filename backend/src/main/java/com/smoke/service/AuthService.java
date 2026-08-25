package com.smoke.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smoke.dto.CurrentUserResponse;
import com.smoke.dto.LoginRequest;
import com.smoke.dto.LoginResponse;
import com.smoke.entity.UserAccount;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.UserAccountMapper;
import com.smoke.security.JwtService;
import com.smoke.security.UserAccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        UserAccount user = findByUsername(request.username());
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled())
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        CurrentUserResponse currentUser = toResponse(user);
        String token = jwtService.createToken(
                new UserAccountPrincipal(user.getUsername(), user.getRoleCode()),
                user.getPasswordHash());
        return new LoginResponse(token, "Bearer", currentUser);
    }

    public CurrentUserResponse currentUser(String username) {
        UserAccount user = findByUsername(username);
        if (user == null || !Integer.valueOf(1).equals(user.getEnabled())) {
            throw new BusinessException(401, "用户不存在或已禁用");
        }
        return toResponse(user);
    }

    private UserAccount findByUsername(String username) {
        return userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, username));
    }

    private CurrentUserResponse toResponse(UserAccount user) {
        return new CurrentUserResponse(
                user.getId(), user.getUsername(), user.getDisplayName(), user.getRoleCode());
    }
}
