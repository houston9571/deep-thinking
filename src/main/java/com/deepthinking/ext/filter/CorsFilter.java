package com.deepthinking.ext.filter;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpStatus;
import com.deepthinking.common.utils.IPUtils;
import com.deepthinking.common.utils.StringUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.apache.http.HttpStatus.SC_NOT_FOUND;
import static org.springframework.http.HttpHeaders.*;


@Slf4j
@Component
public class CorsFilter implements Filter {

    private final String[] suspiciousPatterns = {"app", "bin", "site", "map", "check", "test", "scan"};
    private final String[] script = {"bash", "redirect", "url", "referer", "http", "systemd", "shell", "cd+", "rm+", "wget", "curl", "chmod", "sh+", "<script", "alert(", "iframe", "grant", "drop"};

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String uri = request.getRequestURI().toLowerCase();

        try {
            String query = request.getQueryString();
            query = StrUtil.isNotEmpty(query) ? query.toLowerCase() : "";
            if (StrUtil.containsAny(uri, script) || StrUtil.containsAny(query, script)) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                log.error("URI攻击过滤: {} param:{}", uri, query);
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
        } catch (NoResourceFoundException e) {
            response.setStatus(SC_NOT_FOUND);
            log.error("URI包含可疑特征: {} 返回{} {}", uri, SC_NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("URI过滤未知错误: {} {}", uri, e.getMessage());
        }

    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void destroy() {
    }
}
