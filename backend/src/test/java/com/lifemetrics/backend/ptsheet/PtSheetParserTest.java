package com.lifemetrics.backend.ptsheet;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** sync_pt_sheet.py 의 parse_numeric_field·build_sessions·load_cardio_rows 와 같은 결과가 나오는지. */
class PtSheetParserTest {

    @Test
    void 세트별_값_하나값_범위_곱하기() {
        assertThat(PtSheetParser.parseNumericField("60/70/80", 3).values()).containsExactly(60.0, 70.0, 80.0);
        assertThat(PtSheetParser.parseNumericField("60", 3).values()).containsExactly(60.0, 60.0, 60.0);
        var range = PtSheetParser.parseNumericField("10~12", 3);
        assertThat(range.values()).containsExactly(11.0, 11.0, 11.0);
        assertThat(range.note()).contains("범위값 평균");
        assertThat(PtSheetParser.parseNumericField("60*3", 3).values()).containsExactly(60.0, 60.0, 60.0);
        assertThat(PtSheetParser.parseNumericField("40/50*2", 3).values()).containsExactly(40.0, 50.0, 50.0);
    }

    @Test
    void 빈값_글자_오타_세트수_불일치() {
        assertThat(PtSheetParser.parseNumericField("", 2).values()).containsExactly(null, null);
        assertThat(PtSheetParser.parseNumericField("-", 2).values()).containsExactly(null, null);
        var text = PtSheetParser.parseNumericField("맨몸", 2);
        assertThat(text.values()).containsExactly(null, null);
        assertThat(text.note()).isEqualTo("숫자 아님, 원문 보존: 맨몸");
        var typo = PtSheetParser.parseNumericField("2008/7/6", 3);
        assertThat(typo.values()).containsExactly(null, 7.0, 6.0);
        assertThat(typo.note()).contains("비정상값 제외");
        var more = PtSheetParser.parseNumericField("10/10/10/10", 3);
        assertThat(more.values()).containsExactly(10.0, 10.0, 10.0);
        assertThat(more.note()).contains("앞 3개만");
        var fewer = PtSheetParser.parseNumericField("10/8", 4);
        assertThat(fewer.values()).containsExactly(10.0, 8.0, 8.0, 8.0);
        assertThat(fewer.note()).contains("마지막 값으로 채움");
    }

    @Test
    void 세트수() {
        assertThat(PtSheetParser.parseIntSets("4")).isEqualTo(4);
        assertThat(PtSheetParser.parseIntSets("3.0")).isEqualTo(3);
        assertThat(PtSheetParser.parseIntSets("")).isEqualTo(1);
        assertThat(PtSheetParser.parseIntSets("세트")).isEqualTo(1);
        assertThat(PtSheetParser.parseIntSets("0")).isEqualTo(1);
    }

    @Test
    void 종목_분류와_부위_토큰() {
        assertThat(PtSheetParser.inferCategory("바벨 스쿼트", "Barbell Squat", List.of())).isEqualTo("하체");
        assertThat(PtSheetParser.inferCategory("랫 풀다운", "", List.of())).isEqualTo("등");
        assertThat(PtSheetParser.inferCategory("알 수 없음", "", PtSheetParser.normalizeBodyPartTokens("서킷 / 둥(상부)")))
                .isEqualTo("등");
        assertThat(PtSheetParser.inferCategory("알 수 없음", "", List.of())).isEqualTo("전신");
        assertThat(PtSheetParser.normalizeBodyPartTokens("허채 / 삼두")).containsExactly("하체", "삼두");
    }

    @Test
    void 시트에서_세션과_유산소를_읽는다() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-mm-dd"));

            Sheet pt = wb.createSheet(PtSheetParser.SHEET_TAB_NAME);
            header(pt);
            // B: session_no, C: body_part, D: date, E: ko, F: en, G: weight, H: reps, I: sets, J: memo
            row(pt, 6, dateStyle, 12.0, "하체 / 삼두", LocalDate.of(2026, 11, 19), "스쿼트", "Squat", "60/70/80", 10.0, 3.0, "무릎 주의");
            row(pt, 7, dateStyle, null, null, null, "케이블 푸쉬다운", "", 25.0, "12", 3.0, null);
            row(pt, 8, dateStyle, null, null, null, null, null, null, null, null, null);   // 빈 줄
            row(pt, 9, dateStyle, 13.0, "등", "2026.11.20", "랫풀다운", "Lat Pulldown", "맨몸", "10~12", 2.0, null);

