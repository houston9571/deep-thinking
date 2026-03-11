package com.deepthinking.ext.filter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import com.deepthinking.common.utils.StringUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.springframework.http.HttpHeaders.*;


@Slf4j
@Component
public class CorsFilter implements Filter {

    private final String[] suspiciousPatterns = {"site", "map", "uppercheck", "test", "scan"};
    private final String[] script = {"bash", ".", "redirect", "url", "referer", "http", "systemd", "shell", "cd+", "rm+", "wget", "curl", "chmod", "sh+", "<script", "alert(", "iframe", "grant", "drop"};

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String uri = request.getRequestURI().toLowerCase();
        // 如果 URI 包含可疑特征，直接返回 404
        if (StrUtil.containsAny(uri, suspiciousPatterns)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            log.error("URI包含可疑特征: url:{} ", uri);
            return;
        }
        if (StrUtil.containsAny(uri, script)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            log.error("攻击过滤: url:{} ", uri);
            return;
        }

        String query = request.getQueryString();
        query = StrUtil.isNotEmpty(query) ? query.toLowerCase() : "";
        if (StrUtil.containsAny(query, script)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            log.error("攻击过滤: url:{} param:{}", uri, query);
            return;
        }
        response.setHeader(ACCESS_CONTROL_ALLOW_ORIGIN, request.getHeader(ORIGIN));
        response.setHeader(ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        response.setHeader(ACCESS_CONTROL_ALLOW_METHODS, "POST, GET, DELETE, PUT, OPTIONS");
        response.setHeader(ACCESS_CONTROL_ALLOW_HEADERS, CONTENT_TYPE + "," + ACCEPT_LANGUAGE);
        response.setHeader(ACCESS_CONTROL_MAX_AGE, "3600");
        response.setHeader(CACHE_CONTROL, "no-cache, no-store, must-revalidate");

        if (RequestMethod.OPTIONS.name().equals(request.getMethod())) {
            response.setStatus(HttpStatus.HTTP_OK);
        } else {
            chain.doFilter(request, response);
        }
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
