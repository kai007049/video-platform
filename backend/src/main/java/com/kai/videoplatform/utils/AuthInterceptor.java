package com.kai.videoplatform.utils;

import com.kai.videoplatform.common.Constants;
import com.kai.videoplatform.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.io.PrintWriter;

/**
 * 认证拦截器：解析 token 并写入 UserContext
 */
@Component
@Slf4j
public class AuthInterceptor implements HandlerInterceptor {

    private static final String HEADER_AUTH = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtUtils jwtUtils;

    public AuthInterceptor(RedisTemplate<String, Object> redisTemplate, JwtUtils jwtUtils) {
        this.redisTemplate = redisTemplate;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String path = request.getRequestURI();
        boolean optionalAuth = isOptionalAuthPath(path);

        String authHeader = request.getHeader(HEADER_AUTH);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(TOKEN_PREFIX)) {
            if (optionalAuth) {
                return true;
            }
            writeUnauthorized(response);
            return false;
        }

        String token = authHeader.substring(TOKEN_PREFIX.length());
        if (!jwtUtils.validateToken(token)) {
            if (optionalAuth) {
                return true;
            }
            writeUnauthorized(response);
            return false;
        }

        Long userId = jwtUtils.getUserIdFromToken(token);
        Object redisToken = redisTemplate.opsForValue().get(Constants.loginTokenPrefix + ":" + userId);

        if (redisToken == null || !redisToken.equals(token)) {
            writeUnauthorized(response);
            return false;
        }

        UserContext.set(userId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex)
            throws Exception {
        UserContext.remove();
    }

    /** 可选认证路径：无 Token 也可访问 */
    private boolean isOptionalAuthPath(String path) {
        if ("/video/list".equals(path)) return true;
        if (path.startsWith("/video/recommended") || path.startsWith("/video/author/")) return true;
        if (path.startsWith("/user/profile/")) return true;
        if (path.startsWith("/danmu/video/")) return true;
        if (path.equals("/captcha")) return true;
        if (path.equals("/user/login") || path.equals("/user/register")) return true;
        if (path.equals("/search")) return true;
        if (path.equals("/search/users")) return true;
        if (path.startsWith("/file/")) return true;
        if (path.startsWith("/video/") && !path.equals("/video/upload")) return true;
        return false;
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        PrintWriter writer = response.getWriter();
        writer.write("{\"code\":" + ResultCode.UNAUTHORIZED + ",\"message\":\"未登录或Token已过期\"}");
        writer.flush();
    }
}