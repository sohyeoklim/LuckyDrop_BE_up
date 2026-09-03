package luckydrop.demo.draw;

import lombok.RequiredArgsConstructor;
import luckydrop.demo.draw.enums.FulfillmentStatus;
import luckydrop.demo.draw.repository.DrawWinnerRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class DrawDeliveryScheduler {
    private final DrawWinnerRepository drawWinnerRepository;

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireUnsubmittedAddresses() {
        LocalDateTime now = LocalDateTime.now();
        drawWinnerRepository.findByFulfillmentStatusAndAddressDeadlineAtBefore(
                        FulfillmentStatus.ADDRESS_REQUIRED, now)
                .forEach(winner -> winner.expireAddressSubmission());
        drawWinnerRepository.findByFulfillmentStatusAndRewardDeliveryDeadlineAtBefore(
                        FulfillmentStatus.REWARD_PENDING, now)
                .forEach(winner -> winner.expireRewardDelivery());
    }
}
