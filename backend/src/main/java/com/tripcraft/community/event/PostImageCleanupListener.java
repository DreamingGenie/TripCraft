package com.tripcraft.community.event;

import com.tripcraft.global.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class PostImageCleanupListener {

    private final FileStorageService fileStorageService;

    /**
     * 게시글 삭제 트랜잭션이 커밋된 이후에 실제 파일을 삭제한다.
     * AFTER_COMMIT을 사용하는 이유: DB 롤백이 발생하면 파일을 삭제하지 않아
     * attach 레코드와 실제 파일의 일관성을 유지할 수 있다.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(PostImageDeletedEvent event) {
        event.hostPaths().forEach(path -> {
            fileStorageService.delete(path);
            log.debug("게시글 이미지 삭제 완료 — path={}", path);
        });
    }
}
