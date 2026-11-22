package com.lifemetrics.backend.schema;

import com.lifemetrics.backend.schema.SchemaModel.Column;
import com.lifemetrics.backend.schema.SchemaModel.Relation;
import com.lifemetrics.backend.schema.SchemaModel.Table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 외래 키를 거의 선언하지 않은 DB 라서, 컬럼 이름으로 관계를 추정한다.
 * {@code xxx_id} 컬럼 → 같은 DB 의 {@code xxx}, {@code xxxs}, {@code xxxes} 테이블, 없으면 이름이 {@code _xxx} 로 끝나는 테이블이 하나뿐일 때 그 테이블.
 * {@code parent_id} 는 자기 자신을 가리킨다. 이미 선언된 외래 키와 겹치면 추정하지 않는다.
 */
final class RelationInference {

    private RelationInference() {
    }

    static List<Relation> infer(List<Table> tables, List<Relation> declared) {
        Set<String> names = new HashSet<>();
        for (Table t : tables) names.add(t.name());
        Set<String> declaredKeys = new HashSet<>();
        for (Relation r : declared) declaredKeys.add(r.fromTable() + "." + r.fromColumn());

        List<Relation> out = new ArrayList<>();
        for (Table t : tables) {
            if (t.view()) continue;
            for (Column c : t.columns()) {
                String col = c.name().toLowerCase();
                if (!col.endsWith("_id") || col.equals("id")) continue;
                if (declaredKeys.contains(t.name() + "." + c.name())) continue;
                String target = target(t.name(), col.substring(0, col.length() - 3), names);
                if (target != null) out.add(new Relation(t.name(), c.name(), target, "id", false));
            }
        }
        return out;
    }

    private static String target(String self, String base, Set<String> names) {
        if (base.isEmpty()) return null;
        if (base.equals("parent")) return self;
        for (String cand : List.of(base, base + "s", base + "es")) {
            if (names.contains(cand) && !cand.equals(self)) return cand;
        }
        Set<String> suffixed = new LinkedHashSet<>();
        for (String n : names) {
            if (!n.equals(self) && n.endsWith("_" + base)) suffixed.add(n);
        }
        return suffixed.size() == 1 ? suffixed.iterator().next() : null;
    }
}
