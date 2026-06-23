package com.tripcraft.member.service;

import com.tripcraft.global.attach.mapper.AttachMapper;
import com.tripcraft.global.storage.FileStorageService;
import com.tripcraft.member.domain.Member;
import com.tripcraft.member.mapper.MemberMapper;
import com.tripcraft.plan.mapper.TripBlockMapper;
import com.tripcraft.plan.mapper.TripCandidateMapper;
import com.tripcraft.plan.mapper.TripMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {

    private static final List<String> FILE_TARGETS = List.of("profile", "post_draft");

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final TripBlockMapper tripBlockMapper;
    private final TripCandidateMapper tripCandidateMapper;
    private final TripMapper tripMapper;
    private final AttachMapper attachMapper;
    private final FileStorageService fileStorageService;
    private final KakaoOAuthService kakaoOAuthService;

    /**
     * 회원 탈퇴.
     *
     * DB 삭제 순서:
     *   1. attach 레코드 (profile·post_draft) — FK 없으므로 수동
     *   2. trip_block → trip_candidate → trip (RESTRICT FK 역순)
     *   3. member (CASCADE: member_token, favorite, post_like, post_bookmark)
     *      SET NULL: post.member_id, post_comment.member_id, notice.member_id
     *
     * 파일 삭제는 트랜잭션 커밋 후 실행.
     * DB 롤백 시 파일이 먼저 지워지는 불일치를 방지하기 위함.
     * 커밋 후 파일 삭제 실패 시 고아 파일이 남지만 DB 정합성은 보장된다.
     */
    @Override
    @Transactional
    public void withdraw(Long memberId, String password) {
        Member member = memberMapper.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "회원을 찾을 수 없습니다."));

        // 일반 계정은 비밀번호 확인. 소셜 전용 계정(password=null)은 JWT 인증으로 신원 확인되므로 생략.
        if (member.getPassword() != null
                && (password == null || !passwordEncoder.matches(password, member.getPassword()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비밀번호가 올바르지 않습니다.");
        }

        // 카카오 연결 해제 (best-effort — 실패해도 탈퇴는 진행)
        if ("kakao".equals(member.getSocialProvider()) && member.getSocialId() != null) {
            kakaoOAuthService.unlink(member.getSocialId());
        }

        // 커밋 후 삭제할 파일 경로 수집 (트랜잭션 안에서 조회, 삭제는 커밋 이후)
        List<String> pathsToDelete = FILE_TARGETS.stream()
                .flatMap(target -> attachMapper.findByTarget(target, memberId).stream())
                .map(a -> a.getHostPath())
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        // attach 레코드 삭제
        FILE_TARGETS.forEach(target -> attachMapper.deleteByTarget(target, memberId));

        // 일정 관련 데이터: RESTRICT FK로 인해 역순 삭제 필수
        tripBlockMapper.deleteByMemberId(memberId);
        tripCandidateMapper.deleteByMemberId(memberId);
        tripMapper.deleteByMemberId(memberId);

        // member 삭제
        memberMapper.deleteById(memberId);

        // 트랜잭션 커밋 후 파일 삭제 등록
        registerFileCleanup(pathsToDelete);
    }

    private void registerFileCleanup(List<String> paths) {
        if (paths.isEmpty()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                paths.forEach(path -> {
                    try {
                        fileStorageService.delete(path);
                    } catch (Exception ignored) {
                        // 파일 삭제 실패 → 고아 파일로 남음. DB 정합성은 이미 보장.
                        // 실운영이라면 별도 모니터링 또는 정리 배치가 필요한 지점.
                    }
                });
            }
        });
    }
}
