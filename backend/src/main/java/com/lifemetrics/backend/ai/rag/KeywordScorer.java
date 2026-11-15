package com.lifemetrics.backend.ai.rag;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 외부 호출 없는 키워드 관련도 점수 (애슐리트 RAG·개발자 경험 카드 검색 공용).
 * 점수 = 0.7 × (질문 단어 중 본문에 있는 비율) + 0.3 × (질문의 2글자 조각 중 본문에 있는 비율). 0~1.
 * 2글자 조각은 "무릎이/무릎을"처럼 조사가 붙은 한국어를 어느 정도 맞춘다.
 */
public final class KeywordScorer {

    private final Set<String> terms;
    private final Set<String> grams;

    private KeywordScorer(Set<String> terms, Set<String> grams) {
        this.terms = terms;
        this.grams = grams;
    }

    public static KeywordScorer of(String query) {
        String q = normalize(query);
        Set<String> terms = new LinkedHashSet<>();
        for (String t : q.split(" ")) if (t.length() >= 2) terms.add(t);
        return new KeywordScorer(terms, bigrams(q));
    }

    public double score(String text) {
        if (terms.isEmpty() && grams.isEmpty()) return 0;
        String t = normalize(text);
        long termHits = terms.stream().filter(t::contains).count();
        Set<String> textGrams = bigrams(t);
        long gramHits = grams.stream().filter(textGrams::contains).count();
        double termPart = terms.isEmpty() ? 0 : (double) termHits / terms.size();
        double gramPart = grams.isEmpty() ? 0 : (double) gramHits / grams.size();
        return 0.7 * termPart + 0.3 * gramPart;
    }

    static String normalize(String s) {
        return s == null ? "" : s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").strip();
    }

    static Set<String> bigrams(String s) {
        Set<String> out = new HashSet<>();
        for (String w : s.split(" ")) for (int i = 0; i + 2 <= w.length(); i++) out.add(w.substring(i, i + 2));
        return out;
    }
}
