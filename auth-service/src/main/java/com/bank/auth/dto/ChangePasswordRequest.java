package com.bank.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ChangePasswordRequest(
        @NotBlank(message = "oldPassword is required")
        String oldPassword,

        @NotBlank(message = "newPassword is required")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$",
                message = "newPassword must be at least 8 characters and include a letter and a digit"
        )
        String newPassword
) {
}
