package com.aparcar.api.controller;

import com.aparcar.api.dto.dashboard.DashboardResponseDto;
import com.aparcar.api.service.IDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {
    private final IDashboardService dashboardService;

    @GetMapping
    public ResponseEntity<DashboardResponseDto> obtener() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(dashboardService.obtener());
    }
}
