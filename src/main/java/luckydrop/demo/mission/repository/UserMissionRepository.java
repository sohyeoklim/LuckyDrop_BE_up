package luckydrop.demo.mission.repository;

import jakarta.persistence.LockModeType;
import luckydrop.demo.mission.entity.UserMission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;

public interface UserMissionRepository extends JpaRepository<UserMission, Long> {

    // 기존: 일별 기록용(유지)
    boolean existsByUserIdAndMissionIdAndPeriodKey(Long userId, Long missionId, String periodKey);
    Optional<UserMission> findByUserIdAndMissionIdAndPeriodKey(Long userId, Long missionId, String periodKey);

    // 추가: "상태행 1개" 조회용(락 없이)
    Optional<UserMission> findByUserIdAndMissionId(Long userId, Long missionId);

    // 추가: "상태행 1개" 조회용(락 걸기)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select um from UserMission um where um.userId = :userId and um.missionId = :missionId")
    Optional<UserMission> findByUserIdAndMissionIdWithLock(
            @Param("userId") Long userId,
            @Param("missionId") Long missionId
    );

    List<UserMission> findAllByUserId(Long userId);
}
