package com.tripcraft.global.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 공유 링크로 일정을 보는 화면이 REST 요청에 실어 보내는 공유 토큰(`X-Share-Token`).
 * 링크 공유(VIEW·EDIT)는 이 토큰이 일정의 토큰과 같을 때만 권한을 준다 — 일정 id만으로는 열리지 않는다.
 * 요청 밖(재계산 큐 등)에서는 토큰이 없다.
 */
@Component
public class RequestShareToken {

    public static final String HEADER = "X-Share-Token";

    /** 현재 HTTP 요청의 공유 토큰. 없거나 요청 밖이면 null. */
    public String current() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        HttpServletRequest request = attrs.getRequest();
        String token = request.getHeader(HEADER);
        return token == null || token.isBlank() ? null : token;
    }

    /** 상수 시간 비교 — 둘 중 하나라도 없으면 false. */
    public static boolean matches(String expected, String presented) {
        return expected != null && presented != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                                         presented.getBytes(StandardCharsets.UTF_8));
    }
}
