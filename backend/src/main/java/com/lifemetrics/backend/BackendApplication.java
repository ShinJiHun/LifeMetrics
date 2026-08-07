package com.lifemetrics.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import jakarta.annotation.PostConstruct;
import java.util.TimeZone;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        // .env 는 backend/src/main/resources/.env 에 위치 (gitignored).
        // spring-dotenv 기본 탐색 경로는 실행 작업 디렉터리(backend/)이므로 디렉터리를 지정한다.
        System.setProperty("springdotenv.directory", "src/main/resources");
        SpringApplication.run(BackendApplication.class, args);
    }

    @PostConstruct
    public void init() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
    }

}
