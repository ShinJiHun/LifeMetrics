package com.lifemetrics.backend.repository;

import com.lifemetrics.backend.entity.BlogPost;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BlogPostRepository extends JpaRepository<BlogPost, Long> {

    Optional<BlogPost> findByUrl(String url);

    List<BlogPost> findAllByOrderByPublishedAtDesc();
}