package com.support.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateStaffRequest {

    @NotBlank(message = "Full name is required")
    @Size(max = 255, message = "Full name is too long")
    private String fullname;

    @NotBlank(message = "Username is required")
    @Pattern(regexp = "^[a-z0-9]+$", message = "Username must be strictly lowercase letters and digits without spaces or special symbols")
    @Size(min = 3, max = 25, message = "Username must be between 3 and 25 characters")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Please enter a valid email address")
    @Size(max = 255, message = "Email is too long")
    private String email;

    @Pattern(regexp = "^$|^[0-9]{10}$", message = "Mobile number must be 10 digits, or left empty")
    private String mobileNumber;

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "ROLE_AGENT|ROLE_ADMIN", message = "Please choose a valid role")
    private String role = "ROLE_AGENT";
}
