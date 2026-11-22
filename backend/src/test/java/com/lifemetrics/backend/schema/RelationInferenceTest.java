package com.lifemetrics.backend.schema;

import com.lifemetrics.backend.schema.SchemaModel.Column;
import com.lifemetrics.backend.schema.SchemaModel.Relation;
import com.lifemetrics.backend.schema.SchemaModel.Table;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RelationInferenceTest {

    private static Table table(String name, String... cols) {
        return new Table(name, false, 0L, "",
                Arrays.stream(cols).map(c -> new Column(c, "bigint", true, "", "")).toList());
    }

    private static List<String> edges(List<Relation> rs) {
        return rs.stream().map(r -> r.fromTable() + "." + r.fromColumn() + "->" + r.toTable()).toList();
    }

    @Test
    void 이름이_같은_테이블과_복수형으로_잇는다() {
        List<Table> tables = List.of(
                table("activity", "id", "user_id"),
                table("activity_point", "id", "activity_id"),
                table("users", "id"));
        assertThat(edges(RelationInference.infer(tables, List.of())))
                .containsExactlyInAnyOrder("activity.user_id->users", "activity_point.activity_id->activity");
    }

    @Test
    void 접미사가_하나뿐인_테이블로_잇고_여럿이면_추정하지_않는다() {
        List<Table> tables = List.of(
                table("lecture_entry", "id", "post_id", "entry_id"),
                table("journal_post", "id"),
                table("diary_entry", "id"),
                table("lecture_file", "id", "entry_id"));
        // post → journal_post 하나뿐이라 잇는다. entry → lecture_entry·diary_entry 둘이라 lecture_file 쪽은 추정 안 함.
        // lecture_entry.entry_id 는 자기 자신을 빼면 diary_entry 하나라 잇는다.
        assertThat(edges(RelationInference.infer(tables, List.of())))
                .containsExactlyInAnyOrder("lecture_entry.post_id->journal_post", "lecture_entry.entry_id->diary_entry");
    }

    @Test
    void parent_id_는_자기_자신_선언된_외래키와_뷰는_건너뛴다() {
        Table view = new Table("v_summary", true, null, "", List.of(new Column("activity_id", "bigint", true, "", "")));
        List<Table> tables = List.of(
                table("dev_request", "id", "parent_id"),
                table("activity", "id"),
                table("activity_core", "id", "activity_id"),
                view);
        List<Relation> declared = List.of(new Relation("activity_core", "activity_id", "activity", "id", true));
        assertThat(edges(RelationInference.infer(tables, declared)))
                .containsExactly("dev_request.parent_id->dev_request");
    }
}
