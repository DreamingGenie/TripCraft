package com.tripcraft.plan.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TripTaskQueueTest {

    /** 깨지면: 같은 일정의 재계산이 엇갈려 옛 결과가 새 결과를 덮어쓸 수 있다. */
    @Test
    void 같은_일정의_작업은_제출_순서대로_실행된다() throws Exception {
        TripTaskQueue queue = new TripTaskQueue();
        List<Integer> ran = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(200);

        for (int i = 0; i < 200; i++) {
            int n = i;
            queue.submit(7L, () -> { ran.add(n); done.countDown(); });
        }

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(ran).containsExactlyElementsOf(IntStream.range(0, 200).boxed().toList());
        queue.shutdown();
    }

    /** 깨지면: 한 작업의 예외가 실행 스레드를 죽여 그 뒤 재계산이 전부 멈춘다. */
    @Test
    void 작업이_예외를_던져도_다음_작업은_실행된다() throws Exception {
        TripTaskQueue queue = new TripTaskQueue();
        CountDownLatch done = new CountDownLatch(1);

        queue.submit(7L, () -> { throw new IllegalStateException("실패"); });
        queue.submit(7L, done::countDown);

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        queue.shutdown();
    }
}
