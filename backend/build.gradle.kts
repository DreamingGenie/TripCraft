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

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-security")
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
	compileOnly("org.projectlombok:lombok")
	runtimeOnly("com.mysql:mysql-connector-j")
	annotationProcessor("org.projectlombok:lombok")
	// Spring Batch
	implementation("org.springframework.boot:spring-boot-starter-batch")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.batch:spring-batch-test")
	testCompileOnly("org.projectlombok:lombok")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testAnnotationProcessor("org.projectlombok:lombok")
}

tasks.withType<JavaCompile> {
	options.compilerArgs.add("-parameters")
}

tasks.withType<Test> {
	useJUnitPlatform()
}
