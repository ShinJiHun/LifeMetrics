package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.UserBasicProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserBasicProfileRepository extends JpaRepository<UserBasicProfile, Long> {
}