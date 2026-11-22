package com.lifemetrics.backend.schema;

import com.lifemetrics.backend.schema.SchemaModel.Column;
import com.lifemetrics.backend.schema.SchemaModel.Relation;
import com.lifemetrics.backend.schema.SchemaModel.Table;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * 외래 키를 거의 선언하지 않은 DB 라서, 컬럼 이름으로 관계를 추정한다. {@code xxx_id} 컬럼의 대상 테이블을 이 순서로 찾는다.
 * <ol>
 *   <li>{@code xxx}, {@code xxxs}, {@code xxxes}</li>
 *   <li>이름이 {@code _xxx} 로 끝나는 테이블 — 여럿이면 원래 테이블과 접두어(첫 토큰)가 같은 것 하나만</li>
 *   <li>원래 테이블 접두어 + {@code _xxx} 로 시작하는 테이블 하나(journal_post.sub_id → journal_sub_menu)</li>
 *   <li>그래도 없으면 {@code xxx} 앞 토큰을 떼고 다시(source_note_id → note → diary_note)</li>
 * </ol>
 * {@code parent_id} 는 자기 자신. 이미 선언된 외래 키, 뷰, 자기 자신(parent 제외)은 건너뛴다. 애매하면 추정하지 않는다.
 */
final class RelationInference {

    private RelationInference() {
    }

    static List<Relation> infer(List<Table> tables, List<Relation> declared) {
        Set<String> names = new HashSet<>();
        for (Table t : tables) if (!t.view()) names.add(t.name());
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
        if (base.equals("parent")) return self;
        String prefix = self.split("_")[0];
        String b = base;
        while (!b.isEmpty()) {
            String found = match(self, prefix, b, names);
            if (found != null) return found;
            int cut = b.indexOf('_');
            if (cut < 0) break;
            b = b.substring(cut + 1); // 앞 토큰을 떼고 다시
        }
        return null;
    }

    private static String match(String self, String prefix, String base, Set<String> names) {
        for (String cand : List.of(base, base + "s", base + "es")) {
            if (names.contains(cand) && !cand.equals(self)) return cand;
        }
        String bySuffix = unique(names, n -> !n.equals(self) && n.endsWith("_" + base), prefix);
        if (bySuffix != null) return bySuffix;
        return unique(names, n -> !n.equals(self) && n.startsWith(prefix + "_" + base), null);
    }

    /** 조건에 맞는 테이블이 하나면 그것, 여럿이면 접두어가 같은 것이 하나일 때만 그것. */
    private static String unique(Set<String> names, Predicate<String> ok, String prefix) {
        List<String> hits = names.stream().filter(ok).sorted().toList();
        if (hits.size() == 1) return hits.get(0);
        if (hits.size() > 1 && prefix != null) {
            List<String> same = hits.stream().filter(n -> n.startsWith(prefix + "_")).toList();
            if (same.size() == 1) return same.get(0);
        }
        return null;
    }
}
