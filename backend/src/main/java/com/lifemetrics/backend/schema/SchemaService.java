package com.lifemetrics.backend.schema;

import com.lifemetrics.backend.schema.SchemaModel.Column;
import com.lifemetrics.backend.schema.SchemaModel.Database;
import com.lifemetrics.backend.schema.SchemaModel.Relation;
import com.lifemetrics.backend.schema.SchemaModel.Table;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 연결된 DB(riding·journal·lotto)의 실제 스키마를 information_schema 에서 읽는다.
 * create.sql 이 낡아 운영 DB 와 다르므로, 지금 붙어 있는 DB 를 그대로 보여 주는 게 목적이다(dev 는 *_dev).
 * 읽기만 한다. lotto 처럼 꺼진 데이터소스는 목록에 없다.
 */
@Slf4j
@Service
public class SchemaService {

    /** 데이터소스 빈 이름 → 화면 key. 순서대로 보여 준다. */
    private static final Map<String, String> SOURCES = new LinkedHashMap<>();

    static {
        SOURCES.put("ridingDataSource", "riding");
        SOURCES.put("journalDataSource", "journal");
        SOURCES.put("lottoDataSource", "lotto");
    }

    private final Map<String, DataSource> dataSources;

    public SchemaService(Map<String, DataSource> dataSources) {
        this.dataSources = dataSources;
    }

    public List<Database> all() {
        List<Database> out = new ArrayList<>();
        SOURCES.forEach((bean, key) -> {
            DataSource ds = dataSources.get(bean);
            if (ds != null) out.add(read(key, ds));
        });
        return out;
    }

    private Database read(String key, DataSource ds) {
        JdbcTemplate jdbc = new JdbcTemplate(ds);
        String db = null;
        try {
            db = jdbc.queryForObject("SELECT DATABASE()", String.class);

            Map<String, List<Column>> cols = new LinkedHashMap<>();
            jdbc.query("""
                    SELECT TABLE_NAME, COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_KEY, COLUMN_COMMENT
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                    ORDER BY TABLE_NAME, ORDINAL_POSITION
                    """, rs -> {
                cols.computeIfAbsent(rs.getString(1), k -> new ArrayList<>()).add(new Column(
                        rs.getString(2), rs.getString(3), "YES".equals(rs.getString(4)),
                        nz(rs.getString(5)), nz(rs.getString(6))));
            });

            List<Table> tables = jdbc.query("""
                    SELECT TABLE_NAME, TABLE_TYPE, TABLE_ROWS, TABLE_COMMENT
                    FROM information_schema.TABLES
                    WHERE TABLE_SCHEMA = DATABASE()
                    ORDER BY TABLE_NAME
                    """, (rs, i) -> {
                String name = rs.getString(1);
                long rows = rs.getLong(3);
                return new Table(name, "VIEW".equals(rs.getString(2)), rs.wasNull() ? null : rows,
                        nz(rs.getString(4)), cols.getOrDefault(name, List.of()));
            });

            List<Relation> declared = jdbc.query("""
                    SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
                    FROM information_schema.KEY_COLUMN_USAGE
                    WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_SCHEMA = DATABASE()
                      AND REFERENCED_TABLE_NAME IS NOT NULL
                    ORDER BY TABLE_NAME, COLUMN_NAME
                    """, (rs, i) -> new Relation(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), true));

            List<Relation> relations = new ArrayList<>(declared);
            relations.addAll(RelationInference.infer(tables, declared, db));
            return new Database(key, db, tables, relations, null);
        } catch (Exception e) {
            log.warn("스키마 읽기 실패 ({}): {}", key, e.getMessage());
            return new Database(key, db, List.of(), List.of(), "스키마를 읽지 못했습니다: " + e.getClass().getSimpleName());
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
