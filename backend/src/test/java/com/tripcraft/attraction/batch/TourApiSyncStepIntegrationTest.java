package com.tripcraft.attraction.batch;

import com.tripcraft.attraction.client.TourApiClient;
import com.tripcraft.attraction.client.dto.TourApiItem;
import com.tripcraft.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * 관광지 동기화 Step 을 실 MySQL 로 돌린다. 외부 TourAPI 호출만 목으로 바꾼다.
 */
@SpringBatchTest
class TourApiSyncStepIntegrationTest extends AbstractIntegrationTest {

    @Autowired JobLauncherTestUtils jobLauncher;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean TourApiClient tourApiClient;

    /** 깨지면: 저장할 수 없는 관광지 한 건 때문에 Step 이 FAILED 로 끝나 나머지 지역이 수집되지 않는다. */
    @Test
    void 저장에_실패한_한_건만_건너뛰고_나머지는_저장한다() {
        String run = "s" + (System.nanoTime() % 1_000_000_000L);   // content_id 는 VARCHAR(20)
        when(tourApiClient.fetchAreaList(1, 12, 1, 1000)).thenReturn(List.of(
                item(run + "-a", "정상 1"),
                item(run + "-b", "가".repeat(201)),      // title VARCHAR(200) 초과 → 저장 실패
                item(run + "-c", "정상 2")));

        JobExecution execution = jobLauncher.launchStep("tourApiSyncStep",
                new JobParametersBuilder().addString("run", run).toJobParameters());

        StepExecution step = execution.getStepExecutions().iterator().next();
        assertThat(step.getStatus()).isEqualTo(BatchStatus.COMPLETED);
        assertThat(step.getWriteSkipCount()).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT content_id FROM attraction WHERE content_id LIKE ? ORDER BY content_id",
                String.class, run + "%")).containsExactly(run + "-a", run + "-c");
    }

    private TourApiItem item(String contentId, String title) {
        TourApiItem item = new TourApiItem();
        ReflectionTestUtils.setField(item, "contentid", contentId);
        ReflectionTestUtils.setField(item, "contenttypeid", "12");
        ReflectionTestUtils.setField(item, "title", title);
        ReflectionTestUtils.setField(item, "areacode", "1");
        ReflectionTestUtils.setField(item, "sigungucode", "1");
        return item;
    }
}
