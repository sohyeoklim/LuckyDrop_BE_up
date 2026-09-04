package luckydrop.demo.user.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import luckydrop.demo.common.auth.JwtTokenProvider;
import luckydrop.demo.common.member.CustomUserPrincipal;
import luckydrop.demo.common.util.CookieUtil;
import luckydrop.demo.email.service.EmailService;
import luckydrop.demo.user.dto.request.*;
import luckydrop.demo.user.dto.response.*;
import luckydrop.demo.user.entity.User;
import luckydrop.demo.user.service.UserService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import luckydrop.demo.common.auth.OAuth2LoginSuccessHandler;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/user")
public class UserController {

    private final UserService userService;
    private final EmailService emailService;
    private final JwtTokenProvider jwtTokenProvider;
    private final CookieUtil cookieUtil;

    @PostMapping("/create")
    public ResponseEntity<?> memberCreate(@RequestBody UserSaveReqDto userSaveReqDto) {
        User user = userService.create(userSaveReqDto);
        return new ResponseEntity<>(user.getId(), HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<UserStatusDto> loginMember(
            @RequestBody @Valid UserLoginReqDto userLoginReqDto,
            HttpServletResponse response) {

        Map<String, Object> authData = userService.login(userLoginReqDto);

        String accessToken = (String) authData.get("accessToken");
        String refreshToken = (String) authData.get("refreshToken");
        UserInfoResDto userInfo = (UserInfoResDto) authData.get("userInfo");

        // Authorization 헤더에 accessToken 실어줌
        // response.addHeader("Authorization", "Bearer " + accessToken);

        // HttpOnly + Secure 쿠키로 저장 (프론트 JS에서 접근 불가)
        cookieUtil.addCookie(response, "accessToken", accessToken, 30 * 60);
        cookieUtil.addCookie(response, "refreshToken", refreshToken, 60 * 60 * 24 * 14);

        // JSON 응답에는 토큰 안 실음, 사용자 정보만 실음
        UserStatusDto userStatusDto = new UserStatusDto(true, userInfo);
        return new ResponseEntity<>(userStatusDto, HttpStatus.OK);
    }

    @PostMapping("/logout")
    public ResponseEntity<String> logout(
            HttpServletRequest request,
            HttpServletResponse response,
            @AuthenticationPrincipal CustomUserPrincipal principal) {
        if (principal != null) {
            userService.logoutAll(principal.getUser().getId());
        }
        cookieUtil.getCookie(request, "refreshToken").ifPresent(cookie -> {
            userService.logout(cookie.getValue());
        });

        // 쿠키의 유효기간을 0으로 설정
        cookieUtil.deleteCookie(response, "refreshToken");
        cookieUtil.deleteCookie(response, "accessToken");
        cookieUtil.deleteCookie(response, "JSESSIONID");
        SecurityContextHolder.clearContext();
        if (request.getSession(false) != null) {
            request.getSession(false).invalidate();
        }
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body("로그아웃 되었습니다.");
    }

    @PostMapping("/token/reissue")
    public ResponseEntity<String> reissue(HttpServletRequest request, HttpServletResponse response) {
        log.info("/token reissue = {}", request.getRequestURI());
        // 쿠키에서 리프레시 토큰을 가져옴
        String refreshToken = cookieUtil.getCookie(request, "refreshToken")
                .map(Cookie::getValue)
                .orElseThrow(() -> new IllegalArgumentException("토큰을 찾지 못했습니다."));

        // 서비스를 호출하여 새로운 액세스 토큰을 발급
        HashMap<String, String> tokenMap = userService.reissue(refreshToken);

        // 응답 헤더에 새로운 액세스 토큰을 추가
        response.addHeader("Authorization", "Bearer " + tokenMap.get("accessToken"));
        // 쿠키에 새로운 리프레시 토큰 추가
        cookieUtil.addCookie(response, "refreshToken", tokenMap.get("refreshToken"), 60 * 60 * 24 * 14);

        return ResponseEntity.ok("액세스 토큰이 성공적으로 재발급되었습니다.");
    }


    @GetMapping("/list")
    public ResponseEntity<?> memberList() {
        List<UserListResDto> dtos = userService.findAll();
        return new ResponseEntity<>(dtos, HttpStatus.OK);
    }

    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(
            @AuthenticationPrincipal  CustomUserPrincipal principal,
            @Valid @RequestBody ProfileUpdateReqDto request){
        log.info("프로필 업데이트 요청 : userId={}, request={}", principal.getUser().getId(), request);
        userService.updateProfile(principal.getUser().getId(), request);
        UserInfoResDto updatedUserInfo = userService.getUserInfo(principal.getUser().getId());

        return new ResponseEntity<>(updatedUserInfo, HttpStatus.OK);
    }

    @PostMapping("/onboarding")
    public ResponseEntity<UserInfoResDto> completeGoogleOnboarding(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody GoogleProfileCompleteRequest request) {
        userService.completeGoogleProfile(principal.getUser().getId(), request);
        return ResponseEntity.ok(userService.getUserInfo(principal.getUser().getId()));
    }

    @PostMapping("/google/link")
    public ResponseEntity<Map<String, String>> startGoogleLink(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            HttpServletRequest request,
            @RequestHeader("X-Requested-With") String requestedWith) {
        request.getSession(true).setAttribute(
                OAuth2LoginSuccessHandler.GOOGLE_LINK_USER_ID_SESSION_KEY,
                principal.getUser().getId()
        );
        return ResponseEntity.ok(Map.of("authorizationUrl", "/oauth2/authorization/google"));
    }

    @GetMapping("/me")
    public ResponseEntity<UserDetailResDto> getUserDetail(
            @AuthenticationPrincipal CustomUserPrincipal principal) {

        Long userId = principal.getUser().getId(); // JWT에서 userId 추출
        UserDetailResDto userDetail = userService.getUserDetail(userId);

        return new ResponseEntity<>(userDetail, HttpStatus.OK);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<String> forgotPassword(@Valid @RequestBody ForgotPasswordReqDto reqDto) {
        userService.validateEmailExists(reqDto.getEmail());
        emailService.sendPasswordResetEmail(reqDto.getEmail());
        return ResponseEntity.ok("인증 코드가 이메일로 발송되었습니다.");
    }

    @PostMapping("/verify-reset-code")
    public ResponseEntity<Map<String, Boolean>> verifyResetCode(@RequestBody VerifyResetCodeReqDto reqDto) {
        boolean isValid = emailService.checkResetAuthNum(reqDto.getEmail(), reqDto.getAuthNum());
        Map<String, Boolean> response = Map.of("valid", isValid);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<String> resetPassword(@Valid @RequestBody ResetPasswordReqDto reqDto) {
//        if (!emailService.verifyResetCode(reqDto.getEmail(), reqDto.getAuthNum())) {
//            throw new IllegalArgumentException("인증 코드가 만료되었거나 일치하지 않습니다.");
//        }
        userService.resetUserPassword(reqDto.getEmail(), reqDto.getNewPassword());
        emailService.deleteResetCode(reqDto.getEmail());  // 최종 삭제
        return ResponseEntity.ok("비밀번호가 성공적으로 재설정되었습니다.");
    }

    @PutMapping("/change-password")
    public ResponseEntity<String> changePassword(
            @AuthenticationPrincipal CustomUserPrincipal principal,
            @Valid @RequestBody ChangePasswordReqDto reqDto) {

        userService.changePassword(principal.getUser().getId(), reqDto);
        return ResponseEntity.ok("비밀번호가 변경되었습니다.");
    }

    @PostMapping("/check-nickname")
    public ResponseEntity<Map<String, Object>> checkNickname(@RequestBody Map<String, String> request) {
        String nickname = request.get("nickname");

        Map<String, Object> response = new HashMap<>();
        try {
            boolean available = userService.isNicknameAvailable(nickname);
            response.put("available", available);
            response.put("message", available ? "사용 가능한 닉네임입니다." : "이미 사용중인 닉네임입니다.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("available", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // ✅ 새로 추가: 이메일 중복 체크
    @PostMapping("/check-email")
    public ResponseEntity<Map<String, Object>> checkEmail(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        Map<String, Object> response = new HashMap<>();
        try {
            boolean available = userService.isEmailAvailable(email);
            response.put("available", available);
            response.put("message", available ? "사용 가능한 이메일입니다." : "이미 가입된 이메일입니다.");
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            response.put("available", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    // 전체 목록 + 검색 (query 파라미터 있으면 검색, 없으면 전체)
//    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ResponseEntity<List<AdminUserResDto>> getUsers(
            @RequestParam(required = false) String query) {
        return ResponseEntity.ok(userService.getUsers(query));
    }

    // 호스트 승격
//    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{userId}/role")
    public ResponseEntity<Void> changeRole(
            @PathVariable String userId,
            @RequestBody ChangeRoleReqDto request) {
        userService.changeRole(userId, request.getRole());
        return ResponseEntity.ok().build();
    }
}
