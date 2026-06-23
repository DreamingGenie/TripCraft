package com.tripcraft.member.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripcraft.member.dto.KakaoUserInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

/**
 * 카카오 OAuth 2.0 — 인가 코드로 access_token 교환 후 사용자 정보 조회.
 * 공유 {@link RestClient} 빈(TourApiConfig)을 재사용한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoOAuthService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${kakao.client-id}")
    private String clientId;

    @Value("${kakao.client-secret:}")
    private String clientSecret;

    @Value("${kakao.redirect-uri}")
    private String redirectUri;

    @Value("${kakao.token-url}")
    private String tokenUrl;

    @Value("${kakao.user-info-url}")
    private String userInfoUrl;

    /** 인가 코드 → 카카오 access_token */
    public String getKakaoAccessToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        if (clientSecret != null && !clientSecret.isBlank()) {
            form.add("client_secret", clientSecret);
        }

        try {
            String response = restClient.post()
                    .uri(URI.create(tokenUrl))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            String accessToken = objectMapper.readTree(response).path("access_token").asText(null);
            if (accessToken == null || accessToken.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "카카오 토큰 발급에 실패했습니다.");
            }
            return accessToken;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("카카오 토큰 교환 실패: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "카카오 인증에 실패했습니다.");
        }
    }

    /** access_token → 사용자 정보 (socialId, email, nickname) */
    public KakaoUserInfo getKakaoUserInfo(String accessToken) {
        try {
            String response = restClient.get()
                    .uri(URI.create(userInfoUrl))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            String socialId = root.path("id").asText(null);
            if (socialId == null || socialId.isBlank()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "카카오 사용자 식별에 실패했습니다.");
            }

            JsonNode account = root.path("kakao_account");
            String email = account.path("email").asText(null);

            String nickname = account.path("profile").path("nickname").asText(null);
            if (nickname == null || nickname.isBlank()) {
                nickname = root.path("properties").path("nickname").asText(null);
            }

            String profileImage = account.path("profile").path("profile_image_url").asText(null);
            if (profileImage == null || profileImage.isBlank()) {
                profileImage = root.path("properties").path("profile_image").asText(null);
            }

            return new KakaoUserInfo(socialId, email, nickname, profileImage);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.warn("카카오 사용자 정보 조회 실패: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "카카오 사용자 정보 조회에 실패했습니다.");
        }
    }

    /** 프로필 이미지 URL에서 바이트 다운로드 (실패 시 null — 프로필 이미지는 best-effort) */
    public DownloadedImage downloadImage(String url) {
        if (url == null || url.isBlank()) return null;
        try {
            ResponseEntity<byte[]> resp = restClient.get()
                    .uri(URI.create(url))
                    .retrieve()
                    .toEntity(byte[].class);
            String contentType = resp.getHeaders().getContentType() != null
                    ? resp.getHeaders().getContentType().toString() : null;
            if (contentType != null) {
                int semi = contentType.indexOf(';');
                if (semi > 0) contentType = contentType.substring(0, semi).trim();
            }
            return new DownloadedImage(resp.getBody(), contentType);
        } catch (Exception e) {
            log.warn("카카오 프로필 이미지 다운로드 실패: {}", e.getMessage());
            return null;
        }
    }

    public record DownloadedImage(byte[] bytes, String contentType) {}
}
