package com.tripcraft.attraction.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripcraft.attraction.client.TourApiCallLimiter;
import com.tripcraft.attraction.client.TourApiClient;
import com.tripcraft.attraction.client.dto.TourApiItem;
import com.tripcraft.attraction.domain.Attraction;
import com.tripcraft.attraction.mapper.AttractionDetailCommonMapper;
import com.tripcraft.attraction.mapper.AttractionDetailImageMapper;
import com.tripcraft.attraction.mapper.AttractionDetailInfoMapper;
import com.tripcraft.attraction.mapper.AttractionDetailIntroMapper;
import com.tripcraft.attraction.mapper.AttractionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * TourAPI 관광지 동기화 Batch Job 설정.
 *
 * <h2>구조</h2>
 * <pre>
 * tourApiSyncJob
 *  └─ tourApiSyncStep  (chunk size: 100)
 *        ├─ TourApiItemReader     : TourAPI 페이지 순회, 아이템 1건씩 반환
 *        ├─ TourApiItemProcessor  : TourApiItem → Attraction 변환 (실패 시 null → 걸러냄. skip 집계에는 안 잡힌다)
 *        └─ TourApiItemWriter     : attraction 테이블 UPSERT (ON DUPLICATE KEY UPDATE)
 * </pre>
 *
 * <h2>청크 처리 흐름</h2>
 * Reader가 100건을 읽을 때마다 Processor → Writer 순서로 처리하고
 * 하나의 트랜잭션으로 커밋합니다.
 *
 * <h2>쓰기 실패 건너뛰기</h2>
 * 청크 UPSERT 가 DB 오류(길이 초과 등)로 실패하면 그 청크를 롤백하고 1건씩 다시 써서 문제 행만 찾아
 * 건너뛴다(fault tolerant skip). 한 행 때문에 Step 전체가 FAILED 로 끝나 그 뒤 지역이 수집되지 않는 것을 막는다.
 * 건너뛴 건수가 {@value #SKIP_LIMIT}을 넘으면 데이터가 아니라 환경 문제로 보고 Step 을 실패시킨다.
 *
 * <h2>@StepScope</h2>
 * Reader / Processor / Writer 모두 {@code @StepScope}로 등록됩니다.
 * Step이 시작될 때 인스턴스가 생성되고 Step이 끝나면 소멸되므로,
 * Reader의 내부 상태(버퍼, 페이지 번호 등)가 매 실행마다 초기화됩니다.
 */
@Configuration
@RequiredArgsConstructor
public class TourApiSyncJobConfig {

    private static final int CHUNK_SIZE = 100;
    private static final int SKIP_LIMIT = 100;

    // ───────── Job ─────────

    @Bean
    public Job tourApiSyncJob(JobRepository jobRepository,
                              @Qualifier("tourApiSyncStep") Step tourApiSyncStep,
                              @Qualifier("tourApiDetailSyncStep") Step tourApiDetailSyncStep) {
        return new JobBuilder("tourApiSyncJob", jobRepository)
                .start(tourApiSyncStep)
                .next(tourApiDetailSyncStep)
                .build();
    }

    // ───────── Step ─────────

    @Bean
    public Step tourApiSyncStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager,
                                TourApiItemReader tourApiItemReader,
                                TourApiItemProcessor tourApiItemProcessor,
                                TourApiItemWriter tourApiItemWriter) {
        return new StepBuilder("tourApiSyncStep", jobRepository)
                .<TourApiItem, Attraction>chunk(CHUNK_SIZE, transactionManager)
                .reader(tourApiItemReader)
                .processor(tourApiItemProcessor)
                .writer(tourApiItemWriter)
                .faultTolerant()
                .skip(org.springframework.dao.DataAccessException.class)
                .skipLimit(SKIP_LIMIT)
                .listener(new org.springframework.batch.core.SkipListener<TourApiItem, Attraction>() {
                    @Override
                    public void onSkipInWrite(Attraction item, Throwable t) {
                        org.slf4j.LoggerFactory.getLogger(TourApiSyncJobConfig.class)
                                .warn("관광지 저장 건너뜀 — contentId={} title={}: {}",
                                        item.getContentId(), item.getTitle(), t.getMessage());
                    }
                })
                .build();
    }

    // ───────── Reader / Processor / Writer Bean ─────────

    /**
     * @StepScope: Step 시작 시 생성, Step 종료 시 소멸.
     * 매 실행마다 Reader 내부 상태(버퍼, 인덱스)가 초기화됩니다.
     */
    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public TourApiItemReader tourApiItemReader(TourApiClient tourApiClient) {
        return new TourApiItemReader(tourApiClient);
    }

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public TourApiItemProcessor tourApiItemProcessor() {
        return new TourApiItemProcessor();
    }

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public TourApiItemWriter tourApiItemWriter(AttractionMapper attractionMapper) {
        return new TourApiItemWriter(attractionMapper);
    }

    // ───────── Detail Sync Step ─────────

    @Bean
    public Step tourApiDetailSyncStep(JobRepository jobRepository,
                                      PlatformTransactionManager transactionManager,
                                      AttractionMapper attractionMapper,
                                      AttractionDetailCommonMapper detailCommonMapper,
                                      AttractionDetailIntroMapper detailIntroMapper,
                                      AttractionDetailImageMapper detailImageMapper,
                                      AttractionDetailInfoMapper detailInfoMapper,
                                      TourApiClient tourApiClient,
                                      TourApiCallLimiter limiter,
                                      ObjectMapper objectMapper) {
        return new StepBuilder("tourApiDetailSyncStep", jobRepository)
                .tasklet(new AttractionDetailSyncTasklet(attractionMapper, detailCommonMapper,
                        detailIntroMapper, detailImageMapper, detailInfoMapper,
                        tourApiClient, limiter, objectMapper),
                        transactionManager)
                .build();
    }
}
