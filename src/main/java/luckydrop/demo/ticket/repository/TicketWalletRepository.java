package luckydrop.demo.ticket.repository;

import jakarta.persistence.LockModeType;
import luckydrop.demo.ticket.entity.TicketWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TicketWalletRepository extends JpaRepository<TicketWallet, Long> {

    Optional<TicketWallet> findByUserId(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT tw FROM TicketWallet tw WHERE tw.user.id = :userId")
    Optional<TicketWallet> findByUserIdWithLock(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
        UPDATE ticket_wallet
           SET balance = balance - :amount,
               updated_at = NOW()
         WHERE user_id = :userId
           AND balance >= :amount
        """, nativeQuery = true)
    int decreaseBalance(@Param("userId") Long userId,
                        @Param("amount") int amount);
}
