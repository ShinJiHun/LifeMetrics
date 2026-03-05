package com.lifemetrics.backend.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Objects;

@Getter
@Setter
public class BodyAnalysisLifetimeCurrentId implements Serializable {

    private Long userId;
    private String goalType;

    public BodyAnalysisLifetimeCurrentId() {}

    public BodyAnalysisLifetimeCurrentId(Long userId, String goalType) {
        this.userId = userId;
        this.goalType = goalType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BodyAnalysisLifetimeCurrentId)) return false;
        BodyAnalysisLifetimeCurrentId that = (BodyAnalysisLifetimeCurrentId) o;
        return Objects.equals(userId, that.userId)
                && Objects.equals(goalType, that.goalType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, goalType);
    }
}
