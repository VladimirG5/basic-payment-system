package com.bank.core.controller;

import com.bank.core.dto.CreateUserRequest;
import com.bank.core.dto.UpdatePasswordRequest;
import com.bank.core.dto.UserCredentialsResult;
import com.bank.core.dto.UserProvisioningResult;
import com.bank.core.service.UserProvisioningService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal-only endpoint for auth-service, which has no direct database access of its own -
 * not exposed through any public routing, relies on the two services sharing a private Docker
 * network (same shape as InternalTransferController).
 */
@RestController
@RequestMapping("/internal/users")
public class InternalUserController {

    private final UserProvisioningService userProvisioningService;

    public InternalUserController(UserProvisioningService userProvisioningService) {
        this.userProvisioningService = userProvisioningService;
    }

    @PostMapping
    public ResponseEntity<UserProvisioningResult> createUser(@Valid @RequestBody CreateUserRequest request) {
        UserProvisioningResult result = userProvisioningService.createUser(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/by-email/{email}")
    public ResponseEntity<UserCredentialsResult> findByEmail(@PathVariable String email) {
        return ResponseEntity.ok(userProvisioningService.findByEmail(email));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<UserCredentialsResult> findById(@PathVariable Long userId) {
        return ResponseEntity.ok(userProvisioningService.findById(userId));
    }

    @PatchMapping("/{userId}/password")
    public ResponseEntity<Void> updatePassword(@PathVariable Long userId, @Valid @RequestBody UpdatePasswordRequest request) {
        userProvisioningService.updatePassword(userId, request.passwordHash());
        return ResponseEntity.noContent().build();
    }
}
