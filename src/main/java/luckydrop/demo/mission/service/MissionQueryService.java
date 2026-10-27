package luckydrop.demo.mission.service;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.mission.dto.response.MissionResponse;
import luckydrop.demo.mission.entity.Mission;
import luckydrop.demo.mission.entity.UserMission;
import luckydrop.demo.mission.repository.MissionRepository;
import luckydrop.demo.mission.repository.UserMissionRepository;
import luckydrop.demo.user.entity.User;
import luckydrop.demo.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MissionQueryService {
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private final MissionRepository missionRepository;
    private final UserMissionRepository userMissionRepository;
    private final UserRepository userRepository;
    private final DrawMissionClaimService drawMissionClaimService;

    @Transactional
    public java.util.List<MissionResponse> getMyMissions(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        // 기능 배포 전 가입한 사용자도 실제 달성 조건을 기준으로 한 번만 보상/기록한다.
        if (!user.requiresProfileCompletion()) {
            drawMissionClaimService.rewardLifetimeMission(userId, "PROFILE_COMPLETE", "프로필 완성 보상");
        }
        long referralCount = userRepository.countByReferredByCode(user.getInvitationCode());
        if (referralCount >= 1) {
            drawMissionClaimService.rewardLifetimeMission(userId, "REFERRAL_1", "친구 초대 1명 보상");
        }
        if (referralCount >= 5) {
            drawMissionClaimService.rewardLifetimeMission(userId, "REFERRAL_5", "친구 초대 5명 보상");
        }
        Map<Long, java.util.List<UserMission>> history = userMissionRepository.findAllByUserId(userId).stream()
                .collect(Collectors.groupingBy(UserMission::getMissionId));
        String today = LocalDate.now().format(BASIC_DATE);
        String drawToday = LocalDate.now().toString();

        return missionRepository.findByActiveTrueOrderByMissionTypeAscCodeAsc().stream()
                .filter(m -> !"ATTENDANCE_STATE".equals(m.getCode()))
                .map(m -> toResponse(m, history.getOrDefault(m.getId(), java.util.List.of()), user, today, drawToday))
                .toList();
    }

    private MissionResponse toResponse(Mission mission, java.util.List<UserMission> history, User user, String today, String drawToday) {
        String code = mission.getCode();
        int target = referralTarget(code);
        int progress = 0;
        boolean completed;

        if ("ATTENDANCE_DAILY".equals(code)) {
            Mission stateMission = missionRepository.findByCode("ATTENDANCE_STATE").orElseThrow();
            UserMission state = userMissionRepository.findByUserIdAndMissionId(user.getId(), stateMission.getId()).orElse(null);
            completed = state != null && today.equals(state.getPeriodKey());
            progress = completed ? 1 : 0;
        } else if (code.startsWith("ATTENDANCE_BONUS_")) {
            Mission stateMission = missionRepository.findByCode("ATTENDANCE_STATE").orElseThrow();
            UserMission state = userMissionRepository.findByUserIdAndMissionId(user.getId(), stateMission.getId()).orElse(null);
            target = Integer.parseInt(code.substring(code.lastIndexOf('_') + 1));
            progress = state == null ? 0 : state.getProgressCount();
            completed = progress >= target;
        } else if ("DRAW_DAILY".equals(code)) {
            completed = history.stream().anyMatch(h -> drawToday.equals(h.getPeriodKey()));
            progress = completed ? 1 : 0;
        } else if ("PROFILE_COMPLETE".equals(code)) {
            completed = !user.requiresProfileCompletion();
            progress = completed ? 1 : 0;
        } else if (code.startsWith("REFERRAL_")) {
            target = referralTarget(code);
            progress = Math.toIntExact(userRepository.countByReferredByCode(user.getInvitationCode()));
            completed = history.stream().anyMatch(h -> "LIFETIME".equals(h.getPeriodKey()));
        } else {
            String key = mission.getLimitUnit().name().equals("DAY") ? today : "LIFETIME";
            completed = history.stream().anyMatch(h -> key.equals(h.getPeriodKey()) || ("DRAW_DAILY".equals(code) && drawToday.equals(h.getPeriodKey())));
            progress = completed ? 1 : 0;
        }
        return MissionResponse.builder().code(code).title(mission.getTitle()).description(mission.getDescription())
                .type(mission.getMissionType().name()).rewardTickets(mission.getRewardTicketAmount())
                .progress(Math.min(progress, target)).target(target).completed(completed)
                .claimable(false).adVerificationRequired(code.startsWith("AD_")).build();
    }

    private int referralTarget(String code) {
        if ("REFERRAL_1".equals(code)) return 1;
        if ("REFERRAL_5".equals(code)) return 5;
        return 1;
    }
}
