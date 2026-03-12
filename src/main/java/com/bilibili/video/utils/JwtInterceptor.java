package com.bilibili.video.utils;

import com.bilibili.video.common.Constants;
import com.bilibili.video.common.ResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;

/**
 * JWT 认证拦截器
 */
@Component
@Slf4j
public class JwtInterceptor implements HandlerInterceptor {

    private static final String HEADER_AUTH = "Authorization";
    private static final String TOKEN_PREFIX = "Bearer ";
    @Autowired
    private  RedisTemplate<String, Object> redisTemplate;

    private final JwtUtils jwtUtils;

    public JwtInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equals(request.getMethod())) {// OPTIONS 请求不进行拦截
            return true;
        }

        String path = request.getRequestURI();// 请求路径
        boolean optionalAuth = isOptionalAuthPath(path);// 是否为可选认证路径

        String authHeader = request.getHeader(HEADER_AUTH);// 获取 Authorization 头
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith(TOKEN_PREFIX)) {// 没有 Authorization 头或者 Authorization 头格式错误
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
        Object redisToken = redisTemplate.opsForValue()
                .get(Constants.loginTokenPrefix +":"+ userId);
        log.info("redisToken: " + redisToken);

        if (redisToken == null) {
            writeUnauthorized(response);
            return false;
        }
        if(!redisToken.equals(token)){
            writeUnauthorized(response);
            return false;
        }
        request.setAttribute("userId", userId);
        return true;
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
