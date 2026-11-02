package com.support.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {

    @NotBlank(message = "Current password is required")
    private String currentPassword;

    @NotBlank(message = "New password is required")
    @Pattern(regexp = RegisterRequest.PASSWORD_PATTERN, message = RegisterRequest.PASSWORD_MESSAGE)
    private String newPassword;

    @NotBlank(message = "Confirmation password is required")
    private String confirmPassword;
}
