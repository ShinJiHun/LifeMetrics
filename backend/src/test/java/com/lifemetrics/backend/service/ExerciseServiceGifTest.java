package com.lifemetrics.backend.service;

import com.lifemetrics.backend.entity.ExerciseItem;
import com.lifemetrics.backend.repository.ExerciseCategoryRepository;
import com.lifemetrics.backend.repository.ExerciseItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExerciseServiceGifTest {

    @TempDir
    Path dir;

    private final ExerciseItemRepository items = mock(ExerciseItemRepository.class);
    private final ExerciseService service = new ExerciseService(items, mock(ExerciseCategoryRepository.class));

    private static final byte[] GIF = "GIF89a....".getBytes();

    private ExerciseItem item(long id, String gifUrl) {
        ExerciseItem i = new ExerciseItem();
        ReflectionTestUtils.setField(i, "id", id);
        i.setGifUrl(gifUrl);
        when(items.findById(id)).thenReturn(Optional.of(i));
        return i;
    }

    private void configure() {
        ReflectionTestUtils.setField(service, "gifBasePath", dir.toString());
        ReflectionTestUtils.setField(service, "gifBaseUrl", "/api/exercise/gif/");
    }

    @Test
    void 기존_경로가_있으면_덮어쓰고_옛_파일은_백업한다() throws Exception {
        configure();
        item(1L, "chest/PUSH_UP.gif");
        Files.createDirectories(dir.resolve("chest"));
        Files.write(dir.resolve("chest/PUSH_UP.gif"), "GIF89a-old".getBytes());

        var res = service.uploadGif(1L, new MockMultipartFile("file", "new.gif", "image/gif", GIF));

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat((String) res.getBody().get("gifUrl")).startsWith("/api/exercise/gif/chest/PUSH_UP.gif?v=");
        assertThat(Files.readAllBytes(dir.resolve("chest/PUSH_UP.gif"))).isEqualTo(GIF);
        try (var backups = Files.list(dir.resolve(".backup"))) {
            assertThat(backups.toList()).singleElement()
                    .satisfies(p -> assertThat(Files.readString(p)).isEqualTo("GIF89a-old"));
        }
        verify(items, never()).save(any());
    }

    @Test
    void 경로가_없으면_uploads_에_저장하고_DB_경로를_채운다() throws Exception {
        configure();
        ExerciseItem i = item(7L, null);

        var res = service.uploadGif(7L, new MockMultipartFile("file", "x.gif", "image/gif", GIF));

        assertThat(res.getStatusCode().value()).isEqualTo(200);
        assertThat(i.getGifUrl()).isEqualTo("uploads/7.gif");
        assertThat(Files.exists(dir.resolve("uploads/7.gif"))).isTrue();
        verify(items).save(i);
    }

    @Test
    void GIF_가_아니거나_경로가_밖으로_나가면_거절한다() {
        configure();
        item(2L, null);
        var notGif = service.uploadGif(2L, new MockMultipartFile("file", "a.png", "image/png", "\u0089PNG....".getBytes()));
        assertThat(notGif.getStatusCode().value()).isEqualTo(400);

        item(3L, "../../etc/evil.gif");
        var escape = service.uploadGif(3L, new MockMultipartFile("file", "x.gif", "image/gif", GIF));
        assertThat(escape.getStatusCode().value()).isEqualTo(400);
        assertThat(ExerciseService.isGif("GIF87a".getBytes())).isTrue();
    }
}
