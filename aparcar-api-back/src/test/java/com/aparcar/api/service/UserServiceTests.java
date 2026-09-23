package com.aparcar.api.service;

import com.aparcar.api.component.IRevokedUserCache;
import com.aparcar.api.config.UnitTests;
import com.aparcar.api.dto.auth.UpdateUserDto;
import com.aparcar.api.entity.auth.AppAuthority;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.entity.reserva.Vehiculo;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.impl.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@UnitTests
public class UserServiceTests {

    @Mock
    private VisitanteRepository visitanteRepository;

    @Mock
    private IRevokedUserCache revokedUserCache;

    @Mock
    private ReservaRepository reservaRepository;

    @Mock
    private VehiculoRepository vehiculoRepository;

    @InjectMocks
    private UserService usersService;

    private final String testEmail = "test@mail.com";

    @Test
    @DisplayName("activateUser throws NotFoundException when user not found")
    void activateUserThrowsNotFoundExceptionWhenUserNotFound() {
        // Arrange
        when(visitanteRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class, () -> usersService.activateUser(testEmail));
    }

    @Test
    @DisplayName("activateUser activates user successfully")
    void  activateUserSuccessfully() {
        // Arrange
        var user = new Visitante();
        user.setIsActive(false);
        when(visitanteRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
        when(visitanteRepository.save(user)).thenAnswer(i -> i.getArgument(0));

        // Act
        usersService.activateUser(testEmail);

        // Assert
        ArgumentCaptor<Visitante> captor = ArgumentCaptor.forClass(Visitante.class);
        verify(visitanteRepository).save(captor.capture());
        Visitante savedUser = captor.getValue();
        assertTrue(savedUser.getIsActive());
    }

    @Test
    @DisplayName("getInactiveUsers returns set of inactive user emails")
    void getInactiveUsersReturnsSetOfInactiveUserEmails() {
        // Arrange
        when(visitanteRepository.findInactiveEmails()).thenReturn(Set.of(testEmail));

        // Act
        Set<String> result = usersService.getInactiveUsers().emails();

        // Assert
        assertEquals(1, result.size());
        assertTrue(result.contains(testEmail));
    }

    @Test
    @DisplayName("deleteUser throws RuntimeException when caller email is null")
    void  deleteUserThrowsRuntimeExceptionWhenCallerEmailIsNull() {
        // Act & Assert
        assertThrows(RuntimeException.class, () -> usersService.deleteUser(testEmail, null));
    }

    @Test
    @DisplayName("deleteUser throws NotFoundException when user not found")
    void  deleteUserThrowsNotFoundExceptionWhenUserNotFound() {
        // Arrange
        when(visitanteRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(NotFoundException.class, () -> usersService.deleteUser(testEmail, "caller@email.com"));
    }

    @Test
    @DisplayName("deleteUser throws ValidationException when user tries to delete themselves")
    void  deleteUserThrowsValidationExceptionWhenUserTriesToDeleteThemselves() {
        // Arrange
        var user = new Visitante();
        user.setEmail(testEmail);
        when(visitanteRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));

        // Act & Assert
        assertThrows(ValidationException.class, () -> usersService.deleteUser(testEmail, testEmail));
    }

    // Antes de unificar las entidades, borrar la cuenta desvinculaba al
    // visitante y lo dejaba huerfano con todas sus reservas: esa era una de las
    // fuentes de fantasmas. Ahora la cuenta ES el visitante, asi que borrarla
    // se llevaria puesto su historial, y preferimos frenar y que lo resuelva
    // un humano.
    @Test
    @DisplayName("deleteUser lanza ValidationException si el visitante tiene reservas registradas")
    void deleteUserThrowsWhenVisitanteHasReservas() {
        // Arrange
        var user = new Visitante();
        user.setId(UUID.randomUUID());
        user.setEmail(testEmail);

        when(visitanteRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
        when(reservaRepository.existsByVisitanteId(user.getId())).thenReturn(true);

        // Act & Assert
        assertThrows(ValidationException.class,
                () -> usersService.deleteUser(testEmail, "caller@email.com"));
        verify(visitanteRepository, never()).delete(any());
    }

    @Test
    @DisplayName("deleteUser borra los vehiculos del visitante junto con su cuenta")
    void deleteUserDeletesOwnVehiclesAlongWithTheAccount() {
        // Arrange
        var user = new Visitante();
        user.setId(UUID.randomUUID());
        user.setEmail(testEmail);

        var vehiculo = new Vehiculo();
        vehiculo.setId(UUID.randomUUID());

        when(visitanteRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
        when(reservaRepository.existsByVisitanteId(user.getId())).thenReturn(false);
        when(vehiculoRepository.findByVisitanteId(user.getId())).thenReturn(List.of(vehiculo));

        // Act
        usersService.deleteUser(testEmail, "caller@email.com");

        // Assert
        verify(vehiculoRepository).deleteAll(List.of(vehiculo));
        verify(revokedUserCache).revoke(testEmail);
        verify(visitanteRepository).delete(user);
    }

    @Test
    @DisplayName("updateUser rechaza un documento que ya usa otro visitante")
    void updateUserRejectsDocumentoAlreadyInUse() {
        // Arrange
        var user = new Visitante();
        user.setId(UUID.randomUUID());
        user.setEmail(testEmail);
        user.setDocumento("30111222");

        when(visitanteRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(visitanteRepository.existsByDocumento("40999888")).thenReturn(true);

        // Act & Assert
        assertThrows(ValidationException.class, () -> usersService.updateUser(
                user.getId(),
                new UpdateUserDto("Juan Perez", "40999888", null, Set.of(AppAuthority.USER))));
    }

    @Test
    @DisplayName("updateUser actualiza los datos y los roles del visitante")
    void updateUserUpdatesDataAndRoles() {
        // Arrange
        var user = new Visitante();
        user.setId(UUID.randomUUID());
        user.setEmail(testEmail);
        user.setDocumento("30111222");

        when(visitanteRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(visitanteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        // Act
        var result = usersService.updateUser(
                user.getId(),
                new UpdateUserDto("Juan Perez", "30111222", "11-2222-3333", Set.of(AppAuthority.ADMIN)));

        // Assert
        assertEquals("Juan Perez", result.nombre());
        assertEquals("30111222", result.documento());
        assertEquals("11-2222-3333", result.telefono());
        assertTrue(result.authorities().contains("ADMIN"));
    }
}
