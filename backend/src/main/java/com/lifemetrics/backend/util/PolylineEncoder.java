package com.lifemetrics.backend.util;

import com.lifemetrics.backend.entity.ActivityPoint;

import java.util.List;

/**
 * Google encoded polyline algorithm (precision 5). 프론트 @mapbox/polyline 디코더와
 * 동일한 포맷이어야 지도에 그려진다.
 */
public final class PolylineEncoder {

    private PolylineEncoder() {
    }

    public static String encode(List<ActivityPoint> points) {
        return encodeLatLng(points.stream()
                .filter(p -> p.getLat() != null && p.getLon() != null)
                .map(p -> new double[]{p.getLat(), p.getLon()})
                .toList());
    }

    /** GPX 등 ActivityPoint가 아닌 단순 [lat, lon] 목록을 인코딩할 때 사용 */
    public static String encodeLatLng(List<double[]> latLngs) {
        StringBuilder result = new StringBuilder();
        long lastLat = 0;
        long lastLng = 0;

        for (double[] p : latLngs) {
            long lat = Math.round(p[0] * 1e5);
            long lng = Math.round(p[1] * 1e5);

            encodeSignedNumber(lat - lastLat, result);
            encodeSignedNumber(lng - lastLng, result);

            lastLat = lat;
            lastLng = lng;
        }

        return result.toString();
    }

    /** encodeLatLng 의 역. [lat, lon] 목록을 돌려준다. */
    public static List<double[]> decode(String encoded) {
        List<double[]> coords = new java.util.ArrayList<>();
        if (encoded == null) return coords;
        int index = 0;
        long lat = 0;
        long lng = 0;
        int len = encoded.length();
        while (index < len) {
            long[] r = decodeNumber(encoded, index);
            lat += r[0];
            index = (int) r[1];
            if (index >= len) break;
            r = decodeNumber(encoded, index);
            lng += r[0];
            index = (int) r[1];
            coords.add(new double[]{lat / 1e5, lng / 1e5});
        }
        return coords;
    }

    /** {부호 있는 값, 다음 index} */
    private static long[] decodeNumber(String encoded, int index) {
        long result = 0;
        int shift = 0;
        int b;
        do {
            b = encoded.charAt(index++) - 63;
            result |= (long) (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20 && index < encoded.length());
        long value = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new long[]{value, index};
    }

    /**
     * Douglas-Peucker 단순화. tolerance 는 위경도(도) 단위이며 경도는 위도에 맞춰 보정한다.
     * 지도 전체 보기용으로 점 수를 크게 줄일 때 쓴다.
     */
    public static List<double[]> simplify(List<double[]> pts, double tolerance) {
        int n = pts.size();
        if (n < 3) return pts;
        double cosLat = Math.cos(Math.toRadians(pts.get(n / 2)[0]));
        boolean[] keep = new boolean[n];
        keep[0] = true;
        keep[n - 1] = true;
        java.util.ArrayDeque<int[]> stack = new java.util.ArrayDeque<>();
        stack.push(new int[]{0, n - 1});
        double tol2 = tolerance * tolerance;
        while (!stack.isEmpty()) {
            int[] seg = stack.pop();
            int s = seg[0], e = seg[1];
            if (e - s < 2) continue;
            double ax = pts.get(s)[1] * cosLat, ay = pts.get(s)[0];
            double bx = pts.get(e)[1] * cosLat, by = pts.get(e)[0];
            double dx = bx - ax, dy = by - ay;
            double segLen2 = dx * dx + dy * dy;
            int maxIdx = -1;
            double maxD2 = tol2;
            for (int i = s + 1; i < e; i++) {
                double px = pts.get(i)[1] * cosLat, py = pts.get(i)[0];
                double t = segLen2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / segLen2));
                double qx = ax + t * dx - px, qy = ay + t * dy - py;
                double d2 = qx * qx + qy * qy;
                if (d2 > maxD2) {
                    maxD2 = d2;
                    maxIdx = i;
                }
            }
            if (maxIdx != -1) {
                keep[maxIdx] = true;
                stack.push(new int[]{s, maxIdx});
                stack.push(new int[]{maxIdx, e});
            }
        }
        List<double[]> out = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) if (keep[i]) out.add(pts.get(i));
        return out;
    }

    private static void encodeSignedNumber(long num, StringBuilder result) {
        long sgnNum = num << 1;
        if (num < 0) sgnNum = ~sgnNum;
        encodeNumber(sgnNum, result);
    }

    private static void encodeNumber(long num, StringBuilder result) {
        while (num >= 0x20) {
            result.append((char) ((0x20 | (num & 0x1f)) + 63));
            num >>= 5;
        }
        result.append((char) (num + 63));
    }
}
