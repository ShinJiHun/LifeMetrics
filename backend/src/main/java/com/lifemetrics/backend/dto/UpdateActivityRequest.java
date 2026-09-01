// dto/UpdateActivityRequest.java
package com.lifemetrics.backend.dto;

import lombok.Data;

/**
 * 활동 정보 부분 수정 요청 DTO.
 * 각 필드는 null이면 "수정하지 않음"을 의미한다 (요청 시점에 값이 전달된 필드만 반영).
 * 현재는 제목(name), 라이딩 타입(rideType), 퍼머넌트 코스번호(permanentNo)를 지원하지만
 * 향후 description, gearId 등 확장 가능.
 */
@Data
public class UpdateActivityRequest {
    private String name;

    // PERMANENT/BREVET/FLECHE/POPULAIRE/TOURING/GENERAL
    private String rideType;

    // rideType=PERMANENT일 때 permanent_courses.permanent_no 참조
    private String permanentNo;
}
