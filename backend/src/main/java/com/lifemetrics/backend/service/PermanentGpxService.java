package com.lifemetrics.backend.service;

import com.lifemetrics.backend.util.PolylineEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * NAS의 permanent 폴더(permanent.nas-path)에서 GPX 파일을 직접 스캔한다.
 * permanent_courses.permanent_no는 NAS의 폴더명과 항상 같아서 별도 경로 컬럼 없이
 * 폴더명으로 바로 찾는다(BrevetGpxService와 동일한 방식).
 * 폴더 안에 gpx가 여러 개인 코스(본코스/Plan B 등)는 있는 그대로 전부 반환한다.
 */
@Slf4j
@Service
public class PermanentGpxService {

    @Value("${permanent.nas-path:/mnt/200gb/NAS/data/permanent}")
    private String nasPath;

    /** 특정 permanentNo 폴더 안의 gpx 파일명 목록 (정렬됨, 없으면 빈 리스트) */
    public List<String> listGpxFiles(String permanentNo) {
        List<String> result = new ArrayList<>();
        if (permanentNo == null || permanentNo.contains("..")) {
            return result;
        }

        Path dir = Paths.get(nasPath, permanentNo);
        if (!Files.isDirectory(dir)) {
            return result;
        }

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".gpx"))
                    .filter(Files::isRegularFile)
                    .map(f -> f.getFileName().toString())
                    .sorted()
                    .forEach(result::add);
        } catch (IOException e) {
            log.warn("퍼머넌트 폴더 탐색 실패: {}", dir, e);
        }
        return result;
    }

    /** 특정 permanentNo 폴더의 특정 gpx 파일 내용 반환 */
    public String getGpxContent(String permanentNo, String fileName) throws IOException {
        if (permanentNo == null || permanentNo.contains("..") || fileName == null || fileName.contains("..")) {
            throw new IllegalArgumentException("잘못된 경로");
        }

        Path base = Paths.get(nasPath).toAbsolutePath().normalize();
        Path resolved = base.resolve(permanentNo).resolve(fileName).normalize();
        if (!resolved.startsWith(base)) {
            throw new SecurityException("허용되지 않은 경로");
        }
        if (!Files.exists(resolved)) {
            throw new IOException("GPX 파일 없음: " + resolved);
        }

        return Files.readString(resolved);
    }

    /**
     * 코스 확인 페이지 지도용 대표 gpx의 인코딩 경로를 계산한다.
     * 폴더에 gpx가 여러 개면(본코스/Plan B 등) listGpxFiles 정렬 기준 첫 번째를 대표로 쓴다.
     */
    public Optional<String> computePolylineForPrimaryFile(String permanentNo) {
        List<String> gpxFiles = listGpxFiles(permanentNo);
        if (gpxFiles.isEmpty()) {
            return Optional.empty();
        }

        try {
            String content = getGpxContent(permanentNo, gpxFiles.get(0));
            List<double[]> points = parseTrackPoints(content);
            if (points.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(PolylineEncoder.encodeLatLng(points));
        } catch (Exception e) {
            log.warn("퍼머넌트 polyline 계산 실패: {}", permanentNo, e);
            return Optional.empty();
        }
    }

    /** GPX XML에서 <trkpt lat lon> 좌표를 순서대로 추출 */
    private List<double[]> parseTrackPoints(String gpxXml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        // XXE 방지: 외부 엔티티/DOCTYPE 비활성화
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new ByteArrayInputStream(gpxXml.getBytes(StandardCharsets.UTF_8)));

        List<double[]> points = new ArrayList<>();
        NodeList trkpts = doc.getElementsByTagName("trkpt");
        for (int i = 0; i < trkpts.getLength(); i++) {
            Element el = (Element) trkpts.item(i);
            String latStr = el.getAttribute("lat");
            String lonStr = el.getAttribute("lon");
            if (latStr.isBlank() || lonStr.isBlank()) continue;
            try {
                points.add(new double[]{Double.parseDouble(latStr), Double.parseDouble(lonStr)});
            } catch (NumberFormatException ignored) {
                // 좌표 파싱 실패한 포인트는 건너뜀
            }
        }
        return points;
    }
}
