package com.lifemetrics.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ActivityPhotoServiceTest {

    @Test
    void webPath_images폴더를_web폴더_jpg로_바꾼다() {
        assertThat(ActivityPhotoService.webPath("athlete/029_백두대간/images/02.gif"))
                .isEqualTo("athlete/029_백두대간/web/02.jpg");
        assertThat(ActivityPhotoService.webPath("athlete/029_x/images/10.jpeg"))
                .isEqualTo("athlete/029_x/web/10.jpg");
    }

    @Test
    void webPath_images폴더가_없으면_그대로() {
        assertThat(ActivityPhotoService.webPath("etc/photo.jpg")).isEqualTo("etc/photo.jpg");
    }
}
