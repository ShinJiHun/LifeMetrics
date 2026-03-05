package com.lifemetrics.backend.service;

import com.lifemetrics.backend.entity.ExerciseItem;
import com.lifemetrics.backend.repository.ExerciseItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

// service/ExerciseItemService.java
@Service
@RequiredArgsConstructor
public class ExerciseItemService {
    private final ExerciseItemRepository repository;
    
    public List<ExerciseItem> findAll() {
        return repository.findAll();
    }
    
    public List<ExerciseItem> findByCategoryId(Long categoryId) {
        return repository.findByCategoryId(categoryId);
    }
    
    public List<ExerciseItem> findByMediaUrlIsNotNull() {
        return repository.findByMediaUrlIsNotNull();
    }
}