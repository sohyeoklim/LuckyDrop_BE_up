package luckydrop.demo.mission.service;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.mission.entity.Mission;
import luckydrop.demo.mission.entity.UserMission;
import luckydrop.demo.mission.repository.MissionRepository;
import luckydrop.demo.mission.repository.UserMissionRepository;
import luckydrop.demo.ticket.dto.request.TicketEarnReqDto;
import luckydrop.demo.ticket.service.TicketService;
import luckydrop.demo.entry.repository.DrawEntrySummaryRepository;
import luckydrop.demo.user.entity.User;
import luckydrop.demo.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class DrawMissionClaimService {

    private final MissionRepository missionRepository;
    private final UserMissionRepository userMissionRepository;
    private final TicketService ticketService;
    private final DrawEntrySummaryRepository drawEntrySummaryRepository;
    private final UserRepository userRepository;

    @Transactional
    public void rewardEntryMissions(Long userId) {
        rewardOnce(userId, "DRAW_FIRST", "LIFETIME", "첫 응모 보상");
        rewardOnce(userId, "DRAW_DAILY", LocalDate.now().toString(), "일일 응모 보상");
        rewardReferralAfterFirstEntry(userId);
    }

    @Transactional
    public void rewardFirstBookmark(Long userId) {
        rewardOnce(userId, "BOOKMARK_FIRST", "LIFETIME", "첫 관심 드로우 보상");
    }

    private void rewardOnce(Long userId, String missionCode, String periodKey, String reason) {
        Mission mission = missionRepository.findByCode(missionCode)
                .orElseThrow(() -> new IllegalStateException("미션 없음: " + missionCode));
        if (userMissionRepository.existsByUserIdAndMissionIdAndPeriodKey(userId, mission.getId(), periodKey)) {
            return;
        }
        userMissionRepository.save(UserMission.builder()
                .userId(userId).missionId(mission.getId()).periodKey(periodKey)
                .progressCount(1).completedAt(LocalDateTime.now()).rewardedAt(LocalDateTime.now()).build());
        ticketService.earnTickets(TicketEarnReqDto.builder()
                .userId(userId).amount(mission.getRewardTicketAmount()).reason(reason)
                .refType("MISSION").refId(mission.getId())
                .idempotencyKey(missionCode + ":" + userId + ":" + periodKey).build());
    }

    private void rewardReferralAfterFirstEntry(Long userId) {
        User invitedUser = userRepository.findById(userId).orElseThrow();
        String referralCode = invitedUser.getReferredByCode();
        if (referralCode == null || referralCode.isBlank()) return;
        User referrer = userRepository.findByInvitationCode(referralCode).orElseThrow();
        String key = "REFERRAL_FIRST_ENTRY:" + userId;
        ticketService.earnTickets(TicketEarnReqDto.builder().userId(userId).amount(20)
                .reason("추천 첫 응모 보상").refType("REFERRAL").refId(referrer.getId())
                .idempotencyKey(key + ":INVITED").build());
        ticketService.earnTickets(TicketEarnReqDto.builder().userId(referrer.getId()).amount(20)
                .reason("친구 첫 응모 보상").refType("REFERRAL").refId(userId)
                .idempotencyKey(key + ":REFERRER").build());
    }

    @Transactional
    public void claimFirstDrawMission(Long userId) {

        // 응모 이력이 있는지 확인
        boolean hasAnyEntry = drawEntrySummaryRepository.existsByUserId(userId);

        if (!hasAnyEntry) {
            throw new IllegalStateException("드로우 응모 이력이 없습니다.");
        }

        // 미션 조회
        Mission mission = missionRepository.findByCode("DRAW_FIRST")
                .orElseThrow(() -> new IllegalStateException("미션 없음"));

        // 이미 보상 받았는지 확인 (1회성 미션)
        boolean alreadyClaimed = userMissionRepository
                .findByUserIdAndMissionId(userId, mission.getId())
                .isPresent();

        if (alreadyClaimed) {
            throw new IllegalStateException("이미 보상을 받았습니다.");
        }

        // 기록 생성
        UserMission userMission = UserMission.builder()
                .userId(userId)
                .missionId(mission.getId())
                .periodKey("FIRST")
                .progressCount(1)
                .build();

        userMissionRepository.save(userMission);

        // 티켓 지급
        ticketService.earnTickets(
                TicketEarnReqDto.builder()
                        .userId(userId)
                        .amount(mission.getRewardTicketAmount())
                        .reason("DRAW_FIRST")
                        .refType("MISSION")
                        .refId(mission.getId())
                        .idempotencyKey("DRAW_FIRST:" + userId)
                        .build()
        );
    }

    @Transactional
    public void claimDailyDrawMission(Long userId) {

        // 오늘 시작/끝 시간
        LocalDate today = LocalDate.now();
        LocalDateTime start = today.atStartOfDay();
        LocalDateTime end = today.plusDays(1).atStartOfDay();

        // 오늘 응모했는지 확인
        boolean hasEntryToday = drawEntrySummaryRepository
                .existsByUserIdAndUpdatedAtBetween(userId, start, end);

        if (!hasEntryToday) {
            throw new IllegalStateException("오늘 드로우 응모 이력이 없습니다.");
        }

        // 미션 조회
        Mission mission = missionRepository.findByCode("DRAW_DAILY")
                .orElseThrow(() -> new IllegalStateException("미션 없음"));

        // 이미 보상 받았는지 확인
        boolean alreadyClaimed = userMissionRepository
                .existsByUserIdAndMissionIdAndPeriodKey(
                        userId,
                        mission.getId(),
                        today.toString()
                );

        if (alreadyClaimed) {
            throw new IllegalStateException("이미 보상을 받았습니다.");
        }

        // 기록 생성
        UserMission userMission = UserMission.builder()
                .userId(userId)
                .missionId(mission.getId())
                .periodKey(today.toString())
                .progressCount(1)
                .build();

        userMissionRepository.save(userMission);

        // 티켓 지급
        ticketService.earnTickets(
                TicketEarnReqDto.builder()
                        .userId(userId)
                        .amount(mission.getRewardTicketAmount())
                        .reason("DRAW_DAILY")
                        .refType("MISSION")
                        .refId(mission.getId())
                        .idempotencyKey("DRAW_DAILY:" + userId + ":" + today)
                        .build()
        );
    }
}
