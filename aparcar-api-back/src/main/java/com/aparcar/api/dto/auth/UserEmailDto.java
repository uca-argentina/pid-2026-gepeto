package com.aparcar.api.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.web.util.HtmlUtils;

@Getter
@NoArgsConstructor
public class UserEmailDto {
    @Email
    @NotEmpty(message = "Email is required")
    private String email;

    public UserEmailDto(String email) {
        this.email = HtmlUtils.htmlEscape(email);
    }

    public void setEmail(String email) {
        this.email = HtmlUtils.htmlEscape(email);
    }
}
