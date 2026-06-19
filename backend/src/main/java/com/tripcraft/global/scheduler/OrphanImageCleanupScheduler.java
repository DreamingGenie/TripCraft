package com.tripcraft.global.scheduler;

import com.tripcraft.community.mapper.AttachMapper;
import com.tripcraft.global.service.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 게시글 등록 없이 방치된 업로드 이미지(post_draft)를 매일 새벽 3시에 정리한다.
 * 업로드 후 24시간이 지나도록 게시글에 연결되지 않은 파일을 고아(orphan)로 간주한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrphanImageCleanupScheduler {

    private final AttachMapper attachMapper;
    private final FileStorageService fileStorageService;

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void cleanupOrphanImages() {
        List<String> expiredNames = attachMapper.findExpiredDraftNames();
        if (expiredNames.isEmpty()) return;

        log.info("고아 이미지 정리 시작 — {}건", expiredNames.size());
        expiredNames.forEach(fileStorageService::deleteImage);
        attachMapper.deleteExpiredDrafts();
        log.info("고아 이미지 정리 완료");
    }
}
