package luckydrop.demo.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GoogleProfileCompleteRequest(
        @NotBlank(message = "닉네임을 입력해주세요.")
        @Size(min = 2, max = 16, message = "닉네임은 2~16자 사이여야 합니다.")
        @Pattern(regexp = "^[a-zA-Z가-힣0-9_]{2,16}$", message = "닉네임은 한글, 영문, 숫자, _만 사용 가능합니다.")
        String nickname,

        @NotBlank(message = "전화번호를 입력해주세요.")
        @Pattern(regexp = "^01[0-9]-?[0-9]{4}-?[0-9]{4}$", message = "올바른 전화번호 형식이 아닙니다.")
        String phone,

        String referredByCode
) {
}
