package com.pulseos.system;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {
    @GetMapping("/status")
    public Map<String, String> status() {
        return Map.of("service", "pulseos-api", "status", "operational", "stage", "foundation");
    }
}
