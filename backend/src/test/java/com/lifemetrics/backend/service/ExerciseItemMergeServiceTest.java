package com.lifemetrics.backend.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExerciseItemMergeServiceTest {

    @Test
    void 영문명_키는_공백_하이픈_대소문자를_무시한다() {
        assertThat(ExerciseItemMergeService.key("Cable Cross Over")).isEqualTo(ExerciseItemMergeService.key("cable crossover"));
        assertThat(ExerciseItemMergeService.key("Decline Push-Up")).isEqualTo(ExerciseItemMergeService.key("Decline Push Up"));
        assertThat(ExerciseItemMergeService.key("Dumbbell Incline Press")).isNotEqualTo(ExerciseItemMergeService.key("Dumbbell Incline Fly"));
        assertThat(ExerciseItemMergeService.key(null)).isEmpty();
        assertThat(ExerciseItemMergeService.key("  ")).isEmpty();
    }
}
