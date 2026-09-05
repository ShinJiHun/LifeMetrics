package com.lifemetrics.backend.lotto.util;

import java.util.Arrays;

/**
 * 번호 6개(1~45) 조합에 대한 홀짝/고저/합계/연속번호 등 파생 통계 계산.
 * 로또 번호 통계·티켓 분석에서 공용으로 사용한다.
 */
public final class LottoStatsUtil {

    /** 저(low) 구간 상한. 1~22 = 저, 23~45 = 고. */
    public static final int LOW_HIGH_BOUNDARY = 22;

    private LottoStatsUtil() {}

    public static int oddCount(int[] numbers) {
        int c = 0;
        for (int n : numbers) if (n % 2 != 0) c++;
        return c;
    }

    public static int evenCount(int[] numbers) {
        return numbers.length - oddCount(numbers);
    }

    public static int lowCount(int[] numbers) {
        int c = 0;
        for (int n : numbers) if (n <= LOW_HIGH_BOUNDARY) c++;
        return c;
    }

    public static int highCount(int[] numbers) {
        return numbers.length - lowCount(numbers);
    }

    public static int sum(int[] numbers) {
        int s = 0;
        for (int n : numbers) s += n;
        return s;
    }

    /** 연속된 번호(예: 12,13) 쌍의 개수. */
    public static int consecutivePairCount(int[] numbers) {
        int[] sorted = numbers.clone();
        Arrays.sort(sorted);
        int c = 0;
        for (int i = 1; i < sorted.length; i++) {
            if (sorted[i] - sorted[i - 1] == 1) c++;
        }
        return c;
    }

    public static String oddEvenLabel(int[] numbers) {
        return oddCount(numbers) + ":" + evenCount(numbers);
    }

    public static String lowHighLabel(int[] numbers) {
        return lowCount(numbers) + ":" + highCount(numbers);
    }
}
