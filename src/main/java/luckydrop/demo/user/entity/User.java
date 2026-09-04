package luckydrop.demo.user.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import luckydrop.demo.common.BaseEntity;
import luckydrop.demo.ticket.entity.TicketLedger;
import luckydrop.demo.ticket.entity.TicketWallet;
import luckydrop.demo.user.dto.request.ProfileUpdateReqDto;

import java.util.List;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(
        name = "uk_user_auth_provider_id",
        columnNames = {"auth_provider", "provider_id"}
))
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 16, unique = true)
    private String nickname;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", length = 20)
    @Builder.Default
    private AuthProvider authProvider = AuthProvider.LOCAL;

    @Column(name = "provider_id", length = 255)
    private String providerId;

    @Column(nullable = true)
    private String address;

    @Column(nullable = true)
    private String invitationCode;

    @Column(nullable = true)
    private String referredByCode;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private Role role = Role.USER; // 기본적으로 입력하지 않을 시, user로 들어감.

    @Builder.Default
    private String status = "ACTIVE";

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private TicketWallet ticketWallet;

    @OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private List<TicketLedger> ticketLedger;

    public ProfileUpdateReqDto.ProfileUpdateReqDtoBuilder toProfileUpdateReqDto() {
        return ProfileUpdateReqDto.builder()
                .phone(this.phone)
                .address(this.address)
                .nickname(this.nickname);
    }

    public void edit(ProfileUpdateReqDto profileUpdateReqDto) {
        this.phone = profileUpdateReqDto.getPhone();
        this.address = profileUpdateReqDto.getAddress();
        this.nickname = profileUpdateReqDto.getNickname();
    }

    public void updatePassword(String encodedPassword) {
        this.password = encodedPassword;
    }
    public void changeRole(Role role) {
        this.role = role;
    }

    public void linkGoogleAccount(String providerId) {
        this.authProvider = AuthProvider.GOOGLE;
        this.providerId = providerId;
    }

    public boolean requiresProfileCompletion() {
        return "PROFILE_REQUIRED".equals(this.status)
                || (this.authProvider == AuthProvider.GOOGLE && (this.phone == null || this.phone.isBlank()));
    }

    public void completeGoogleProfile(String nickname, String phone, String referredByCode) {
        this.nickname = nickname;
        this.phone = phone;
        this.referredByCode = referredByCode;
        this.status = "ACTIVE";
    }
}
