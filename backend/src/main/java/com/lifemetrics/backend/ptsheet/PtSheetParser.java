package com.lifemetrics.backend.ptsheet;

import org.apache.poi.ss.usermodel.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 구글 시트 "헬스 운동기록"(xlsx export)의 "PT운동 일정" / "유산소" 탭 파싱.
 * data_pipeline/health/scripts/sync_pt_sheet.py 의 파싱 규칙을 그대로 옮겼다(DB 와 무관한 순수 로직 — 테스트 대상).
 */
public final class PtSheetParser {

    public static final String SHEET_TAB_NAME = "PT운동 일정";
    public static final String CARDIO_TAB_NAME = "유산소";
    /** 이 이상은 오타로 본다 (예: "2008/7/6"). */
    static final double MAX_SANE_WEIGHT = 500.0;

    private PtSheetParser() {}

    // ── 시트 행 ─────────────────────────────────────

    /** PT 탭 한 줄 (B~K 열: session_no|body_part|date|exercise_ko|exercise_en|weight|reps|sets|memo|영상 링크). */
    public record Row(String sessionNo, String bodyPart, String date, String exKo, String exEn,
                      String weight, String reps, String sets, String memo) {}

    public record ParsedNumbers(List<Double> values, String raw, String note) {}

    public record ExerciseEntry(String nameKo, String nameEn, String weightRaw, String repsRaw, int sets, String memo,
                                List<Double> perSetWeight, List<Double> perSetReps, List<String> notes) {}

    public record SessionEntry(String sessionNo, String bodyPart, String date, List<ExerciseEntry> exercises) {}

    public record CardioEntry(String date, String name, int occurrence, Double durationMin, Double distanceKm,
                              String speedIncline, Integer kcal, Integer avgHr, String memo) {
        public String marker() {
            return "[cardio#" + date + "#" + name + "#" + occurrence + "]";
        }
    }

    public static Workbook open(byte[] xlsx) throws IOException {
        return WorkbookFactory.create(new ByteArrayInputStream(xlsx));
    }

    /** PT 탭: 6행이 헤더, 7행부터 데이터. 모든 칸이 빈 줄은 건너뛴다. */
    public static List<Row> loadRows(Workbook wb, String tabName) {
        Sheet ws = wb.getSheet(tabName);
        if (ws == null) throw new IllegalStateException("시트에 '" + tabName + "' 탭이 없습니다.");
        List<Row> rows = new ArrayList<>();
        for (int r = 6; r <= ws.getLastRowNum(); r++) {
            org.apache.poi.ss.usermodel.Row row = ws.getRow(r);
            if (row == null) continue;
            Object[] v = new Object[10];
            boolean allNull = true;
            for (int c = 0; c < 10; c++) {
                v[c] = value(row.getCell(c + 1));   // B(1) ~ K(10)
                if (v[c] != null) allNull = false;
            }
            if (allNull) continue;
            rows.add(new Row(
                    fmtSessionNo(v[0]),
                    v[1] != null && !str(v[1]).isEmpty() ? str(v[1]).strip() : null,
                    fmtDate(v[2]),
                    v[3] == null ? null : str(v[3]),
                    v[4] == null ? null : str(v[4]),
                    v[5] == null ? "" : str(v[5]).strip(),
                    v[6] == null ? "" : str(v[6]).strip(),
                    v[7] == null ? "" : str(v[7]).strip(),
                    v[8] == null ? "" : str(v[8]).strip()));
        }
        return rows;
    }

