package luckydrop.demo.user.repository;

import jakarta.validation.constraints.Size;
import luckydrop.demo.user.entity.User;
import luckydrop.demo.user.entity.AuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);

    boolean existsByNickname(@Size(min = 2, message = "닉네임은 최소 2자 이상이어야 합니다.") String nickname);

    boolean existsByEmail(String email);

    Optional<User> findByInvitationCode(String invitationCode);

    long countByReferredByCode(String referredByCode);

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.ticketWallet")
    List<User> findAllWithWallet();

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.ticketWallet " +
            "WHERE u.nickname LIKE %:query% OR u.email LIKE %:query%")
    List<User> findByNicknameOrEmailWithWallet(@Param("query") String query);
}
