package com.linggong.interceptor;

import com.linggong.utils.UserHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 登录校验拦截器：检查 ThreadLocal 中是否有当前用户，没有则返回 401。
 */
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 浏览类 GET 接口放行：未登录也能浏览岗位（对齐黑马点评开放 /shop/** 查询）
        // 以及接口文档（页面 /doc.html、静态资源 /webjars、OpenAPI JSON /v3/api-docs）。
        // 写操作（POST/PUT/DELETE）不在此列，仍需登录校验
        String uri = request.getRequestURI();
        if ("GET".equalsIgnoreCase(request.getMethod())
                && (uri.startsWith("/job")
                    || uri.startsWith("/doc.html")
                    || uri.startsWith("/v3/api-docs")
                    || uri.startsWith("/webjars")
                    || uri.startsWith("/swagger-ui")
                    // 上传的图片是公开静态资源（头像/岗位图/晒单图），未登录也要能访问
                    || uri.startsWith("/uploads")
                    // /error 是 Spring 异常处理的内部转发，放行以保留真实错误码（否则会误报 401）
                    || uri.startsWith("/error"))) {
            return true;
        }
        if (UserHolder.getUser() == null) {
            // 未登录，返回 401 + 统一格式的 JSON 提示
            response.setStatus(401);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"errorMsg\":\"请先登录\"}");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束，释放 ThreadLocal，防止线程复用导致内存泄漏 / 数据串号
        UserHolder.remove();
    }
}
