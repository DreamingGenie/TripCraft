package com.tripcraft.plan.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 일정 단위 후속 작업(이동시간 재계산)을 요청 스레드 밖에서 실행한다.
 *
 * <p>같은 일정의 작업은 제출 순서대로 하나씩 실행한다 — 재계산이 서로 엇갈려 옛 결과가 새 결과를
 * 덮어쓰지 않게 하기 위함. 일정 id 로 고정 개수의 단일 스레드 실행기 중 하나를 고르는 방식이라
 * 서로 다른 일정은 병렬로 돈다(같은 실행기에 걸린 일정끼리는 순서를 공유).
 *
 * <p>큐는 메모리에만 있다. 프로세스가 내려가면 대기 중인 재계산은 사라지고, 해당 날짜의 다음 편집 때
 * 다시 계산된다.
 */
@Slf4j
@Component
public class TripTaskQueue {

    private static final int STRIPES = 4;

    private final ExecutorService[] stripes = new ExecutorService[STRIPES];

    public TripTaskQueue() {
        for (int i = 0; i < STRIPES; i++) {
            String name = "trip-task-" + i;
            stripes[i] = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, name);
                t.setDaemon(true);
                return t;
            });
        }
    }

    public void submit(Long tripId, Runnable task) {
        stripes[(int) Math.floorMod(tripId, (long) STRIPES)].execute(() -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                log.error("일정 후속 작업 실패: tripId={}", tripId, e);
            }
        });
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        for (ExecutorService stripe : stripes) stripe.shutdown();
        for (ExecutorService stripe : stripes) stripe.awaitTermination(5, TimeUnit.SECONDS);
    }
}
