package com.tripcraft.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * @Scheduled 스케줄링 활성화 설정.
 * TourApiSyncScheduler 등 @Scheduled 어노테이션이 동작하려면 이 설정이 필요합니다.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
