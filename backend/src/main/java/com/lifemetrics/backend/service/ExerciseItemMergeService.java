package com.lifemetrics.backend.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.*;

/**
 * 중복 운동 종목 병합 (관리자 전용).
 *
 * PT 시트 동기화가 시트의 오타 이름(예: "케이스 케이블 크로스 오버")을 새 종목으로 자동 추가해
 * 영문명이 같은 종목이 여러 개 생긴다. 영문명(영문·숫자만 남겨 소문자)이 같은 종목을 한 그룹으로 보고,
 * 대표(활성 종목 중 가장 작은 id)로 기록(exercise_log)을 옮긴 뒤 나머지는 지우지 않고
 * merged_into = 대표 id, is_active = 0 으로 둔다.
 * 시트 동기화는 merged_into 를 따라가므로 오타 이름이 다시 나와도 대표 종목에 기록된다.
 * 되돌리기: UPDATE exercise_item SET merged_into = NULL, is_active = 1 WHERE id = ? (옮긴 기록은 대표에 남음).
 *
 * dev·운영은 시트가 자동 추가한 종목의 id 가 서로 달라 고정 SQL 대신 각 DB 에서 그룹을 계산한다.
 */
@Service
public class ExerciseItemMergeService {

    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public ExerciseItemMergeService(@Qualifier("ridingDataSource") DataSource dataSource,
                                    @Qualifier("ridingTransactionManager") PlatformTransactionManager txManager) {
        this.jdbc = new NamedParameterJdbcTemplate(dataSource);
        this.tx = new TransactionTemplate(txManager);
    }

    public record Item(long id, String nameKo, String nameEn, Long categoryId, boolean active, String gifUrl, int logCount) {}

    public record Group(String key, Item keep, List<Item> merge) {}

    public record Preview(int groupCount, int mergeItemCount, int logCount, List<Group> groups) {}

    public record Result(int mergedGroups, int mergedItems, int movedLogs, int filledGifs) {}

    /** 영문명 비교 키: 영문·숫자만 남긴 소문자. "Cable Cross Over" = "Cable Crossover" = "cable-crossover". */
    static String key(String nameEn) {
        return nameEn == null ? "" : nameEn.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /** 병합 대상 그룹. excludeIds 에 든 종목은 그룹에서 뺀다(같은 영문명이지만 다른 운동인 경우). */
    public Preview preview(Collection<Long> excludeIds) {
        Set<Long> exclude = excludeIds == null ? Set.of() : new HashSet<>(excludeIds);
        Map<String, List<Item>> byKey = new LinkedHashMap<>();
        jdbc.query("""
                SELECT i.id, i.name_ko, i.name_en, i.category_id, i.is_active, i.gif_url,
                       (SELECT COUNT(*) FROM exercise_log l WHERE l.exercise_item_id = i.id) AS log_count
                FROM exercise_item i
                WHERE i.merged_into IS NULL
                ORDER BY i.id
                """, new MapSqlParameterSource(), rs -> {
            long id = rs.getLong("id");
            if (exclude.contains(id)) return;
            String k = key(rs.getString("name_en"));
            if (k.isEmpty()) return;
            long cat = rs.getLong("category_id");
            byKey.computeIfAbsent(k, x -> new ArrayList<>()).add(new Item(id, rs.getString("name_ko"), rs.getString("name_en"),
                    rs.wasNull() ? null : cat, rs.getBoolean("is_active"), rs.getString("gif_url"), rs.getInt("log_count")));
        });

        List<Group> groups = new ArrayList<>();
        int items = 0, logs = 0;
        for (Map.Entry<String, List<Item>> e : byKey.entrySet()) {
            List<Item> list = e.getValue();
            if (list.size() < 2) continue;
            Item keep = list.stream().filter(Item::active).findFirst().orElse(list.get(0));
            List<Item> merge = list.stream().filter(i -> i.id() != keep.id()).toList();
            groups.add(new Group(e.getKey(), keep, merge));
            items += merge.size();
            logs += merge.stream().mapToInt(Item::logCount).sum();
        }
        return new Preview(groups.size(), items, logs, groups);
    }

    /** preview 와 같은 그룹을 한 트랜잭션으로 병합한다. */
    public Result merge(Collection<Long> excludeIds) {
        return tx.execute(status -> {
            Preview p = preview(excludeIds);
            int movedLogs = 0, mergedItems = 0, filledGifs = 0;
            for (Group g : p.groups()) {
                List<Long> ids = g.merge().stream().map(Item::id).toList();
                MapSqlParameterSource params = new MapSqlParameterSource("keep", g.keep().id()).addValue("ids", ids);
                movedLogs += jdbc.update("UPDATE exercise_log SET exercise_item_id = :keep WHERE exercise_item_id IN (:ids)", params);
                mergedItems += jdbc.update("UPDATE exercise_item SET merged_into = :keep, is_active = 0 WHERE id IN (:ids)", params);
                // 대표에 GIF 가 없고 합쳐지는 종목에 있으면 가져온다.
                if (g.keep().gifUrl() == null || g.keep().gifUrl().isBlank()) {
                    g.merge().stream().map(Item::gifUrl).filter(u -> u != null && !u.isBlank()).findFirst().ifPresent(u ->
                            jdbc.update("UPDATE exercise_item SET gif_url = :u WHERE id = :keep AND (gif_url IS NULL OR gif_url = '')",
                                    new MapSqlParameterSource("u", u).addValue("keep", g.keep().id())));
                    if (g.merge().stream().anyMatch(i -> i.gifUrl() != null && !i.gifUrl().isBlank())) filledGifs++;
                }
            }
            return new Result(p.groupCount(), mergedItems, movedLogs, filledGifs);
        });
    }
}