    /** 유산소 탭: 6행 헤더(날짜|운동 종목|운동 시간 (분)|거리 (km)|속도 / 경사도|소모 칼로리|심박수|메모), B~I 열. 탭이 없으면 null. */
    public static List<CardioEntry> loadCardioRows(Workbook wb, String tabName) {
        Sheet ws = wb.getSheet(tabName);
        if (ws == null) return null;
        List<CardioEntry> out = new ArrayList<>();
        Map<String, Integer> seen = new HashMap<>();
        for (int r = 6; r <= ws.getLastRowNum(); r++) {
            org.apache.poi.ss.usermodel.Row row = ws.getRow(r);
            if (row == null) continue;
            Object[] v = new Object[8];
            for (int c = 0; c < 8; c++) v[c] = value(row.getCell(c + 1));
            Object date = v[0], name = v[1];
            if (date == null || name == null || str(name).isBlank()) continue;
            String d;
            if (date instanceof LocalDate ld) {
                d = ld.toString();
            } else {
                Matcher m = Pattern.compile("^(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})").matcher(str(date).strip());
                if (!m.find()) continue;
                d = String.format("%s-%02d-%02d", m.group(1), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
            }
            String n = str(name).strip();
            int occ = seen.merge(d + "\u0000" + n, 1, Integer::sum);
            Double k = num(v[5]), h = num(v[6]);
            out.add(new CardioEntry(d, n, occ, num(v[2]), num(v[3]),
                    v[4] != null ? str(v[4]).strip() : "",
                    k != null ? (int) roundHalfEven(k, 0) : null,
                    h != null ? (int) roundHalfEven(h, 0) : null,
                    v[7] != null ? str(v[7]).strip() : ""));
        }
        return out;
    }

    // ── 셀 값 ───────────────────────────────────────

    /** openpyxl(data_only=True) 처럼: 날짜 서식 → LocalDate, 숫자 → Double, 글자 → String, 빈 칸 → null. 수식은 캐시된 값. */
    static Object value(Cell cell) {
        if (cell == null) return null;
        CellType t = cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
        return switch (t) {
            case NUMERIC -> DateUtil.isCellDateFormatted(cell)
                    ? cell.getLocalDateTimeCellValue().toLocalDate() : (Object) cell.getNumericCellValue();
            case STRING -> {
                String s = cell.getStringCellValue();
                yield s.isEmpty() ? null : s;
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    /** 파이썬 str() 과 비슷하게: 정수 값 숫자는 "60", 아니면 "62.5". */
    static String str(Object v) {
        if (v == null) return "";
        if (v instanceof Double d) {
            if (d == Math.rint(d) && !Double.isInfinite(d)) return String.valueOf(d.longValue());
            return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
        }
        return v.toString();
    }

    static String fmtSessionNo(Object v) {
        if (v == null) return null;
        String s = str(v).strip();
        return s.isEmpty() ? null : s;
    }

    static String fmtDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate d) return d.format(DateTimeFormatter.ISO_LOCAL_DATE);
        return str(v).strip();
    }

    static Double num(Object v) {
        if (v == null) return null;
        if (v instanceof Double d) return d;
        String s = str(v);
        if (s.isBlank()) return null;
        Matcher m = Pattern.compile("-?\\d+(\\.\\d+)?").matcher(s.replace(",", ""));
        return m.find() ? Double.parseDouble(m.group()) : null;
    }

    // ── 숫자 칸 해석 ─────────────────────────────────

    private static final Pattern NUM_RE = Pattern.compile("-?\\d+(\\.\\d+)?");
    private static final Pattern RANGE_RE = Pattern.compile("(\\d+(\\.\\d+)?)\\s*~\\s*(\\d+(\\.\\d+)?)");
    private static final Pattern NUMERIC_ONLY = Pattern.compile("[\\d/*. ]+");

    static List<Double> expandToken(String tok) {
        tok = tok.strip();
        if (tok.isEmpty()) return List.of();
        int star = tok.indexOf('*');
        if (star >= 0) {
            String base = tok.substring(0, star).strip(), rep = tok.substring(star + 1).strip();
            if (NUM_RE.matcher(base).matches() && !rep.isEmpty() && rep.chars().allMatch(Character::isDigit)) {
                return Collections.nCopies(Integer.parseInt(rep), Double.parseDouble(base));
            }
            return List.of();
        }
        if (NUM_RE.matcher(tok).matches()) return List.of(Double.parseDouble(tok));
        return List.of();
    }

    /**
     * 무게·횟수 칸 → 세트별 값. "60/70/80" 은 세트별, 값 하나면 전 세트 같은 값, "10~12" 는 평균,
     * "60*3" 은 60 세 번. 숫자가 아니거나 애매하면 null 로 두고 note 에 원문을 남긴다.
     */
    public static ParsedNumbers parseNumericField(String raw, int sets) {
        if (raw == null) return new ParsedNumbers(nulls(sets), "", "");
        String s = raw.strip();
        if (s.isEmpty() || s.equals("-") || s.equals("/")) return new ParsedNumbers(nulls(sets), s, "");

        Matcher m = RANGE_RE.matcher(s);
        if (m.matches()) {
            double avg = (Double.parseDouble(m.group(1)) + Double.parseDouble(m.group(3))) / 2;
            return new ParsedNumbers(new ArrayList<>(Collections.nCopies(Math.max(sets, 1), avg)), s, "범위값 평균 처리(원본:" + s + ")");
        }
        if (!NUMERIC_ONLY.matcher(s).matches()) return new ParsedNumbers(nulls(sets), s, "숫자 아님, 원문 보존: " + s);

        List<Double> values = new ArrayList<>();
        for (String t : s.split("/", -1)) {
            if (t.strip().isEmpty()) continue;
            values.addAll(expandToken(t));
        }
        String note = "";
        List<Double> cleaned = new ArrayList<>();
        for (Double v : values) {
            if (v != null && v > MAX_SANE_WEIGHT) {
                note = (note.isEmpty() ? "" : note + "; ") + "비정상값 제외(" + v + ")";
                cleaned.add(null);
            } else {
                cleaned.add(v);
            }
        }
        values = cleaned;
        if (values.isEmpty()) return new ParsedNumbers(nulls(sets), s, note.isEmpty() ? "파싱 실패" : note);

        int n = Math.max(sets, 1);
        if (values.size() == n) return new ParsedNumbers(values, s, note);
        if (values.size() == 1) return new ParsedNumbers(new ArrayList<>(Collections.nCopies(n, values.get(0))), s, note);
        if (values.size() > n) {
            note = (note.isEmpty() ? "" : note + "; ") + "세트수(" + sets + ")보다 값이 많아 앞 " + n + "개만 사용";
            return new ParsedNumbers(new ArrayList<>(values.subList(0, n)), s, note);
        }
        note = (note.isEmpty() ? "" : note + "; ") + "세트수(" + sets + ")보다 값이 적어 마지막 값으로 채움";
        Double last = values.get(values.size() - 1);
        while (values.size() < n) values.add(last);
        return new ParsedNumbers(values, s, note);
    }

    public static int parseIntSets(String raw) {
        if (raw == null) return 1;
        String s = raw.strip();
        if (s.isEmpty() || s.equals("-") || s.equals("/")) return 1;
        try {
            return Math.max(1, (int) Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    /** session_no 가 있는 줄에서 새 세션을 시작하고, 비어 있는 줄은 바로 위 세션의 운동으로 본다(병합 셀). */
    public static List<SessionEntry> buildSessions(List<Row> rows) {
        List<SessionEntry> sessions = new ArrayList<>();
        SessionEntry cur = null;
        for (Row row : rows) {
            if (row.sessionNo() != null) {
                cur = new SessionEntry(row.sessionNo(), row.bodyPart(), row.date(), new ArrayList<>());
                sessions.add(cur);
            }
            boolean hasName = (row.exKo() != null && !row.exKo().isEmpty()) || (row.exEn() != null && !row.exEn().isEmpty());
            if (cur == null || !hasName) continue;

            int sets = parseIntSets(row.sets());
            ParsedNumbers w = parseNumericField(row.weight(), sets);
            ParsedNumbers r = parseNumericField(row.reps(), sets);
            int n = Math.max(Math.max(w.values().size(), r.values().size()), sets);
            List<Double> pw = new ArrayList<>(w.values()), pr = new ArrayList<>(r.values());
            while (pw.size() < n) pw.add(null);
            while (pr.size() < n) pr.add(null);
            List<String> notes = new ArrayList<>();
            if (!w.note().isEmpty()) notes.add(w.note());
            if (!r.note().isEmpty()) notes.add(r.note());

            cur.exercises().add(new ExerciseEntry(
                    row.exKo() == null ? "" : row.exKo().strip(),
                    row.exEn() == null ? "" : row.exEn().strip(),
                    row.weight(), row.reps(), n, row.memo() == null ? "" : row.memo().strip(), pw, pr, notes));
        }
        return sessions;
    }

    // ── 종목 분류 ────────────────────────────────────

    private static final Map<String, String> TYPO_FIX = new LinkedHashMap<>(Map.of("둥", "등", "허채", "하체"));
    private static final Set<String> NON_MUSCLE_LABELS = Set.of("서킷", "슈퍼 세트", "소근육");
    private static final List<Map.Entry<Pattern, String>> KEYWORD_CATEGORY = List.of(
            kw("squat|런지|lunge|leg|레그|스쿼트|힙|hip|calf|카프|어덕션|abduction|adduction|deadlift|데드", "하체"),
            kw("bench|체스트|chest|플라이|fly|딥스|dip|푸쉬업|push[- ]?up", "가슴"),
            kw("row|로우|pulldown|풀다운|풀 다운|pull[- ]?up|풀업|lat\\b|랫", "등"),
            kw("shoulder|숄더|delt|레터럴|lateral|프론트 레이즈|front raise|오버헤드 프레스|overhead press|military|밀리터리|비하인드|behind.*neck|업라이트|upright|face pull|페이스\\s*풀", "어깨"),
            kw("curl.*bicep|바이셉|바이 셉|이두", "이두"),
            kw("extension|익스텐션|pushdown|푸쉬\\s*다운|킥백|kickback|삼두|tricep", "삼두"),
            kw("sit[- ]?up|싯업|abdominal|복근|leg raise|레그 레이즈|플랭크|plank|crunch|크런치", "복근"));

    private static Map.Entry<Pattern, String> kw(String regex, String cat) {
        return Map.entry(Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), cat);
    }

    public static List<String> normalizeBodyPartTokens(String raw) {
        if (raw == null || raw.isEmpty()) return List.of();
        String s = raw.strip().replaceAll("\\([^)]*\\)", "");
        List<String> tokens = new ArrayList<>();
        for (String p : s.split("[/,]")) {
            p = p.strip();
            if (p.isEmpty()) continue;
            for (Map.Entry<String, String> e : TYPO_FIX.entrySet()) p = p.replace(e.getKey(), e.getValue());
            tokens.add(p);
        }
        return tokens;
    }

    public static String inferCategory(String nameKo, String nameEn, List<String> fallbackTokens) {
        String hay = ((nameKo == null ? "" : nameKo) + " " + (nameEn == null ? "" : nameEn)).toLowerCase(Locale.ROOT);
        for (Map.Entry<Pattern, String> e : KEYWORD_CATEGORY) {
            if (e.getKey().matcher(hay).find()) return e.getValue();
        }
        for (String tok : fallbackTokens) {
            if (!NON_MUSCLE_LABELS.contains(tok)) return tok;
        }
        return "전신";
    }

    /** 이름 비교용: 공백 제거 + 소문자. */
    public static String normName(String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    // ── 숫자 ───────────────────────────────────────

    /** 파이썬 round() 와 같은 은행가 반올림. */
    public static double roundHalfEven(double v, int scale) {
        return BigDecimal.valueOf(v).setScale(scale, RoundingMode.HALF_EVEN).doubleValue();
    }

    private static List<Double> nulls(int n) {
        return new ArrayList<>(Collections.nCopies(Math.max(n, 0), (Double) null));
    }
}
