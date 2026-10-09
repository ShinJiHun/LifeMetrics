package com.lifemetrics.backend.service;

import com.lifemetrics.backend.dto.RiderZoneSettingDto;
import com.lifemetrics.backend.entity.RiderZoneSetting;
import com.lifemetrics.backend.repository.RiderZoneSettingRepository;
import com.lifemetrics.backend.repository.UserBodyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * FTP·최대심박 설정. 저장값이 없으면 추정값(체중×3.0 W, 190 bpm)을 돌려준다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiderZoneSettingService {

    static final int DEFAULT_MAX_HR = 190;
    static final double DEFAULT_FTP_PER_KG = 3.0;

    private final RiderZoneSettingRepository settingRepo;
    private final UserBodyRecordRepository bodyRecordRepo;

    public RiderZoneSettingDto getEffective(Long userId) {
        RiderZoneSetting saved = findSaved(userId).orElse(null);
        Integer ftp = saved != null ? saved.getFtp() : null;
        Integer maxHr = saved != null ? saved.getMaxHr() : null;
        return new RiderZoneSettingDto(
                ftp != null ? ftp : (int) Math.round(getLatestWeight(userId) * DEFAULT_FTP_PER_KG),
                maxHr != null ? maxHr : DEFAULT_MAX_HR,
                ftp == null,
                maxHr == null
        );
    }

    public RiderZoneSettingDto save(Long userId, Integer ftp, Integer maxHr) {
        RiderZoneSetting setting = settingRepo.findById(userId).orElseGet(() -> {
            RiderZoneSetting s = new RiderZoneSetting();
            s.setUserId(userId);
            return s;
        });
        setting.setFtp(ftp);
        setting.setMaxHr(maxHr);
        settingRepo.save(setting);
        return getEffective(userId);
    }

    public double getLatestWeight(Long userId) {
        return bodyRecordRepo
                .findByUserIdOrderByRecordDate(userId)
                .stream()
                .filter(r -> r.getWeight() != null)
                .reduce((first, second) -> second)
                .map(r -> r.getWeight())
                .orElse(75.0);
    }

    // rider_zone_setting 테이블이 아직 운영 DB 에 없을 수 있으므로 조회 실패는 추정값으로 처리
    private Optional<RiderZoneSetting> findSaved(Long userId) {
        try {
            return settingRepo.findById(userId);
        } catch (Exception e) {
            log.warn("rider_zone_setting 조회 실패 - 추정값 사용: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
