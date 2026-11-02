package com.support.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateTicketRequest {
    @NotBlank(message = "Customer Name Required")
    @Size(max = 255, message = "Customer name is too long")
    private String customer_name;

    @NotBlank(message = "Customer Email is Required")
    @Pattern(regexp = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$", message = "Invalid Email") // Needs a real domain, so a@b is refused
    @Size(max = 255, message = "Email is too long")
    private String customer_email;

    @NotBlank(message = "Subject is required")
    @Size(max = 255, message = "Subject must be under 255 characters")
    private String subject;

    @NotBlank(message = "Description is required")
    @Size(max = 5000, message = "Description must be under 5000 characters")
    private String description;

    // Hidden trap on the public form. People leave it empty, bots fill it in.
    private String website;
}
