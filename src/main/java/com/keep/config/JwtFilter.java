package com.keep.config;

import com.keep.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器
 * 将解析结果放入 request attribute 供 Controller 使用
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    public static final String ATTR_USER_ID = "userId";
    public static final String ATTR_ROLE = "userRole";

    private final JwtUtil jwtUtil;

    public JwtFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            Claims claims = jwtUtil.parseToken(token);
            if (claims != null) {
                request.setAttribute(ATTR_USER_ID, jwtUtil.getUserId(claims));
                request.setAttribute(ATTR_ROLE, jwtUtil.getRole(claims));
            }
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 公开接口不需要解析token（但也不拦截）
        return path.startsWith("/api/auth/captcha")
                || path.startsWith("/api/auth/login")
                || path.startsWith("/api/auth/register")
                || path.startsWith("/api/auth/password-reset")
                || path.equals("/api/health");
    }
}
