package com.smoke.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.smoke.dto.ChangePasswordRequest;
import com.smoke.dto.CreateUserRequest;
import com.smoke.dto.PageResponse;
import com.smoke.dto.UpdateUserRequest;
import com.smoke.dto.UserResponse;
import com.smoke.entity.UserAccount;
import com.smoke.exception.BusinessException;
import com.smoke.mapper.UserAccountMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class UserService {

    private static final String SYSTEM_ADMIN = "SYSTEM_ADMIN";

    private final UserAccountMapper userAccountMapper;
    private final PasswordEncoder passwordEncoder;

    public PageResponse<UserResponse> list(int page, int pageSize, String keyword, String role, Integer enabled) {
        validatePage(page, pageSize);
        validateRole(role);
        validateEnabled(enabled);
        Page<UserAccount> result = userAccountMapper.selectPage(
                new Page<>(page, pageSize),
                Wrappers.<UserAccount>lambdaQuery()
                        .and(StringUtils.hasText(keyword), wrapper -> wrapper
                                .like(UserAccount::getUsername, keyword.trim())
                                .or()
                                .like(UserAccount::getDisplayName, keyword.trim())
                                .or()
                                .like(UserAccount::getPhone, keyword.trim()))
                        .eq(StringUtils.hasText(role), UserAccount::getRoleCode, role)
                        .eq(enabled != null, UserAccount::getEnabled, enabled)
                        .orderByAsc(UserAccount::getId));
        return new PageResponse<>(
                result.getRecords().stream().map(this::toResponse).toList(),
                result.getTotal(),
                page,
                pageSize);
    }

    public UserResponse get(Long id) {
        return toResponse(requireUser(id));
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        UserAccount user = new UserAccount();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setDisplayName(request.displayName());
        user.setRoleCode(request.role());
        user.setEnabled(1);
        user.setPhone(request.phone());
        try {
            userAccountMapper.insert(user);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(409, "用户名已存在");
        }
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateStatus(Long id, Integer enabled, String operator) {
        UserAccount user = requireUser(id);
        if (enabled == 0 && user.getUsername().equals(operator)) {
            throw new BusinessException(409, "不能禁用当前登录账号");
        }
        ensureSystemAdminRemainsAvailable(user, user.getRoleCode(), enabled);
        user.setEnabled(enabled);
        userAccountMapper.updateById(user);
        return toResponse(user);
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, String operator) {
        UserAccount user = requireUser(id);
        if (user.getUsername().equals(operator) && !user.getRoleCode().equals(request.role())) {
            throw new BusinessException(409, "不能修改当前登录账号的角色");
        }
        ensureSystemAdminRemainsAvailable(user, request.role(), user.getEnabled());
        user.setDisplayName(request.displayName().trim());
        user.setRoleCode(request.role());
        user.setPhone(normalizePhone(request.phone()));
        userAccountMapper.updateById(user);
        return toResponse(user);
    }

    @Transactional
    public void resetPassword(Long id, String password, String operator) {
        UserAccount user = requireUser(id);
        if (user.getUsername().equals(operator)) {
            throw new BusinessException(409, "请使用修改本人密码接口");
        }
        user.setPasswordHash(passwordEncoder.encode(password));
        userAccountMapper.updateById(user);
    }

    @Transactional
    public void delete(Long id, String operator) {
        UserAccount user = requireUser(id);
        if (user.getUsername().equals(operator)) {
            throw new BusinessException(409, "不能删除当前登录账号");
        }
        ensureSystemAdminRemainsAvailable(user, "", 0);
        userAccountMapper.deleteById(id);
    }

    @Transactional
    public void changeOwnPassword(String username, ChangePasswordRequest request) {
        UserAccount user = userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getUsername, username)
                .eq(UserAccount::getEnabled, 1));
        if (user == null || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException(400, "当前密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userAccountMapper.updateById(user);
    }

    private UserAccount requireUser(Long id) {
        UserAccount user = userAccountMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    private UserResponse toResponse(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getRoleCode(),
                Integer.valueOf(1).equals(user.getEnabled()),
                user.getPhone(),
                user.getCreatedAt());
    }

    private void validatePage(int page, int pageSize) {
        if (page < 1 || pageSize < 1 || pageSize > 200) {
            throw new BusinessException(400, "page 必须大于 0，pageSize 必须在 1 到 200 之间");
        }
    }

    private void validateRole(String role) {
        if (role != null && !role.isBlank()
                && !java.util.Set.of("RESIDENT", "COMMUNITY_ADMIN", SYSTEM_ADMIN, "FIREFIGHTER").contains(role)) {
            throw new BusinessException(400, "role 不合法");
        }
    }

    private void validateEnabled(Integer enabled) {
        if (enabled != null && enabled != 0 && enabled != 1) {
            throw new BusinessException(400, "enabled 仅支持 0 或 1");
        }
    }

    private void ensureSystemAdminRemainsAvailable(UserAccount user, String nextRole, Integer nextEnabled) {
        boolean removesActiveSystemAdmin = SYSTEM_ADMIN.equals(user.getRoleCode())
                && Integer.valueOf(1).equals(user.getEnabled())
                && (!SYSTEM_ADMIN.equals(nextRole) || !Integer.valueOf(1).equals(nextEnabled));
        if (!removesActiveSystemAdmin) {
            return;
        }
        Long activeSystemAdminCount = userAccountMapper.selectCount(Wrappers.<UserAccount>lambdaQuery()
                .eq(UserAccount::getRoleCode, SYSTEM_ADMIN)
                .eq(UserAccount::getEnabled, 1));
        if (activeSystemAdminCount <= 1) {
            throw new BusinessException(409, "系统至少需要保留一个启用的系统管理员");
        }
    }

    private String normalizePhone(String phone) {
        return phone == null || phone.isBlank() ? null : phone.trim();
    }
}
