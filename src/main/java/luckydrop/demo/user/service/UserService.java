package luckydrop.demo.user.service;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import luckydrop.demo.common.auth.JwtTokenProvider;
import luckydrop.demo.ticket.dto.request.TicketEarnReqDto;
import luckydrop.demo.ticket.service.TicketService;
import luckydrop.demo.mission.service.DrawMissionClaimService;
import luckydrop.demo.user.dto.request.ChangePasswordReqDto;
import luckydrop.demo.user.dto.request.ProfileUpdateReqDto;
import luckydrop.demo.user.dto.request.GoogleProfileCompleteRequest;
import luckydrop.demo.user.dto.request.UserLoginReqDto;
import luckydrop.demo.user.dto.request.UserSaveReqDto;
import luckydrop.demo.user.dto.response.AdminUserResDto;
import luckydrop.demo.user.dto.response.UserDetailResDto;
import luckydrop.demo.user.dto.response.UserListResDto;
import luckydrop.demo.user.dto.response.UserInfoResDto;
import luckydrop.demo.user.entity.Role;
import luckydrop.demo.user.entity.AuthProvider;
import luckydrop.demo.user.entity.User;
import luckydrop.demo.user.entity.RefreshToken;
import luckydrop.demo.user.repository.UserRepository;
import luckydrop.demo.user.repository.RefreshTokenRepository;
import luckydrop.demo.ticket.entity.TicketWallet;
import luckydrop.demo.ticket.repository.TicketWalletRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TicketWalletRepository ticketWalletRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final TicketService ticketService;
    private final DrawMissionClaimService drawMissionClaimService;


    public User create(UserSaveReqDto userSaveReqDto) {
        // 이메일 중복 검증
        if (userRepository.findByEmail(userSaveReqDto.getEmail()).isPresent()) {
            throw new IllegalArgumentException("이미 존재하는 이메일입니다.");
        }

        User newUser = User.builder()
                .name(userSaveReqDto.getName())
                .nickname(userSaveReqDto.getNickname())
                .phone(userSaveReqDto.getPhone())
                .address(userSaveReqDto.getAddress())
                .email(userSaveReqDto.getEmail())
                .password(passwordEncoder.encode(userSaveReqDto.getPassword()))
                .invitationCode(generateUniqueInvitationCode())
                .referredByCode(userSaveReqDto.getReferredByCode())
                .build();

        User savedUser = userRepository.save(newUser);

        initializeNewUser(savedUser);
        drawMissionClaimService.rewardLifetimeMission(savedUser.getId(), "PROFILE_COMPLETE", "프로필 완성 보상");
        if (savedUser.getReferredByCode() != null && !savedUser.getReferredByCode().isBlank()) {
            validateAndRewardReferral(savedUser.getId(), savedUser.getReferredByCode());
            rewardReferralMilestones(savedUser.getReferredByCode());
        }

        return savedUser;
    }

    private void initializeNewUser(User savedUser) {
        // 지갑 생성
        TicketWallet ticketWallet = TicketWallet.builder()
                .user(savedUser)
                .balance(0)
                .build();
        ticketWalletRepository.save(ticketWallet);

        // ⭐ 기본 가입 보상 (추천 코드 없어도 10티켓)
        String signupIdempotencyKey = "SIGNUP_" + savedUser.getId();
        ticketService.earnTickets(
                TicketEarnReqDto.builder()
                        .userId(savedUser.getId())
                        .amount(10)
                        .reason("회원가입 보상")
                        .refType("SIGNUP")
                        .refId(null)
                        .idempotencyKey(signupIdempotencyKey)
                        .build()
        );

    }


    public Map<String, Object> login(UserLoginReqDto userLoginReqDto) {
        String email = userLoginReqDto.getEmail();
        User findUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾지 못했습니다."));

        // 비밀번호 검증
        if (!passwordEncoder.matches(userLoginReqDto.getPassword(), findUser.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        return issueTokens(findUser);
    }

    public Map<String, Object> issueTokens(User user) {
        // 액세스 토큰 생성
        String accessToken = jwtTokenProvider.createAccessToken(user.getEmail(), String.valueOf(user.getRole()));

        // 리프레시 토큰 생성
        String refreshToken = jwtTokenProvider.createRefreshToken(user.getEmail(), String.valueOf(user.getRole()));

        // 리프레시 토큰의 만료 시간
        LocalDateTime refreshTokenExpireTime = LocalDateTime.now().plusSeconds(JwtTokenProvider.REFRESH_TOKEN_VALIDITY_SECONDS / 1000);

        // 토큰 빌더 엔티티 생성
        RefreshToken newRefreshToken = RefreshToken.builder()
                .user(user)
                .token(refreshToken)
                .expiredAt(refreshTokenExpireTime)
                .build();

        // 토큰 저장
        refreshTokenRepository.save(newRefreshToken);

        // 사용자 정보 생성
        UserInfoResDto userInfo = new UserInfoResDto(
                user.getId(),
                user.getNickname(),
                user.getEmail(),
                user.getRole()

        );

        Map<String, Object> authData = new HashMap<>();
        authData.put("accessToken", accessToken);
        authData.put("refreshToken", refreshToken);
        authData.put("userInfo", userInfo);

        log.info("로그인 성공 및 토큰 생성. email = {}", user.getEmail());
        return authData;
    }

    public User loginWithGoogle(String providerId, String email, boolean emailVerified, String name) {
        if (providerId == null || providerId.isBlank() || email == null || email.isBlank()) {
            throw new IllegalArgumentException("Google 계정 정보를 확인할 수 없습니다.");
        }
        if (!emailVerified) {
            throw new IllegalArgumentException("Google에서 이메일 인증이 완료된 계정만 로그인할 수 있습니다.");
        }

        Optional<User> linkedUser = userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, providerId);
        if (linkedUser.isPresent()) {
            return linkedUser.get();
        }

        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException(
                    "이미 가입된 이메일입니다. 기존 계정으로 로그인한 뒤 Google 계정을 연동해주세요.");
        }

        User user = User.builder()
                .name(name == null || name.isBlank() ? "Google User" : name)
                .nickname(generateUniqueGoogleNickname())
                .email(email)
                .password(passwordEncoder.encode(UUID.randomUUID().toString()))
                .phone("")
                .authProvider(AuthProvider.GOOGLE)
                .providerId(providerId)
                .invitationCode(generateUniqueInvitationCode())
                .status("PROFILE_REQUIRED")
                .build();

        User savedUser = userRepository.save(user);
        initializeNewUser(savedUser);
        return savedUser;
    }

    /**
     * 기존 LuckyDrop 로그인 상태에서만 호출되는 Google 계정 연동 처리.
     * Google 이메일과 LuckyDrop 이메일이 달라도, 사용자가 직접 승인한 경우에만 연결한다.
     */
    public void linkGoogleAccount(long userId, String providerId, boolean emailVerified) {
        if (providerId == null || providerId.isBlank()) {
            throw new IllegalArgumentException("Google 계정 정보를 확인할 수 없습니다.");
        }
        if (!emailVerified) {
            throw new IllegalArgumentException("Google에서 이메일 인증이 완료된 계정만 연동할 수 있습니다.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        Optional<User> linkedUser = userRepository.findByAuthProviderAndProviderId(AuthProvider.GOOGLE, providerId);

        if (linkedUser.isPresent() && linkedUser.get().getId() != userId) {
            throw new IllegalStateException("이 Google 계정은 이미 다른 LuckyDrop 계정에 연결되어 있습니다.");
        }
        if (linkedUser.isPresent()) {
            return;
        }

        user.linkGoogleAccount(providerId);
    }

    public void completeGoogleProfile(long userId, GoogleProfileCompleteRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        if (!user.requiresProfileCompletion()) {
            throw new IllegalStateException("이미 프로필 완성을 마친 회원입니다.");
        }
        if (userRepository.existsByNickname(request.nickname())
                && !request.nickname().equals(user.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
        }

        String referredByCode = request.referredByCode() == null ? null : request.referredByCode().trim();
        if (referredByCode != null && !referredByCode.isBlank()) {
            validateAndRewardReferral(userId, referredByCode);
        } else {
            referredByCode = null;
        }

        user.completeGoogleProfile(request.nickname(), request.phone(), referredByCode);
        drawMissionClaimService.rewardLifetimeMission(userId, "PROFILE_COMPLETE", "프로필 완성 보상");
        if (referredByCode != null) {
            rewardReferralMilestones(referredByCode);
        }
    }

    public HashMap<String, String> reissue(String refreshToken) {
        // 리프레시 토큰 유효성 검증 (만료, 위조 등)
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new IllegalArgumentException("토큰이 유효하지 않습니다. ");
        }

        // DB에 저장된 토큰인지, 그리고 만료되지 않았는지 확인
        RefreshToken findRefreshToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new IllegalArgumentException("토큰이 존재하지 않습니다."));

        // 기존 리프레시 토큰 삭제
        refreshTokenRepository.delete(findRefreshToken);

        // 토큰에서 사용자 정보(loginId, role) 추출 및 검증(위에서 진행하긴 했음)
        String email = jwtTokenProvider.getEmail(refreshToken);
        String role = jwtTokenProvider.getRole(refreshToken);

        // 리프레시 토큰 엔티티 저장하기 위해 추출
        User findUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자가 존재하지 않습니다."));

        // 새로운 액세스 토큰 생성
        String newAccessToken = jwtTokenProvider.createAccessToken(email, role);
        // 새로운 리프레시 토큰 생성
        String newRefreshToken = jwtTokenProvider.createRefreshToken(email, role);
        // 만료시간(DB저장 용)
        LocalDateTime refreshTokenExpireTime = LocalDateTime.now().plusSeconds(JwtTokenProvider.REFRESH_TOKEN_VALIDITY_SECONDS / 1000);

        // 토큰 빌더 엔티티 생성
        RefreshToken newRefreshTokenEntity = RefreshToken.builder()
                .user(findUser)
                .token(newRefreshToken)
                .expiredAt(refreshTokenExpireTime)
                .build();
        // DB 저장
        refreshTokenRepository.save(newRefreshTokenEntity);

        log.info("액세스 토큰을 재발급했습니다. user: {}", email);
        log.info("리프레시 토큰을 재발급했습니다. user: {}", email);

        // 컨트롤러 리턴용 맵
        HashMap<String, String> tokenMap = new HashMap<>();
        tokenMap.put("accessToken", newAccessToken);
        tokenMap.put("refreshToken", newRefreshToken);
        // 컨트롤러에 새로운 액세스, 리프레시 토큰 반환
        return tokenMap;
    }

    public void logout(String refreshToken) {
        // 토큰 찾고 있으면 제거
        refreshTokenRepository.findByToken(refreshToken).ifPresent(token -> {
            refreshTokenRepository.delete(token);
            log.info("리프레시 토큰을 DB에서 제거했습니다. user: {}", token.getUser().getEmail());
        });
    }

    public void logoutAll(long userId) {
        refreshTokenRepository.deleteAllByUser_Id(userId);
        log.info("사용자의 모든 리프레시 토큰을 제거했습니다. userId={}", userId);
    }

    public List<UserListResDto> findAll(){
        List<User> users = userRepository.findAll();
        List<UserListResDto> userListResDtos = new ArrayList<>();
        for(User user : users){
            UserListResDto userListResDto = new UserListResDto();
            userListResDto.setId(user.getId());
            userListResDto.setName(user.getName());
            userListResDto.setEmail(user.getEmail());
            userListResDtos.add(userListResDto);
        }
        return userListResDtos;
    }

    @Transactional
    public void updateProfile(long userId, @Valid ProfileUpdateReqDto request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        ProfileUpdateReqDto.ProfileUpdateReqDtoBuilder profileUpdate = user.toProfileUpdateReqDto();
        // 닉네임 업데이트
        if (request.getNickname() != null) {
            // 중복 체크 (본인 제외)
            if (!request.getNickname().equals(user.getNickname())
                    && userRepository.existsByNickname(request.getNickname())) {
                throw new IllegalArgumentException("이미 사용 중인 닉네임입니다.");
            }
            profileUpdate.nickname(request.getNickname());
            log.info("사용자 {}의 닉네임 업데이트: {}", userId, request.getNickname());
        }

        // 전화번호 업데이트
        if (request.getPhone() != null) {
            profileUpdate.phone(request.getPhone());
            log.info("사용자 {}의 전화번호 업데이트", userId);
        }

        // 주소 업데이트
        if (request.getAddress() != null) {
            profileUpdate.address(request.getAddress());
            log.info("사용자 {}의 주소 업데이트", userId);
        }

        user.edit(profileUpdate.build());
    }

    public UserInfoResDto getUserInfo(long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return new UserInfoResDto(user);
    }

    public UserDetailResDto getUserDetail(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return UserDetailResDto.builder()
                .id(user.getId())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .name(user.getName())
                .phone(user.getPhone())
                .address(user.getAddress())
                .invitationCode(user.getInvitationCode())
                .role(user.getRole().name())
                .googleLinked(user.getAuthProvider() == AuthProvider.GOOGLE && user.getProviderId() != null)
                .createdAt(user.getCreatedAt())
                .build();
    }

    // UserService에 추가
    public void validateEmailExists(String email) {
        if (!userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("가입되지 않은 이메일입니다.");
        }
    }

    @Transactional
    public void resetUserPassword(String email, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("가입되지 않은 이메일입니다."));

        // BCrypt 등으로 암호화 (기존 passwordEncoder 주입 필요)
        String encodedPassword = passwordEncoder.encode(newPassword);

        // 기존 updateProfile 패턴처럼 직접 업데이트
        user.updatePassword(encodedPassword);

        refreshTokenRepository.deleteAllByUser_Id(user.getId());
        log.info("사용자 {}의 비밀번호 재설정 및 모든 세션 무효화 완료", user.getId());
    }

    // UserService.changePassword()
    @Transactional
    public void changePassword(Long userId, ChangePasswordReqDto dto) {
        User user = userRepository.findById(userId).orElseThrow();

        // 현재 비밀번호 검증
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("현재 비밀번호가 일치하지 않습니다.");
        }

        // 새 비밀번호 강도 체크 (옵션)
        if (dto.getNewPassword().length() < 8) {
            throw new IllegalArgumentException("새 비밀번호는 8자 이상이어야 합니다.");
        }

        user.updatePassword(passwordEncoder.encode(dto.getNewPassword()));
    }

    public List<AdminUserResDto> getUsers(String query) {
        List<User> users = (query == null || query.isBlank())
                ? userRepository.findAllWithWallet()
                : userRepository.findByNicknameOrEmailWithWallet(query);

        return users.stream()
                .map(u -> AdminUserResDto.builder()
                        .id(String.valueOf(u.getId()))
                        .nickname(u.getNickname())
                        .email(u.getEmail())
                        .role(u.getRole().name())
                        .joinedAt(u.getCreatedAt().toLocalDate().toString())
                        .currentTickets(u.getTicketWallet() != null
                                ? u.getTicketWallet().getBalance() : 0)
                        .build())
                .toList();
    }

    @Transactional
    public void changeRole(String userId, String role) {
        User user = userRepository.findById(Long.valueOf(userId))
                .orElseThrow(() -> new IllegalArgumentException("사용자 없음"));
        user.changeRole(Role.valueOf(role));
    }


    public boolean isNicknameAvailable(String nickname) {
        validateNickname(nickname);
        return !userRepository.existsByNickname(nickname);
    }
    public boolean isEmailAvailable(String email) {
        validateEmail(email);
        return !userRepository.existsByEmail(email);
    }

    private void validateNickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) {
            throw new IllegalArgumentException("닉네임은 필수입니다.");
        }
        if (nickname.length() < 2 || nickname.length() > 16) {
            throw new IllegalArgumentException("닉네임은 2~16자 사이여야 합니다.");
        }
        if (!nickname.matches("^[a-zA-Z가-힣0-9_]{2,16}$")) {
            throw new IllegalArgumentException("닉네임은 한글, 영문, 숫자, _만 사용 가능합니다.");
        }
    }

    private void validateEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("이메일은 필수입니다.");
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})$")) {
            throw new IllegalArgumentException("올바른 이메일 형식이 아닙니다.");
        }
    }

    @Transactional
    public void validateAndRewardReferral(Long newUserId, String referredByCode) {
        User referrer = userRepository.findByInvitationCode(referredByCode)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 추천인 코드입니다."));

        if (referrer.getId() == newUserId) {
            throw new IllegalArgumentException("자기 자신을 추천인으로 할 수 없습니다.");
        }

        String idempotencyKey = "REFERRAL_" + newUserId + "_" + referrer.getId();

        // 1. 가입자에게 추가 5티켓 (추천 보상)
        ticketService.earnTickets(
                TicketEarnReqDto.builder()
                        .userId(newUserId)
                        .amount(5)
                        .reason("추천 회원가입 보상")
                        .refType("REFERRED_SIGNUP")
                        .refId(referrer.getId())
                        .idempotencyKey(idempotencyKey + "_NEW")
                        .build()
        );

        // 2. 추천인에게 5티켓 (추천인 보상)
        ticketService.earnTickets(
                TicketEarnReqDto.builder()
                        .userId(referrer.getId())
                        .amount(5)
                        .reason("추천인 보상")
                        .refType("REFERRAL")
                        .refId(newUserId)
                        .idempotencyKey(idempotencyKey + "_REFERRER")
                        .build()
        );
    }

    private void rewardReferralMilestones(String invitationCode) {
        User referrer = userRepository.findByInvitationCode(invitationCode)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 추천인 코드입니다."));
        long referralCount = userRepository.countByReferredByCode(invitationCode);
        if (referralCount >= 1) {
            drawMissionClaimService.rewardLifetimeMission(referrer.getId(), "REFERRAL_1", "친구 초대 1명 보상");
        }
        if (referralCount >= 5) {
            drawMissionClaimService.rewardLifetimeMission(referrer.getId(), "REFERRAL_5", "친구 초대 5명 보상");
        }
    }

    private String generateUniqueInvitationCode() {
        int maxRetries = 10;  // 충돌 방지 재시도 횟수
        for (int i = 0; i < maxRetries; i++) {
            String code = generateCandidateCode();
            if (userRepository.findByInvitationCode(code).isEmpty()) {
                return code;  // 사용 가능하면 반환
            }
        }
        throw new IllegalStateException("초대 코드를 생성할 수 없습니다. 재시도하세요.");
    }

    private String generateCandidateCode() {
        // 8자리 알파벳+숫자 (예: AB12CD34)
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder(8);
        Random random = new Random();
        for (int i = 0; i < 8; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }

    private String generateUniqueGoogleNickname() {
        for (int i = 0; i < 10; i++) {
            String nickname = "g" + generateCandidateCode();
            if (!userRepository.existsByNickname(nickname)) {
                return nickname;
            }
        }
        throw new IllegalStateException("Google 사용자 닉네임을 생성하지 못했습니다.");
    }


}
