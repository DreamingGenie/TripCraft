package com.tripcraft.plan.client;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ODsay 오류 응답 분류 — "경로 없음"(캐시해도 됨)과 "호출 실패"(다시 물어야 함)를 가른다.
 * 코드 표: lab.odsay.com 대중교통 길찾기 가이드의 에러 코드.
 */
class OdsayClientTest {

    private final OdsayClient client = new OdsayClient();

    @ParameterizedTest
    @ValueSource(strings = {"3", "4", "5", "6", "-98", "-99"})
    void 문서의_경로없음_코드는_빈_결과(String code) {
        String body = "{\"error\":[{\"code\":\"" + code + "\",\"message\":\"no route\"}]}";
        assertThat(client.parseTransitPathResponse(body)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"500", "-8", "-9", "-1"})
    void 서버_입력_인증_오류는_호출_실패(String code) {
        String body = "{\"error\":[{\"code\":\"" + code + "\",\"message\":\"fail\"}]}";
        assertThatThrownBy(() -> client.parseTransitPathResponse(body))
                .isInstanceOf(TransitLookupException.class);
    }

    @Test
    void 객체형_오류도_코드로_분류() {
        assertThat(client.parseTransitPathResponse("{\"error\":{\"code\":-99,\"msg\":\"검색결과가 없습니다.\"}}")).isEmpty();
        assertThatThrownBy(() -> client.parseTransitPathResponse("{\"error\":{\"msg\":\"ApiKeyAuthFailed\"}}"))
                .isInstanceOf(TransitLookupException.class);
    }
}
