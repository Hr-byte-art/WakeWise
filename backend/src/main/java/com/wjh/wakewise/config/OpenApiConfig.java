package com.wjh.wakewise.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * knife4j(springdoc)OpenAPI 文档配置,入口 /doc.html。
 * 全局声明 JWT Bearer 鉴权:文档页 Authorize 填入登录返回的 token 即可调试受保护接口。
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "BearerAuth";

    @Bean
    public OpenAPI wakewiseOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("WakeWise 知晨 API")
                        .description("AI Morning Assistant 后端接口文档")
                        .version("1.0.0"))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
