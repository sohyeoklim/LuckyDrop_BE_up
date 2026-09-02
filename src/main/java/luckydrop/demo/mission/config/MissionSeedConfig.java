package luckydrop.demo.mission.config;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.mission.entity.Mission;
import luckydrop.demo.mission.enums.LimitUnit;
import luckydrop.demo.mission.enums.MissionType;
import luckydrop.demo.mission.repository.MissionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;

@Configuration
@RequiredArgsConstructor
public class MissionSeedConfig {

    private final MissionRepository missionRepository;

    @Bean
    public CommandLineRunner seedAttendanceMissions() {
        return args -> {
            seed("ATTENDANCE_STATE", "출석 상태", "연속출석 상태 저장용",
                    MissionType.ATTENDANCE, LimitUnit.DAY, 0);

            seed("ATTENDANCE_DAILY", "일일 출석", "하루 1회 출석 보상",
                    MissionType.ATTENDANCE, LimitUnit.DAY, 1);

            seed("ATTENDANCE_BONUS_7", "7일 보너스", "연속 7일 보너스",
                    MissionType.ATTENDANCE, LimitUnit.DAY, 3);

            seed("ATTENDANCE_BONUS_14", "14일 보너스", "연속 14일 보너스",
                    MissionType.ATTENDANCE, LimitUnit.DAY, 5);

            seed("ATTENDANCE_BONUS_21", "21일 보너스", "연속 21일 보너스",
                    MissionType.ATTENDANCE, LimitUnit.DAY, 7);

            seed("ATTENDANCE_BONUS_30", "30일 보너스", "연속 30일 보너스",
                    MissionType.ATTENDANCE, LimitUnit.DAY, 10);

            seed("DRAW_FIRST", "첫 응모", "첫 드로우 응모 보상",
                    MissionType.EVENT, LimitUnit.LIFETIME, 5);
            seed("DRAW_DAILY", "일일 응모", "하루 첫 드로우 응모 보상",
                    MissionType.EVENT, LimitUnit.DAY, 1);
            seed("BOOKMARK_FIRST", "첫 관심 드로우", "첫 드로우 북마크 보상",
                    MissionType.EVENT, LimitUnit.LIFETIME, 5);

        };
    }

    private void seed(
            String code,
            String title,
            String description,
            MissionType missionType,
            LimitUnit limitUnit,
            int rewardTicketAmount
    ) {
        missionRepository.findByCode(code).orElseGet(() ->
                missionRepository.save(Mission.builder()
                        .code(code)
                        .title(title)
                        .description(description)
                        .missionType(missionType)
                        .limitUnit(limitUnit)
                        .limitCount(1)
                        .rewardTicketAmount(rewardTicketAmount)
                        .active(true)
                        .startAt(LocalDateTime.of(2020, 1, 1, 0, 0))
                        .endAt(LocalDateTime.of(2099, 12, 31, 23, 59))
                        .build())
        );
    }
}
