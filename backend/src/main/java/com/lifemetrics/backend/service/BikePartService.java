package com.lifemetrics.backend.service;

import com.lifemetrics.backend.entity.Bike;
import com.lifemetrics.backend.entity.BikePart;
import com.lifemetrics.backend.repository.ActivityCoreRepository;
import com.lifemetrics.backend.repository.BikePartRepository;
import com.lifemetrics.backend.repository.BikeRepository;
import com.lifemetrics.backend.repository.BikeRideDistance;
import com.lifemetrics.backend.repository.GearDistanceRow;
import com.lifemetrics.backend.repository.GearUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 장비 변경 계획: 자전거 소모품의 사용 거리·기간을 교체 주기와 비교한다.
 * <p>
 * 사용 거리 = initial_km + (장착일 ≤ 라이딩 날짜 &lt; 탈거일) 인 라이딩 거리 합.
 * 교체 예상일은 그 자전거의 최근 {@value #RECENT_DAYS}일 평균 주행 거리로 남은 거리를 나눠 추정한다.
 */
@Service
@RequiredArgsConstructor
public class BikePartService {

    static final int RECENT_DAYS = 90;
    /** 이 비율 이상 쓰면 '곧 교체' */
    static final double SOON_RATIO = 0.8;

    public static final Set<String> PART_TYPES = Set.of(
            "CHAIN", "TIRE_FRONT", "TIRE_REAR", "CASSETTE", "CHAINRING",
            "BRAKE_PAD", "BAR_TAPE", "CABLE", "SEALANT", "OTHER");

    private final BikePartRepository partRepository;
    private final BikeRepository bikeRepository;
    private final ActivityCoreRepository activityCoreRepository;
    private final GearUsageRepository gearUsageRepository;

    public record PartUsage(
            Long id, Long bikeId, String partType, String name,
            LocalDate installedOn, LocalDate removedOn,
            double initialKm, Integer intervalKm, Integer intervalMonths, String note,
            double usedKm, long usedDays,
            /** 거리·기간 중 더 많이 쓴 쪽 비율. 주기가 없으면 null */
            Double ratio,
            /** OK / SOON / OVERDUE / NONE(주기 없음) / REMOVED */
            String status,
            Double remainingKm,
            /** 거리·기간 기준 중 더 이른 교체 예상일. 추정 불가면 null */
            LocalDate expectedReplaceOn) {}

    /** 톱니수별 주행 거리 */
    public record GearKm(int teeth, double km) {}

    /**
     * frontGears/rearGears: 체인링·스프라켓(코그) 톱니수별 거리. 사용 중인 CHAINRING/CASSETTE 부품이 등록돼 있으면
     * 그 장착일(frontSince/rearSince) 이후만, 없으면 전체 기간을 합산한다. 기어 기록이 있는 라이딩만 포함된다.
     */
    public record BikePlan(Long bikeId, String bikeName, boolean retired, double totalKm, double recentKmPerDay,
                           List<PartUsage> parts, List<PartUsage> history,
                           LocalDate frontSince, List<GearKm> frontGears,
                           LocalDate rearSince, List<GearKm> rearGears) {}

    @Transactional(readOnly = true)
    public List<BikePlan> plan(boolean includeRetired) {
        List<Bike> bikes = bikeRepository.findAllByOrderByIsRetiredAscIsDefaultDescIdDesc().stream()
                .filter(b -> includeRetired || !Boolean.TRUE.equals(b.getIsRetired()))
                .toList();
        if (bikes.isEmpty()) return List.of();
        List<Long> ids = bikes.stream().map(Bike::getId).toList();

        Map<Long, List<BikeRideDistance>> ridesByBike = activityCoreRepository.findRideDistancesByBikeIds(ids)
                .stream().collect(Collectors.groupingBy(BikeRideDistance::getBikeId));
        Map<Long, List<GearDistanceRow>> gearsByBike = gearUsageRepository.findGearDistancesByBikeIds(ids)
                .stream().collect(Collectors.groupingBy(GearDistanceRow::getBikeId));
        Map<Long, List<BikePart>> partsByBike = partRepository.findByBikeIdInOrderByInstalledOnDesc(ids)
                .stream().collect(Collectors.groupingBy(BikePart::getBikeId));

        LocalDate today = LocalDate.now();
        List<BikePlan> result = new ArrayList<>();
        for (Bike bike : bikes) {
            List<BikeRideDistance> rides = ridesByBike.getOrDefault(bike.getId(), List.of());
            double kmPerDay = recentKmPerDay(rides, today);
            List<PartUsage> active = new ArrayList<>();
            List<PartUsage> history = new ArrayList<>();
            for (BikePart p : partsByBike.getOrDefault(bike.getId(), List.of())) {
                PartUsage u = usage(p, rides, today, kmPerDay);
                (p.getRemovedOn() == null ? active : history).add(u);
            }
            active.sort(Comparator.comparing((PartUsage u) -> u.ratio() == null ? -1 : u.ratio()).reversed());
            LocalDate frontSince = activeInstalledOn(active, "CHAINRING");
            LocalDate rearSince = activeInstalledOn(active, "CASSETTE");
            List<GearDistanceRow> gears = gearsByBike.getOrDefault(bike.getId(), List.of());
            double totalKm = rides.stream()
                    .mapToDouble(r -> r.getTotalDistance() == null ? 0 : r.getTotalDistance()).sum() / 1000.0;
            result.add(new BikePlan(bike.getId(), bike.getName(), Boolean.TRUE.equals(bike.getIsRetired()),
                    round1(totalKm), round1(kmPerDay), active, history,
                    frontSince, gearKm(gears, frontSince, true),
                    rearSince, gearKm(gears, rearSince, false)));
        }
        return result;
    }

    @Transactional
    public BikePart create(BikePart req) {
        validate(req);
        BikePart p = new BikePart();
        p.setBikeId(req.getBikeId());
        apply(p, req);
        return partRepository.save(p);
    }

    @Transactional
    public Optional<BikePart> update(Long id, BikePart req) {
        return partRepository.findById(id).map(p -> {
            req.setBikeId(p.getBikeId());
            validate(req);
            apply(p, req);
            return partRepository.save(p);
        });
    }

    /**
     * 순정 소모품 일괄 등록. 장착일 = 구입일 → 등록일 → 첫 라이딩일 순으로 정한다.
     * 이미 사용 중인 같은 종류 부품이 있으면 그 종류는 건너뛴다(여러 번 눌러도 중복 없음).
     * 전자식 변속(Di2/AXS/eTap)이면 변속 케이블은 넣지 않는다.
     */
    @Transactional
    public List<BikePart> registerStock(Long bikeId) {
        Bike bike = bikeRepository.findById(bikeId)
                .orElseThrow(() -> new IllegalArgumentException("자전거를 찾을 수 없습니다."));
        LocalDate installedOn = bike.getPurchaseDate();
        if (installedOn == null && bike.getCreatedAt() != null) installedOn = bike.getCreatedAt().toLocalDate();
        if (installedOn == null) {
            installedOn = activityCoreRepository.findRideDistancesByBikeIds(List.of(bikeId)).stream()
                    .map(r -> r.getStartTime().toLocalDate()).min(Comparator.naturalOrder())
                    .orElse(LocalDate.now());
        }

        Set<String> activeTypes = partRepository.findByBikeIdInOrderByInstalledOnDesc(List.of(bikeId)).stream()
                .filter(p -> p.getRemovedOn() == null).map(BikePart::getPartType)
                .collect(Collectors.toSet());

        List<BikePart> created = new ArrayList<>();
        for (StockPart s : stockParts(bike)) {
            if (activeTypes.contains(s.type())) continue;
            BikePart p = new BikePart();
            p.setBikeId(bikeId);
            p.setPartType(s.type());
            p.setName(s.name());
            p.setInstalledOn(installedOn);
            p.setInitialKm(0.0);
            p.setIntervalKm(s.km());
            p.setIntervalMonths(s.months());
            p.setNote("순정 (자전거 구입일 기준 자동 등록)");
            created.add(partRepository.save(p));
        }
        return created;
    }

    record StockPart(String type, String name, Integer km, Integer months) {}

    static List<StockPart> stockParts(Bike bike) {
        String group = bike.getGroupset() != null && !bike.getGroupset().isBlank() ? bike.getGroupset().trim() : null;
        String speeds = bike.getSpeeds() != null ? " " + bike.getSpeeds() + "단" : "";
        String wheel = bike.getWheelSize() != null ? " " + bike.getWheelSize() : "";
        String rings = bike.getChainringOuter() != null
                ? " " + bike.getChainringOuter() + (bike.getChainringInner() != null ? "/" + bike.getChainringInner() : "") + "T"
                : "";
        String cassette = bike.getCassetteLabel() != null ? " " + bike.getCassetteLabel() : "";
        String prefix = "순정" + (group != null ? " " + group : "");

        List<StockPart> list = new ArrayList<>(List.of(
                new StockPart("CHAIN", prefix + " 체인" + speeds, 3000, null),
                new StockPart("TIRE_REAR", "순정 뒷타이어" + wheel, 4000, null),
                new StockPart("TIRE_FRONT", "순정 앞타이어" + wheel, 6000, null),
                new StockPart("CASSETTE", prefix + " 카세트" + cassette + speeds, 10000, null),
                new StockPart("CHAINRING", prefix + " 체인링" + rings, 20000, null),
                new StockPart("BRAKE_PAD", prefix + " 브레이크 패드", 3000, null),
                new StockPart("BAR_TAPE", "순정 바테이프", null, 12)));
        String shifting = ((group != null ? group : "") + " " + (bike.getShiftingType() != null ? bike.getShiftingType() : ""))
                .toLowerCase();
        boolean electronic = shifting.contains("di2") || shifting.contains("axs") || shifting.contains("etap")
                || shifting.contains("electr") || shifting.contains("전자");
        if (!electronic) list.add(new StockPart("CABLE", "순정 변속·브레이크 케이블", null, 12));
        return list;
    }

    /** 교체: 기존 부품을 date 로 탈거하고 같은 종류·주기의 새 부품을 date 에 장착한다. */
    @Transactional
    public Optional<BikePart> replace(Long id, LocalDate date, String newName, String note) {
        return partRepository.findById(id).map(old -> {
            LocalDate on = date != null ? date : LocalDate.now();
            if (on.isBefore(old.getInstalledOn()))
                throw new IllegalArgumentException("교체일이 장착일보다 빠릅니다.");
            old.setRemovedOn(on);
            partRepository.save(old);

            BikePart p = new BikePart();
            p.setBikeId(old.getBikeId());
            p.setPartType(old.getPartType());
            p.setName(newName != null && !newName.isBlank() ? newName.trim() : old.getName());
            p.setInstalledOn(on);
            p.setInitialKm(0.0);
            p.setIntervalKm(old.getIntervalKm());
            p.setIntervalMonths(old.getIntervalMonths());
            p.setNote(note);
            return partRepository.save(p);
        });
    }

    private void validate(BikePart req) {
        if (req.getBikeId() == null || !bikeRepository.existsById(req.getBikeId()))
            throw new IllegalArgumentException("자전거를 찾을 수 없습니다.");
        if (req.getPartType() == null || !PART_TYPES.contains(req.getPartType()))
            throw new IllegalArgumentException("부품 종류가 올바르지 않습니다.");
        if (req.getInstalledOn() == null)
            throw new IllegalArgumentException("장착일을 입력하세요.");
        if (req.getRemovedOn() != null && req.getRemovedOn().isBefore(req.getInstalledOn()))
            throw new IllegalArgumentException("탈거일이 장착일보다 빠릅니다.");
        if (req.getIntervalKm() != null && req.getIntervalKm() <= 0) req.setIntervalKm(null);
        if (req.getIntervalMonths() != null && req.getIntervalMonths() <= 0) req.setIntervalMonths(null);
    }

    private static void apply(BikePart p, BikePart req) {
        p.setPartType(req.getPartType());
        p.setName(req.getName() == null || req.getName().isBlank() ? null : req.getName().trim());
        p.setInstalledOn(req.getInstalledOn());
        p.setRemovedOn(req.getRemovedOn());
        p.setInitialKm(req.getInitialKm() == null || req.getInitialKm() < 0 ? 0.0 : req.getInitialKm());
        p.setIntervalKm(req.getIntervalKm());
        p.setIntervalMonths(req.getIntervalMonths());
        p.setNote(req.getNote());
    }

    // ───────────── 계산 (테스트 대상) ─────────────

    private static LocalDate activeInstalledOn(List<PartUsage> active, String type) {
        return active.stream().filter(u -> type.equals(u.partType()))
                .map(PartUsage::installedOn).max(Comparator.naturalOrder()).orElse(null);
    }

    /** 톱니수별 거리 합(km, 톱니수 오름차순). since 가 있으면 그날 이후 라이딩만. 1km 미만(센서 튐)은 뺀다. */
    static List<GearKm> gearKm(List<GearDistanceRow> rows, LocalDate since, boolean front) {
        Map<Integer, Double> meters = new TreeMap<>();
        for (GearDistanceRow r : rows) {
            Integer teeth = front ? r.getFrontGear() : r.getRearGear();
            if (teeth == null || teeth <= 0 || r.getDistance() == null) continue;
            if (since != null && (r.getRideStart() == null || r.getRideStart().toLocalDate().isBefore(since))) continue;
            meters.merge(teeth, r.getDistance(), Double::sum);
        }
        return meters.entrySet().stream()
                .filter(e -> e.getValue() >= 1000)
                .map(e -> new GearKm(e.getKey(), round1(e.getValue() / 1000.0)))
                .toList();
    }

    static double recentKmPerDay(List<BikeRideDistance> rides, LocalDate today) {
        LocalDate from = today.minusDays(RECENT_DAYS);
        double meters = rides.stream()
                .filter(r -> !r.getStartTime().toLocalDate().isBefore(from)
                        && !r.getStartTime().toLocalDate().isAfter(today))
                .mapToDouble(r -> r.getTotalDistance() == null ? 0 : r.getTotalDistance())
                .sum();
        return meters / 1000.0 / RECENT_DAYS;
    }

    static PartUsage usage(BikePart p, List<BikeRideDistance> rides, LocalDate today, double kmPerDay) {
        LocalDate end = p.getRemovedOn() != null ? p.getRemovedOn() : today.plusDays(1);
        double meters = rides.stream()
                .filter(r -> {
                    LocalDate d = r.getStartTime().toLocalDate();
                    return !d.isBefore(p.getInstalledOn()) && d.isBefore(end);
                })
                .mapToDouble(r -> r.getTotalDistance() == null ? 0 : r.getTotalDistance())
                .sum();
        double initial = p.getInitialKm() == null ? 0 : p.getInitialKm();
        double usedKm = initial + meters / 1000.0;
        LocalDate until = p.getRemovedOn() != null ? p.getRemovedOn() : today;
        long usedDays = Math.max(0, ChronoUnit.DAYS.between(p.getInstalledOn(), until));

        Double kmRatio = p.getIntervalKm() != null ? usedKm / p.getIntervalKm() : null;
        LocalDate monthDue = p.getIntervalMonths() != null ? p.getInstalledOn().plusMonths(p.getIntervalMonths()) : null;
        Double monthRatio = monthDue != null
                ? (double) usedDays / Math.max(1, ChronoUnit.DAYS.between(p.getInstalledOn(), monthDue))
                : null;
        Double ratio;
        if (kmRatio == null) ratio = monthRatio;
        else if (monthRatio == null) ratio = kmRatio;
        else ratio = Math.max(kmRatio, monthRatio);

        String status;
        if (p.getRemovedOn() != null) status = "REMOVED";
        else if (ratio == null) status = "NONE";
        else if (ratio >= 1.0) status = "OVERDUE";
        else if (ratio >= SOON_RATIO) status = "SOON";
        else status = "OK";

        Double remainingKm = p.getIntervalKm() != null ? round1(p.getIntervalKm() - usedKm) : null;
        LocalDate expected = null;
        if (p.getRemovedOn() == null) {
            LocalDate kmDue = null;
            if (remainingKm != null) {
                if (remainingKm <= 0) kmDue = today;
                else if (kmPerDay > 0) kmDue = today.plusDays((long) Math.ceil(remainingKm / kmPerDay));
            }
            if (kmDue == null) expected = monthDue;
            else if (monthDue == null || kmDue.isBefore(monthDue)) expected = kmDue;
            else expected = monthDue;
        }

        return new PartUsage(p.getId(), p.getBikeId(), p.getPartType(), p.getName(),
                p.getInstalledOn(), p.getRemovedOn(), initial, p.getIntervalKm(), p.getIntervalMonths(), p.getNote(),
                round1(usedKm), usedDays, ratio == null ? null : Math.round(ratio * 1000) / 1000.0,
                status, remainingKm, expected);
    }

    private static double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}
