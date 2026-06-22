package com.tripcraft.attraction.batch;

import com.tripcraft.attraction.service.ReferenceDataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * TourAPI 동기화 배치 스케줄러.
 *
 * <p>매일 새벽 2시(Asia/Seoul)에 {@code tourApiSyncJob}을 자동 실행합니다.
 *
 * <h2>JobParameters에 runDate를 포함하는 이유</h2>
 * Spring Batch는 동일한 JobParameters로 이미 성공한 Job을 재실행하지 않습니다.
 * {@code runDate}를 매일 바꿔 전달함으로써 날마다 새 Job 인스턴스로 실행됩니다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TourApiSyncScheduler {

    private final JobLauncher jobLauncher;
    private final Job tourApiSyncJob;
    private final ReferenceDataSyncService referenceDataSyncService;

    /**
     * 매일 새벽 2시 실행.
     * cron 표현식: 초 분 시 일 월 요일
     */
    @Scheduled(cron = "0 0 2 * * *", zone = "Asia/Seoul")
    public void runDailySync() {
        log.info("TourAPI 정기 배치 시작 — runDate={}", LocalDate.now());

        // 지역 참조 데이터(시도·시군구) 동기화 — 실패해도 관광지 배치는 계속 진행
        try {
            referenceDataSyncService.sync();
        } catch (Exception e) {
            log.error("참조 데이터(시도·시군구) 동기화 실패", e);
        }

        try {
            JobParameters params = new JobParametersBuilder()
                    .addLocalDate("runDate", LocalDate.now())
                    .toJobParameters();

            jobLauncher.run(tourApiSyncJob, params);
        } catch (Exception e) {
            log.error("TourAPI 정기 배치 실행 실패", e);
        }
    }
}
