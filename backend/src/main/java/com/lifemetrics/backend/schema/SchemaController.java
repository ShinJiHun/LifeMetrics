package com.lifemetrics.backend.schema;

import com.lifemetrics.backend.security.AdminWriteFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 구조·ERD 페이지용 DB 스키마. 테이블 구조가 드러나므로 조회도 관리자만. */
@RestController
@RequestMapping("/api/admin/schema")
@RequiredArgsConstructor
public class SchemaController {

    private final SchemaService service;
    private final AdminWriteFilter adminWriteFilter;

    @GetMapping
    public List<SchemaModel.Database> schema(HttpServletRequest request) {
        if (!adminWriteFilter.isAdmin(request)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "관리자만 볼 수 있습니다.");
        }
        return service.all();
    }
}
