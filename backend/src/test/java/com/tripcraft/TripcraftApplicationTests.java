package com.tripcraft;

import com.tripcraft.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

/**
 * 스프링 컨텍스트 로드 스모크 테스트.
 * Testcontainers MySQL 위에서 전체 빈 그래프가 뜨는지 확인한다({@code @Tag("integration")} — Docker 필요).
 */
class TripcraftApplicationTests extends AbstractIntegrationTest {

	@Test
	void contextLoads() {
	}

}
