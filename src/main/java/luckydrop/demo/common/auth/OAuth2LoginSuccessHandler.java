package luckydrop.demo.common.auth;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import luckydrop.demo.common.util.CookieUtil;
import luckydrop.demo.user.entity.User;
import luckydrop.demo.user.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    public static final String GOOGLE_LINK_USER_ID_SESSION_KEY = "GOOGLE_LINK_USER_ID";

    private final UserService userService;
    private final CookieUtil cookieUtil;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication
    ) throws IOException, ServletException {
        OidcUser googleUser = (OidcUser) authentication.getPrincipal();

        Object linkUserId = request.getSession(false) == null
                ? null
                : request.getSession(false).getAttribute(GOOGLE_LINK_USER_ID_SESSION_KEY);

        if (linkUserId instanceof Long userId) {
            request.getSession(false).removeAttribute(GOOGLE_LINK_USER_ID_SESSION_KEY);
            try {
                userService.linkGoogleAccount(
                        userId,
                        googleUser.getSubject(),
                        Boolean.TRUE.equals(googleUser.getEmailVerified())
                );
                getRedirectStrategy().sendRedirect(request, response, frontendUrl + "/my/edit?googleLinked=true");
            } catch (IllegalArgumentException | IllegalStateException e) {
                String message = URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
                getRedirectStrategy().sendRedirect(request, response,
                        frontendUrl + "/my/edit?googleLinkError=" + message);
            }
            return;
        }

        User user;
        try {
            user = userService.loginWithGoogle(
                    googleUser.getSubject(),
                    googleUser.getEmail(),
                    Boolean.TRUE.equals(googleUser.getEmailVerified()),
                    googleUser.getFullName()
            );
        } catch (IllegalArgumentException e) {
            String message = URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            getRedirectStrategy().sendRedirect(request, response,
                    frontendUrl + "/login?oauthError=" + message);
            return;
        }

        Map<String, Object> authData = userService.issueTokens(user);
        cookieUtil.addCookie(response, "accessToken", (String) authData.get("accessToken"), 30 * 60);
        cookieUtil.addCookie(response, "refreshToken", (String) authData.get("refreshToken"), 60 * 60 * 24 * 14);

        String destination = user.requiresProfileCompletion() ? "/onboarding" : "/";
        getRedirectStrategy().sendRedirect(request, response, frontendUrl + destination);
    }
}
