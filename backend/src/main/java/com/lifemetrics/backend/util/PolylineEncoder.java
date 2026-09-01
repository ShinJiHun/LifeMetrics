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
