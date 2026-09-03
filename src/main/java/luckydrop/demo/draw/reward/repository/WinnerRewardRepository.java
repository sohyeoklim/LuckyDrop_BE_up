package luckydrop.demo.draw.reward.repository;

import luckydrop.demo.draw.reward.entity.WinnerReward;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WinnerRewardRepository extends JpaRepository<WinnerReward, Long> {
    Optional<WinnerReward> findByWinner_Id(Long winnerId);
    boolean existsByWinner_Id(Long winnerId);
}
