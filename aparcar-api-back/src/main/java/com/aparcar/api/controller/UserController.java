package com.aparcar.api.controller;

import com.aparcar.api.dto.auth.UpdateUserDto;
import com.aparcar.api.dto.auth.UserEmailDto;
import com.aparcar.api.dto.auth.UserResponseDto;
import com.aparcar.api.entity.auth.InactiveUsersDto;
import com.aparcar.api.service.IUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final IUserService userService;

    @PostMapping("/users/activate")
    public ResponseEntity<Void> activateUser(@Valid @RequestBody UserEmailDto dto) {
        userService.activateUser(dto.getEmail());

        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/inactive")
    public ResponseEntity<InactiveUsersDto> getInactiveUsers() {
        var inactiveUsers = userService.getInactiveUsers();

        return ResponseEntity.ok(inactiveUsers);
    }

    @DeleteMapping("/users")
    public ResponseEntity<Void> deleteUser(
            @Valid @RequestBody UserEmailDto dto,
            Authentication authentication) {

        userService.deleteUser(dto.getEmail(), authentication.getName());

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/usuarios")
    public ResponseEntity<List<UserResponseDto>> getUsers() {
        return ResponseEntity.ok(userService.getUsers());
    }

    @PutMapping("/api/v1/usuarios/{id}")
    public ResponseEntity<UserResponseDto> updateUser(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserDto dto) {

        return ResponseEntity.ok(userService.updateUser(id, dto));
    }
}