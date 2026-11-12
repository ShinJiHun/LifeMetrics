package com.lifemetrics.backend.power;

import java.util.ArrayList;
import java.util.List;

/**
 * 5초·1분·5분·FTP 의 W/kg 를 Coggan 파워 프로필 표(남성) 범위에 대어 0~100 점으로 바꾸고,
 * 가장 높은 쪽으로 성향을 정한다. 표의 최하단(비훈련자)~최상단(월드클래스)을 선형으로 근사한 값이라
 * 절대 등급보다 "어느 쪽이 상대적으로 강한가"를 보는 용도다.
 */
public final class RiderTypeClassifier {

    private RiderTypeClassifier() {}

    public record Axis(String key, String label, int seconds, double minWkg, double maxWkg) {}

    public static final List<Axis> AXES = List.of(
            new Axis("5s", "5초 (스프린트)", 5, 10.17, 25.18),
            new Axis("1m", "1분 (무산소)", 60, 5.64, 11.50),
            new Axis("5m", "5분 (VO2max)", 300, 2.33, 7.60),
            new Axis("ft", "FTP (역치)", 0, 1.86, 6.40));

    /** 점수가 이만큼 안에 다 모여 있으면 균형형 */
    static final int BALANCED_SPREAD = 10;

    public record Score(String key, String label, double wkg, int score) {}

    public record Result(String type, String label, String description, List<Score> scores) {}

    public static int score(Axis a, double wkg) {
        double s = (wkg - a.minWkg()) / (a.maxWkg() - a.minWkg()) * 100;
        return (int) Math.round(Math.max(0, Math.min(100, s)));
    }

    /** wkg 순서는 AXES 와 같다 (5s, 1m, 5m, ft). 0 이하인 축은 기록 없음으로 본다. */
    public static Result classify(double[] wkg) {
        List<Score> scores = new ArrayList<>();
        for (int i = 0; i < AXES.size(); i++) {
            Axis a = AXES.get(i);
            scores.add(new Score(a.key(), a.label(), round2(wkg[i]), wkg[i] > 0 ? score(a, wkg[i]) : 0));
        }
        if (scores.stream().anyMatch(s -> s.wkg() <= 0)) {
            return new Result("unknown", "판정 불가", "파워 기록이 부족해 성향을 정하지 못했습니다.", scores);
        }
        int max = scores.stream().mapToInt(Score::score).max().orElse(0);
        int min = scores.stream().mapToInt(Score::score).min().orElse(0);
        if (max - min < BALANCED_SPREAD) {
            return new Result("allrounder", "올라운더", "짧은 폭발력부터 역치까지 고르게 갖춘 균형형입니다.", scores);
        }
        Score top = scores.stream().max(java.util.Comparator.comparingInt(Score::score)).orElseThrow();
        return switch (top.key()) {
            case "5s" -> new Result("sprinter", "스프린터", "몇 초 동안 터뜨리는 최고 출력이 상대적으로 가장 강합니다.", scores);
            case "1m" -> new Result("puncheur", "펀처", "1분 안팎의 무산소 노력(짧은 언덕·어택)이 상대적으로 강합니다.", scores);
            case "5m" -> new Result("pursuiter", "퍼서·클라이머", "3~8분 고강도(VO2max) 능력이 상대적으로 강해 중간 길이 오르막에 유리합니다.", scores);
            default -> new Result("tt", "TT·지구력형", "역치 파워를 오래 유지하는 능력이 상대적으로 가장 강합니다. 장거리·평지 독주에 유리합니다.", scores);
        };
    }

    private static double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
