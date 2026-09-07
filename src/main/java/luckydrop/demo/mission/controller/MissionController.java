package luckydrop.demo.mission.controller;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.common.member.CustomUserPrincipal;
import luckydrop.demo.mission.dto.response.MissionResponse;
import luckydrop.demo.mission.service.MissionQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/missions")
public class MissionController {
    private final MissionQueryService missionQueryService;

    @GetMapping
    public ResponseEntity<List<MissionResponse>> getMyMissions(@AuthenticationPrincipal CustomUserPrincipal principal) {
        return ResponseEntity.ok(missionQueryService.getMyMissions(principal.getUser().getId()));
    }
}
