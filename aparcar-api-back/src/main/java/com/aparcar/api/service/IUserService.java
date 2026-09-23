package com.aparcar.api.service;

import com.aparcar.api.dto.auth.UpdateUserDto;
import com.aparcar.api.dto.auth.UserResponseDto;
import com.aparcar.api.entity.auth.InactiveUsersDto;

import java.util.List;
import java.util.UUID;

/**
 * Interface defining administrative user management operations.
 */
public interface IUserService {

    /**
     * Activates a previously inactive user.
     *
     * @param email The user's email address.
     */
    void activateUser(String email);

    /**
     * Retrieves all users currently pending activation.
     *
     * @return DTO containing the list of inactive users.
     */
    InactiveUsersDto getInactiveUsers();

    /**
     * Deletes a user from the system. Prevents a user from deleting themselves.
     *
     * @param email       The email of the user to be deleted.
     * @param callerEmail The email of the administrator initiating the deletion.
     */
    void deleteUser(String email, String callerEmail);

    /**
     * Retrieves all users.
     *
     * @return list of users without sensitive information.
     */
    List<UserResponseDto> getUsers();

    /**
     * Updates the editable fields of a user.
     *
     * @param id  user's id.
     * @param dto editable user data.
     * @return updated user.
     */
    UserResponseDto updateUser(UUID id, UpdateUserDto dto);
}