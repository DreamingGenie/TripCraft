package com.tripcraft.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * springdoc OpenAPI 설정.
 *
 * <p>인증은 로그인 시 발급되는 {@code access_token} HttpOnly 쿠키를 사용한다. Swagger UI의
 * "Try it out"은 브라우저가 보유한 쿠키를 자동 전송하므로, 먼저 {@code POST /api/auth/login}을
 * 호출해 로그인하면 이후 보호된 엔드포인트도 그대로 호출된다. (아래 보안 스킴은 문서 표기용)
 */
@Configuration
public class OpenApiConfig {

    private static final String COOKIE_AUTH = "cookieAuth";

    @Bean
    public OpenAPI tripCraftOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("TripCraft API")
                        .description("관광지 탐색·여행 일정·이동시간 자동 계산·실시간 협업·커뮤니티 REST/WebSocket API")
                        .version("v1.0"))
                .components(new Components()
                        .addSecuritySchemes(COOKIE_AUTH, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("access_token")
                                .description("로그인 시 발급되는 HttpOnly 쿠키. 브라우저가 자동 전송한다.")));
    }
}
