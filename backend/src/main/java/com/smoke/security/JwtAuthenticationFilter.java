package com.smoke.security;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.smoke.entity.UserAccount;
import com.smoke.mapper.UserAccountMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserAccountMapper userAccountMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null
                && authorization.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            authenticate(authorization.substring(7), request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        try {
            Claims claims = jwtService.parse(token);
            String username = claims.getSubject();
            UserAccount user = userAccountMapper.selectOne(Wrappers.<UserAccount>lambdaQuery()
                    .eq(UserAccount::getUsername, username)
                    .eq(UserAccount::getEnabled, 1));
            if (user == null || !jwtService.credentialMatches(claims, user.getPasswordHash())) {
                SecurityContextHolder.clearContext();
                return;
            }
            var authentication = new UsernamePasswordAuthenticationToken(
                    user.getUsername(),
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_" + user.getRoleCode())));
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (JwtException | IllegalArgumentException ignored) {
            SecurityContextHolder.clearContext();
        }
    }
}
