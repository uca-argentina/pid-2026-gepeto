package com.aparcar.api.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class ResetPasswordDto {
    @Email
    @NotEmpty(message = "email is required")
    private String email;

    @NotEmpty(message = "otp is required")
    private String otp;

    @JsonProperty("new_password")
    @NotEmpty(message = "new_password is required")
    private String newPassword;
}
