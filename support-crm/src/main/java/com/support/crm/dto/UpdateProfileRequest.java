package com.support.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name is too long")
    private String fullname;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    @Size(max = 255, message = "Email is too long")
    private String email;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be 10 digits, or left empty")
    private String mobileNumber;
}
