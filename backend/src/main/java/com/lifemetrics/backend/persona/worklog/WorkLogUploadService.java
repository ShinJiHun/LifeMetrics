package com.lifemetrics.backend.persona.worklog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** 일일·주간보고 JSON 파일 업로드: 형식 검증(JSON 인지)만 하고 원문 그대로 journal_db.work_log_upload 에 보관. */
@Service
public class WorkLogUploadService {

    private static final int MAX_BYTES = 5 * 1024 * 1024;

    public record Upload(long id, String fileName, int sizeBytes, String uploadedAt) {}
    public record UploadDetail(long id, String fileName, int sizeBytes, String uploadedAt, String content) {
        public Upload summary() { return new Upload(id, fileName, sizeBytes, uploadedAt); }
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public WorkLogUploadService(@Qualifier("journalDataSource") DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    public Upload save(String fileName, String content) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일 내용이 비어 있습니다.");
        }
        int size = content.getBytes(StandardCharsets.UTF_8).length;
        if (size > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "파일이 너무 큽니다 (최대 5MB).");
        }
        try {
            objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "JSON 형식이 아닙니다: " + e.getOriginalMessage());
        }
        String name = fileName == null || fileName.isBlank() ? "upload.json"
                : fileName.length() > 255 ? fileName.substring(0, 255) : fileName;
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update("INSERT INTO work_log_upload (file_name, content, size_bytes) VALUES (:n, :c, :s)",
                new MapSqlParameterSource("n", name).addValue("c", content).addValue("s", size), key, new String[]{"id"});
        return get(key.getKey().longValue()).summary();
    }

    public List<Upload> list() {
        return jdbc.query("""
                SELECT id, file_name, size_bytes, uploaded_at FROM work_log_upload ORDER BY id DESC
                """, new MapSqlParameterSource(), (rs, i) -> new Upload(rs.getLong("id"), rs.getString("file_name"),
                rs.getInt("size_bytes"), rs.getTimestamp("uploaded_at").toLocalDateTime().toString()));
    }

    public UploadDetail get(long id) {
        List<UploadDetail> l = jdbc.query("""
                SELECT id, file_name, size_bytes, uploaded_at, content FROM work_log_upload WHERE id = :id
                """, new MapSqlParameterSource("id", id), (rs, i) -> new UploadDetail(rs.getLong("id"),
                rs.getString("file_name"), rs.getInt("size_bytes"),
                rs.getTimestamp("uploaded_at").toLocalDateTime().toString(), rs.getString("content")));
        if (l.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "업로드를 찾을 수 없습니다.");
        return l.get(0);
    }

    public void delete(long id) {
        jdbc.update("DELETE FROM work_log_upload WHERE id = :id", new MapSqlParameterSource("id", id));
    }
}
