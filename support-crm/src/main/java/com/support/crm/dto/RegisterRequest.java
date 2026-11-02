package com.support.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RegisterRequest {

    // Upper limit is 72 because BCrypt cannot hash longer passwords
    public static final String PASSWORD_PATTERN = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#])[A-Za-z\\d@$!%*?&#]{8,72}$";
    public static final String PASSWORD_MESSAGE = "Password must contain at least 1 lowercase, 1 uppercase, 1 number, 1 special character (@$!%*?&#), and 8 to 72 characters";

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

    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[0-9]{10}$", message = "Mobile number must be a valid 10-digit number")
    private String mobileNumber;

    @NotBlank(message = "Password is required")
    @Pattern(regexp = PASSWORD_PATTERN, message = PASSWORD_MESSAGE)
    private String password;
}
