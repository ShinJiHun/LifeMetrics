package com.lifemetrics.backend.randonneuring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifemetrics.backend.entity.ActivityCore;
import com.lifemetrics.backend.entity.RandonneuringResult;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.PermanentCourseRepository;
import com.lifemetrics.backend.repository.RandonneuringResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RandonneuringImportServiceTest {

    private static final ObjectMapper OM = new ObjectMapper();

    private static RandonneuringImportService.Item parse(String json) throws Exception {
        return RandonneuringImportService.parse(OM.readTree(json));
    }

    @Test
    void KORA_항목을_정규화한다() throws Exception {
        var brevet = parse("""
                {"source":"kora","status":"finished","event_date":"2026-05-02","riding_time_sec":141720,
                 "ride_type_code":"BREVET","course_name":"서울 600K-동","distance_km":"600.00",
                 "kora_source_id":"result:21380:2270:2026-05-02"}""");
        assertThat(brevet).isEqualTo(new RandonneuringImportService.Item("result:21380:2270:2026-05-02",
                LocalDate.of(2026, 5, 2), "KORA", "BREVET", "서울 600K-동", null, null, 600, "FINISHED", 141720, null));

        // 플레쉬는 API 에서 BREVET 코드지만 FLECHE 로, DNF 는 완주 시간을 버린다
        var fleche = parse("""
                {"source":"kora","status":"dnf","event_date":"2025-04-19","ride_type_code":"BREVET",
                 "course_name":"플레쉬-2","distance_km":"360.00","riding_time_sec":999,"kora_source_id":"k1"}""");
        assertThat(fleche.type()).isEqualTo("FLECHE");
        assertThat(fleche.seconds()).isNull();

        // e-brevet 항목: event_date 없음 → started_at, 원본 id 는 ebr:<entry_id>, 숫자 거리
        var ebr = parse("""
                {"entry_id":1132,"status":"finished","event_date":null,"started_at":"2026-08-09 05:24:11",
                 "ride_type_code":"PERMANENT","permanent_no":"PT-87","course_name":"진도 루프","distance_km":206}""");
        assertThat(ebr.sourceId()).isEqualTo("ebr:1132");
        assertThat(ebr.date()).isEqualTo(LocalDate.of(2026, 8, 9));
        assertThat(ebr.organizer()).isNull();
        assertThat(RandonneuringImportService.displayName(ebr)).isEqualTo("[PT-87] 진도 루프");
    }

    @Test
    void 퍼머넌트_번호는_코스명에서도_찾고_가지번호는_기본번호로_묶는다() throws Exception {
        var noNumber = parse("""
                {"source":"kora","status":"dnf","event_date":"2025-03-22","ride_type_code":"PERMANENT",
                 "permanent_no":null,"course_name":"PT-188 전라 날다람쥐","distance_km":"203.00"}""");
        assertThat(noNumber.permanentNo()).isEqualTo("PT-188");
        assertThat(RandonneuringImportService.displayName(noNumber)).isEqualTo("[PT-188] 전라 날다람쥐");

        var branch = parse("""
                {"source":"kora","status":"finished","event_date":"2025-08-08","ride_type_code":"PERMANENT",
                 "permanent_no":"PT-137-1","course_name":"PT-137 돌고돌아 안동","distance_km":"211.00"}""");
        assertThat(branch.permanentNo()).isEqualTo("PT-137");
        assertThat(branch.officialNo()).isEqualTo("PT-137-1");
        assertThat(RandonneuringImportService.displayName(branch)).isEqualTo("[PT-137-1] 돌고돌아 안동");
    }

    @Test
    void 상태나_날짜가_없으면_항목을_건너뛴다() {
        assertThatThrownBy(() -> parse("{\"course_name\":\"x\",\"event_date\":\"2026-01-01\"}")).hasMessageContaining("status");
        assertThatThrownBy(() -> parse("{\"status\":\"finished\",\"course_name\":\"x\"}")).hasMessageContaining("날짜");
        assertThatThrownBy(() -> parse("{\"status\":\"ongoing\",\"event_date\":\"2026-01-01\"}")).hasMessageContaining("ONGOING");
    }

    private final RandonneuringResultRepository repo = mock(RandonneuringResultRepository.class);
    private final ActivityCoreRepository rides = mock(ActivityCoreRepository.class);
    private final PermanentCourseRepository courses = mock(PermanentCourseRepository.class);
    private final RandonneuringImportService service = new RandonneuringImportService(repo, rides, courses);

    private static ActivityCore ride(long id, double km, String type) {
        ActivityCore a = new ActivityCore();
        ReflectionTestUtils.setField(a, "id", id);
        ReflectionTestUtils.setField(a, "totalDistance", km * 1000);
        a.setRideType(type);
        return a;
    }

    @Test
    void 새_기록은_추가하고_같은_원본_id_는_갱신하며_GPS_라이딩을_잇고_유형을_채운다() throws Exception {
        RandonneuringResult known = new RandonneuringResult();
        known.setEventName("서울 600K-동");
        known.setActivityCoreId(99L);
        when(repo.findBySourceId("k-old")).thenReturn(Optional.of(known));
        when(repo.findBySourceId("k-new")).thenReturn(Optional.empty());
        when(repo.findByUserIdAndEventDateAndEventName(anyLong(), any(), anyString())).thenReturn(Optional.empty());
        when(repo.countByUserIdAndEventDate(1L, LocalDate.of(2027, 3, 6))).thenReturn(1L);
        ActivityCore shortRide = ride(1, 20, null), longRide = ride(2, 207, null);
        when(rides.findByUserIdAndDateRange(eq(1L), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(List.of(shortRide, longRide));
        when(courses.existsById("PT-102")).thenReturn(true);

        var result = service.importKora(1L, OM.readTree("""
                {"items":[
                 {"source":"kora","status":"finished","event_date":"2026-05-02","ride_type_code":"BREVET",
                  "course_name":"서울 600K-동","distance_km":"600.00","riding_time_sec":141720,"kora_source_id":"k-old"},
                 {"source":"kora","status":"finished","event_date":"2027-03-06","ride_type_code":"PERMANENT",
                  "permanent_no":"PT-102","course_name":"PT-102 중부 200","distance_km":"207.00",
                  "riding_time_sec":42000,"kora_source_id":"k-new"},
                 {"course_name":"상태 없음"}]}"""));

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.updated()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.linked()).isEqualTo(1);
        assertThat(result.tagged()).isEqualTo(1);
        assertThat(known.getRidingTimeSec()).isEqualTo(141720);
        assertThat(longRide.getRideType()).isEqualTo("PERMANENT");
        assertThat(longRide.getPermanentNo()).isEqualTo("PT-102");
        assertThat(shortRide.getRideType()).isNull();
    }

    @Test
    void 같은_날_대회가_둘이면_GPS_를_잇지_않는다() throws Exception {
        when(repo.findBySourceId(anyString())).thenReturn(Optional.empty());
        when(repo.findByUserIdAndEventDateAndEventName(anyLong(), any(), anyString())).thenReturn(Optional.empty());
        when(repo.countByUserIdAndEventDate(eq(1L), any())).thenReturn(2L);

        var result = service.importKora(1L, OM.readTree("""
                [{"source":"kora","status":"finished","event_date":"2026-06-06","ride_type_code":"PERMANENT",
                  "permanent_no":"PT-174","course_name":"PT-174 거창 돼지구이","distance_km":"204.00","kora_source_id":"a"}]"""));

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.linked()).isZero();
        verify(rides, never()).findByUserIdAndDateRange(anyLong(), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void items_가_없으면_거절한다() {
        assertThatThrownBy(() -> service.importKora(1L, OM.readTree("{\"ok\":true}"))).hasMessageContaining("items");
    }
}
