package luckydrop.demo.mission.repository;

import luckydrop.demo.mission.entity.Mission;
import luckydrop.demo.mission.enums.MissionType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;

public interface MissionRepository extends JpaRepository<Mission, Long> {
    Optional<Mission> findByMissionTypeAndActiveTrue(MissionType missionType);
    Optional<Mission> findByCode(String code); // AttendanceService
    List<Mission> findByActiveTrueOrderByMissionTypeAscCodeAsc();
}
