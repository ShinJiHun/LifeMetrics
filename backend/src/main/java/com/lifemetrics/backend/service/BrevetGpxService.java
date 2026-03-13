package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.BrevetCourseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Slf4j
@Service
public class BrevetGpxService {

    @Value("${brevet.nas-path:/data/home/tho881/project/NAS/brevet}")
    private String nasPath;

    private static final Pattern DIST_PATTERN =
            Pattern.compile("(200|300|400|600|1000|1200)", Pattern.CASE_INSENSITIVE);

    /**
     * NAS brevet 폴더 하위의 코스 목록 반환
     * 각 서브폴더 = 1개 코스, 그 안의 GPX 파일명 기준
     */
    public List<BrevetCourseDto> listCourses() throws IOException {
        Path base = Paths.get(nasPath);
        List<BrevetCourseDto> result = new ArrayList<>();

        if (!Files.exists(base)) {
            log.warn("NAS 경로 없음: {}", nasPath);
            return result;
        }

        try (Stream<Path> dirs = Files.list(base)) {
            dirs.filter(Files::isDirectory)
                .sorted()
                .forEach(dir -> {
                    try {
                        List<Path> gpxFiles = findGpxFiles(dir);
                        if (gpxFiles.isEmpty()) return;

                        String folderName = dir.getFileName().toString();
                        String distance = extractDistance(folderName);

                        // GPX 파일이 여러 개면 각각 등록
                        for (Path gpx : gpxFiles) {
                            String gpxName = gpx.getFileName().toString();
                            String courseName = gpxName.replace(".gpx", "").replace(".GPX", "");
                            result.add(BrevetCourseDto.builder()
                                    .folderName(folderName)
                                    .courseName(courseName)
                                    .gpxFileName(gpxName)
                                    .distance(distance)
                                    .fileSizeKb(Math.round(Files.size(gpx) / 1024.0 * 10) / 10.0)
                                    .build());
                        }
                    } catch (IOException e) {
                        log.warn("폴더 탐색 실패: {}", dir, e);
                    }
                });
        }

        log.info("브레베 코스 목록: {}개", result.size());
        return result;
    }

    /**
     * 특정 코스의 GPX 파일 내용 반환
     * folderName / gpxFileName 으로 경로 특정
     */
    public String getGpxContent(String folderName, String gpxFileName) throws IOException {
        // 경로 트래버설 방어
        if (folderName.contains("..") || gpxFileName.contains("..")) {
            throw new IllegalArgumentException("잘못된 경로");
        }

        Path gpxPath = Paths.get(nasPath, folderName, gpxFileName);

        if (!Files.exists(gpxPath)) {
            throw new IOException("GPX 파일 없음: " + gpxPath);
        }
        if (!gpxPath.toAbsolutePath().startsWith(Paths.get(nasPath).toAbsolutePath())) {
            throw new SecurityException("허용되지 않은 경로");
        }

        return Files.readString(gpxPath);
    }

    // ── 내부 유틸 ──────────────────────────────────────

    private List<Path> findGpxFiles(Path dir) throws IOException {
        List<Path> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> {
                String name = f.getFileName().toString().toLowerCase();
                return name.endsWith(".gpx") && Files.isRegularFile(f);
            })
            .sorted()
            .forEach(result::add);
        }
        return result;
    }

    private String extractDistance(String text) {
        Matcher m = DIST_PATTERN.matcher(text);
        return m.find() ? m.group(1) + "K" : "-";
    }
}
