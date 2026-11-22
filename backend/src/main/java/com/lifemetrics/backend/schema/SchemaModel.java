package com.lifemetrics.backend.schema;

import java.util.List;

/** 구조·ERD 페이지(/docs/erd)에 내려보내는 DB 스키마 모양. */
public final class SchemaModel {

    private SchemaModel() {
    }

    /** key: riding | journal | lotto (데이터소스), name: 실제 DB 이름(dev 는 *_dev). error: 읽지 못했을 때 이유. */
    public record Database(String key, String name, List<Table> tables, List<Relation> relations, String error) {
    }

    /** view: 뷰 여부. rows: information_schema 추정 행 수(InnoDB 는 근사치). */
    public record Table(String name, boolean view, Long rows, String comment, List<Column> columns) {
    }

    /** key: PRI | UNI | MUL | "" (COLUMN_KEY). */
    public record Column(String name, String type, boolean nullable, String key, String comment) {
    }

    /** declared: DB 에 선언된 외래 키면 true, 컬럼 이름(xxx_id)으로 추정한 관계면 false. */
    public record Relation(String fromTable, String fromColumn, String toTable, String toColumn, boolean declared) {
    }
}
