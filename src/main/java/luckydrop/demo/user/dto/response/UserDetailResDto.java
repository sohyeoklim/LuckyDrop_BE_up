package luckydrop.demo.user.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserDetailResDto {
    private Long id;
    private String email;
    private String nickname;
    private String name;
    private String phone;
    private String address;
    private String invitationCode;
    private String role;
    private boolean googleLinked;
    private LocalDateTime createdAt;
}
