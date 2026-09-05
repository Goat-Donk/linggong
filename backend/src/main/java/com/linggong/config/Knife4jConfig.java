package com.linggong.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / springdoc 接口文档配置。
 *
 * <p>文档入口 {@code /doc.html}（已由 {@link MvcConfig} 放行登录校验）。
 * 除「登录/验证码」和「岗位浏览类 GET」外，其余接口需登录，
 * 这里注册一个全局的 {@code authorization} 请求头鉴权，
 * 让文档页右上角出现「Authorize」按钮，填入登录返回的 token 即可在线调试。
 */
@Configuration
public class Knife4jConfig {

    /** 安全方案名称，需与 {@code addSecurityItem} 保持一致 */
    private static final String SECURITY_SCHEME = "authorization";

    @Bean
    public OpenAPI linggongOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("本地零工平台 API")
                        .description("本地零工平台后端接口文档：雇主发岗位、打工人报名/做单/互评。\n"
                                + "除「发送验证码 / 登录」和「岗位浏览类 GET」外，其余接口需先登录，"
                                + "点右上角「Authorize」填入 token 后再调试。")
                        .version("v1.0.0")
                        .contact(new Contact().name("linggong")))
                // 全局请求头鉴权：header 名 authorization，值为登录返回的 token
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("authorization")
                                .description("登录接口返回的 token（Redis 存的就是这个 token，直接填 token 本身，不带 Bearer 前缀）")));
    }
}
