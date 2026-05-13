// dto/UpdateActivityRequest.java
package com.lifemetrics.backend.dto;

import lombok.Data;

/**
 * 활동 정보 부분 수정 요청 DTO.
 * 현재는 제목(name)만 수정 가능하지만,
 * 향후 description, gearId 등 확장 가능.
 */
@Data
public class UpdateActivityRequest {
    private String name;
}
