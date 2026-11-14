package com.lifemetrics.backend.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.entity.RandonneuringResult;
import com.lifemetrics.backend.repository.RandonneuringResultRepository;
import com.lifemetrics.backend.service.RandonneuringCareer;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetRandonneuringRecordToolTest {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);

    private static RandonneuringResult r(String date, String type, String name, Integer km, String status) {
        RandonneuringResult r = new RandonneuringResult();
        r.setUserId(1L);
        r.setEventDate(LocalDate.parse(date));
        r.setEventType(type);
        r.setEventName(name);
        r.setDistanceKm(km);
        r.setStatus(status);
        return r;
    }

    // 최신순 (리포지토리 정렬과 같게)
    private static final List<RandonneuringResult> ALL = List.of(
            r("2026-10-03", "BREVET", "서울-부산-서울 1000K", 1000, "DNF"),
            r("2026-05-02", "BREVET", "서울 600K-동", 600, "FINISHED"),
            r("2026-04-25", "BREVET", "서울 400K-서", 400, "FINISHED"),
            r("2026-04-11", "BREVET", "서울 300K-서", 300, "FINISHED"),
            r("2026-03-01", "BREVET", "천안 200K", 200, "FINISHED"),
            r("2025-09-27", "BREVET", "광주-부산-광주 600K", 600, "DNF"),
            r("2025-04-19", "FLECHE", "플레쉬-2", null, "DNF"),
            r("2024-05-18", "PERMANENT", "[PT-193] 한강 투어 2", 202, "FINISHED"));

    @Test
    void 경력_요약은_완주만_거리로_더하고_같은_해_200_600_완주를_찾는다() {
        String s = RandonneuringCareer.summary(ALL);

        assertThat(s).startsWith("공식 참가 8회 (완주 5, DNF 3), 완주 거리 1,702km")
                .contains("최장 완주 브레베 600km (2026-05-02 서울 600K-동)")
                .contains("연도별 2024년 1회(완주 1) · 2025년 2회(완주 0) · 2026년 5회(완주 4)")
                .contains("모두 완주한 해: 2026")
                .endsWith("최근 DNF 2026-10-03 서울-부산-서울 1000K");
        assertThat(RandonneuringCareer.summary(List.of())).isNull();
    }

    @Test
    void 연도_상태로_거르고_머리에_전체_경력을_붙인다() throws Exception {
        RandonneuringResultRepository repo = mock(RandonneuringResultRepository.class);
        when(repo.findByUserIdOrderByEventDateDescIdDesc(1L)).thenReturn(ALL);

        String out = new GetRandonneuringRecordTool(repo).execute(OM.readTree("{\"year\":2025,\"status\":\"dnf\"}"), 1L, TODAY);

        assertThat(out).startsWith("전체 경력: 공식 참가 8회").contains("조회 2025년 DNF: 2건 (최신순)")
                .contains("- 2025-09-27 [BREVET] 광주-부산-광주 600K 600km DNF")
                .contains("- 2025-04-19 [FLECHE] 플레쉬-2 거리 미상 DNF")
                .doesNotContain("천안 200K");
        assertThatThrownBy(() -> new GetRandonneuringRecordTool(repo).execute(OM.readTree("{\"status\":\"DONE\"}"), 1L, TODAY))
                .hasMessageContaining("FINISHED 또는 DNF");
    }
}
