package com.lifemetrics.backend.service;

import com.lifemetrics.backend.entity.ExerciseCategory;
import com.lifemetrics.backend.repository.ExerciseCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// service/ExerciseCategoryService.java
@Service
@RequiredArgsConstructor
public class ExerciseCategoryService {
    private final ExerciseCategoryRepository repository;
    
    public List<ExerciseCategory> findAll() {
        return repository.findAll();
    }
}
