package com.lifemetrics.backend.api;

import com.lifemetrics.backend.dto.ActivityPhotoDto;
import com.lifemetrics.backend.service.ActivityPhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 활동 사진(블로그 후기 사진을 라이딩 지도에 연결한 것) */
@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityPhotoController {

    private final ActivityPhotoService photoService;

    /** 활동의 사진 목록(글 안 순서). 위치가 있는 사진은 lat/lon, 없으면 null. */
    @GetMapping("/{id}/photos")
    public List<ActivityPhotoDto> photos(@PathVariable Long id) {
        return photoService.list(id);
    }

    /** 사진 파일. size = thumb(360px) | web(1600px, 기본) | original */
    @GetMapping("/photos/{photoId}/file")
    public ResponseEntity<Resource> file(@PathVariable Long photoId,
                                         @RequestParam(defaultValue = "web") String size) {
        return photoService.file(photoId, size);
    }
}
