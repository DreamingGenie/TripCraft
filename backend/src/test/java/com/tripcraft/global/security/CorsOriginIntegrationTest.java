package com.tripcraft.global.security;

import com.tripcraft.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 허용 origin 을 설정하면 그 밖의 origin 은 CORS 사전 요청에서 거부되는지 확인한다.
 * (쿠키 인증 + 자격증명 허용이라, 임의 origin 을 받아 주면 다른 사이트의 스크립트가 로그인 쿠키로 API 를 부를 수 있다.)
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "cors.allowed-origins=https://app.example.com, https://admin.example.com")
class CorsOriginIntegrationTest extends AbstractIntegrationTest {

    @Autowired MockMvc mvc;

    /** 깨지면: 운영에서 origin 을 지정해도 임의 사이트의 교차 출처 요청이 허용된다. */
    @Test
    void 설정에_없는_origin의_사전_요청은_거부한다() throws Exception {
        mvc.perform(options("/api/trips")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    /** 깨지면: 설정한 서비스 origin(쉼표 뒤 공백 포함 둘째 값)까지 막혀 프런트가 API 를 못 부른다. */
    @Test
    void 설정한_origin은_자격증명과_함께_허용한다() throws Exception {
        mvc.perform(options("/api/trips")
                        .header("Origin", "https://admin.example.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://admin.example.com"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }
}
