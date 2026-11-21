plugins {
	java
	id("org.springframework.boot") version "3.5.0"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.tripcraft"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

// Spring Boot 의존성 관리가 Testcontainers 를 1.21.0 으로 고정한다(platform BOM 을 선언해도 덮어쓴다).
// Docker Engine 29+(최소 API 1.44)와 통신하려면 1.21.4 이상이 필요해 관리 버전 자체를 올린다.
extra["testcontainers.version"] = "1.21.4"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-web")
	// MyBatis
	implementation("org.mybatis.spring.boot:mybatis-spring-boot-starter:3.0.4")
	// JWT
	implementation("io.jsonwebtoken:jjwt-api:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
	// .env 파일 로드
	implementation("me.paulschwarz:spring-dotenv:4.0.0")
	// Swagger (OpenAPI)
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
	// Spring AI (OpenAI 호환 — gms 프록시 사용)
	implementation("org.springframework.ai:spring-ai-starter-model-openai")
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("com.mysql:mysql-connector-j")
	annotationProcessor("org.projectlombok:lombok")
	// Spring Batch
	implementation("org.springframework.boot:spring-boot-starter-batch")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.batch:spring-batch-test")
	// Testcontainers — 실제 MySQL 8 컨테이너 기반 통합/매퍼 테스트(@Tag("integration"))
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:mysql")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.ai:spring-ai-bom:1.0.6")
	}
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-parameters")
}

// 기본 `test` = 순수 단위테스트만(Docker 불필요) → integration 태그 제외해 항상 green.
tasks.test {
	useJUnitPlatform {
		excludeTags("integration")
	}
}

// `integrationTest` = Testcontainers(실 MySQL) 기반 @Tag("integration") 테스트. Docker 필요.
tasks.register<Test>("integrationTest") {
	description = "Testcontainers 기반 통합/매퍼 테스트 (Docker 필요)"
	group = "verification"
	useJUnitPlatform {
		includeTags("integration")
	}
	shouldRunAfter(tasks.test)
}
