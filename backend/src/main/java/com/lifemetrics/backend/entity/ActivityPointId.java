// entity/ActivityPointId.java
package com.lifemetrics.backend.entity;

import lombok.EqualsAndHashCode;
import java.io.Serializable;

@EqualsAndHashCode
public class ActivityPointId implements Serializable {
    private Long activityCoreId;
    private Integer seq;
}