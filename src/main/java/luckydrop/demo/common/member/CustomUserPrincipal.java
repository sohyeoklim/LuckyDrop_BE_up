package luckydrop.demo.common.member;

import lombok.Getter;
import luckydrop.demo.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;


@Getter
public class CustomUserPrincipal implements UserDetails {

    private final User user;
    private final String registrationId;
    public CustomUserPrincipal(User user) {
        this.user = user;
        this.registrationId = null;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
    }


    @Override
    public String getUsername() {
        if(registrationId == null) {
            return user.getEmail();
        }
        return null;
    }

    @Override
    public String getPassword() {
        if(registrationId == null) {
            return user.getName();
        }
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return UserDetails.super.isAccountNonExpired();
    }

    @Override
    public boolean isAccountNonLocked() {
        return UserDetails.super.isAccountNonLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return UserDetails.super.isCredentialsNonExpired();
    }

    @Override
    public boolean isEnabled() {
        return UserDetails.super.isEnabled();
    }
}
