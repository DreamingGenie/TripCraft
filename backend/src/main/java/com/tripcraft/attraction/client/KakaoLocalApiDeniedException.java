package com.tripcraft.attraction.client;

/**
 * 카카오 로컬 API 가 인증·권한 문제(401/403)로 요청을 거부했다.
 * 키가 틀렸거나 카카오 개발자 콘솔에서 앱의 카카오맵(로컬) 사용이 꺼져 있을 때 난다.
 * 다시 불러도 같은 결과이므로 수집을 계속하지 않고 즉시 멈춰야 한다.
 */
public class KakaoLocalApiDeniedException extends RuntimeException {
    public KakaoLocalApiDeniedException(String message) {
        super(message);
    }
}
