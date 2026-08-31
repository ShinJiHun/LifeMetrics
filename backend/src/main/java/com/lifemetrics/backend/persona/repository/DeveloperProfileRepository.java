package com.lifemetrics.backend.persona.repository;

import com.lifemetrics.backend.persona.entity.DeveloperProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DeveloperProfileRepository extends JpaRepository<DeveloperProfile, Long> {

    /** developer_profile 은 단일 행만 쓰지만, 행이 여럿이어도 항상 같은(가장 먼저 만든) 행을 쓰도록 고정한다. */
    Optional<DeveloperProfile> findFirstByOrderByIdAsc();
}