            Sheet cardio = wb.createSheet(PtSheetParser.CARDIO_TAB_NAME);
            header(cardio);
            // B: 날짜, C: 종목, D: 분, E: km, F: 속도/경사, G: kcal, H: 심박, I: 메모
            row(cardio, 6, dateStyle, LocalDate.of(2026, 11, 19), "러닝머신", 30.0, 4.5, "8.5 / 2", "250kcal", 140.0, "가볍게");
            row(cardio, 7, dateStyle, LocalDate.of(2026, 11, 19), "러닝머신", 10.0, null, null, null, null, null);
            row(cardio, 8, dateStyle, "날짜 없음", "천국의 계단", 10.0, null, null, null, null, null);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            xlsx = out.toByteArray();
        }

        try (Workbook wb = PtSheetParser.open(xlsx)) {
            var sessions = PtSheetParser.buildSessions(PtSheetParser.loadRows(wb, PtSheetParser.SHEET_TAB_NAME));
            assertThat(sessions).hasSize(2);
            var s12 = sessions.get(0);
            assertThat(s12.sessionNo()).isEqualTo("12");
            assertThat(s12.date()).isEqualTo("2026-11-19");
            assertThat(s12.exercises()).hasSize(2);
            assertThat(s12.exercises().get(0).perSetWeight()).containsExactly(60.0, 70.0, 80.0);
            assertThat(s12.exercises().get(0).perSetReps()).containsExactly(10.0, 10.0, 10.0);
            assertThat(s12.exercises().get(0).memo()).isEqualTo("무릎 주의");
            assertThat(s12.exercises().get(1).nameKo()).isEqualTo("케이블 푸쉬다운");
            assertThat(s12.exercises().get(1).perSetWeight()).containsExactly(25.0, 25.0, 25.0);

            var s13 = sessions.get(1);
            assertThat(s13.date()).isEqualTo("2026.11.20");
            assertThat(PtSheetSyncService.parseDate(s13.date())).isEqualTo(LocalDate.of(2026, 11, 20));
            assertThat(s13.exercises().get(0).perSetWeight()).containsExactly(null, null);
            assertThat(s13.exercises().get(0).notes()).hasSize(2);

            var cardio = PtSheetParser.loadCardioRows(wb, PtSheetParser.CARDIO_TAB_NAME);
            assertThat(cardio).hasSize(2);
            assertThat(cardio.get(0).marker()).isEqualTo("[cardio#2026-11-19#러닝머신#1]");
            assertThat(cardio.get(1).marker()).isEqualTo("[cardio#2026-11-19#러닝머신#2]");
            assertThat(cardio.get(0).kcal()).isEqualTo(250);
            assertThat(cardio.get(0).avgHr()).isEqualTo(140);
            assertThat(cardio.get(0).speedIncline()).isEqualTo("8.5 / 2");
            assertThat(PtSheetParser.loadCardioRows(wb, "없는 탭")).isNull();
        }
    }

    @Test
    void 평균은_파이썬처럼_은행가_반올림() {
        assertThat(PtSheetSyncService.avg(Arrays.asList(10.0, 11.0), 0).intValue()).isEqualTo(10);   // 10.5 → 10
        assertThat(PtSheetSyncService.avg(Arrays.asList(11.0, 12.0), 0).intValue()).isEqualTo(12);   // 11.5 → 12
        assertThat(PtSheetSyncService.avg(Arrays.asList(60.0, null, 70.0), 2).doubleValue()).isEqualTo(65.0);
        assertThat(PtSheetSyncService.avg(Arrays.asList(null, null), 2)).isNull();
    }

    private static void header(Sheet s) {
        s.createRow(5).createCell(1).setCellValue("header");
    }

    private static void row(Sheet s, int r, CellStyle dateStyle, Object... values) {
        Row row = s.createRow(r);
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            if (v == null) continue;
            Cell c = row.createCell(i + 1);
            if (v instanceof Double d) c.setCellValue(d);
            else if (v instanceof LocalDate d) {
                c.setCellValue(d);
                c.setCellStyle(dateStyle);
            } else c.setCellValue(v.toString());
        }
    }
}
