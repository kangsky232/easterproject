package com.smoke.dto;

import com.smoke.entity.HazardAction;
import com.smoke.entity.HazardTicket;

import java.util.List;

public record HazardDetailResponse(
        HazardTicket ticket,
        List<HazardAction> actions) {
}
