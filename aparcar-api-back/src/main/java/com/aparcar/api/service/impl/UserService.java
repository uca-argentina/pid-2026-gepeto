package com.aparcar.api.service.impl;

import com.aparcar.api.component.IRevokedUserCache;
import com.aparcar.api.dto.auth.UpdateUserDto;
import com.aparcar.api.dto.auth.UserResponseDto;
import com.aparcar.api.entity.auth.InactiveUsersDto;
import com.aparcar.api.entity.auth.Visitante;
import com.aparcar.api.exception.NotFoundException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.repository.ReservaRepository;
import com.aparcar.api.repository.VehiculoRepository;
import com.aparcar.api.repository.VisitanteRepository;
import com.aparcar.api.service.IUserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService implements IUserService {

    private final VisitanteRepository visitanteRepository;
    private final IRevokedUserCache revokedUserCache;
    private final ReservaRepository reservaRepository;
    private final VehiculoRepository vehiculoRepository;

    @Override
    public void activateUser(String email) {
        Visitante user = visitanteRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found."));

        user.setIsActive(true);

        log.info("Activating user: {}", email);

        visitanteRepository.save(user);
    }

    @Override
    public InactiveUsersDto getInactiveUsers() {
        return new InactiveUsersDto(visitanteRepository.findInactiveEmails());
    }

    @Override
    @Transactional
    public void deleteUser(String email, String callerEmail) {
        if (callerEmail == null) {
            log.error("authentication.getName() returned null.");
            throw new RuntimeException("Could not get caller email.");
        }

        Visitante user = visitanteRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User not found."));

        if (callerEmail.equals(user.getEmail())) {
            log.error("User {} tried to delete themselves.", callerEmail);
            throw new ValidationException("You cannot delete yourself.");
        }

        // Antes de unificar las entidades, borrar la cuenta desvinculaba al
        // visitante y lo dejaba huerfano con todas sus reservas: esa era una de
        // las fuentes de fantasmas. Ahora la cuenta ES el visitante, asi que no
        // hay a quien desvincular y borrarla se lleva puesto su historial. Por
        // eso lo bloqueamos: si tiene reservas, que las resuelva un humano.
        if (reservaRepository.existsByVisitanteId(user.getId())) {
            throw new ValidationException(
                    "No se puede eliminar un visitante con reservas registradas.");
        }

        log.info("Revoking user's access");
        revokedUserCache.revoke(user.getEmail());

        vehiculoRepository.deleteAll(vehiculoRepository.findByVisitanteId(user.getId()));

        log.info("Deleting user: {}", user.getEmail());
        visitanteRepository.delete(user);
    }

    @Override
    public List<UserResponseDto> getUsers() {
        return visitanteRepository.findAll()
                .stream()
                .map(this::toResponseDto)
                .toList();
    }

    @Override
    public UserResponseDto updateUser(UUID id, UpdateUserDto dto) {
        Visitante user = visitanteRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found."));

        boolean cambiaDocumento = !user.getDocumento().equals(dto.documento());
        if (cambiaDocumento && visitanteRepository.existsByDocumento(dto.documento())) {
            throw new ValidationException("Ya existe un visitante con ese documento.");
        }

        user.setNombre(dto.nombre());
        user.setDocumento(dto.documento());
        user.setTelefono(dto.telefono());
        user.setAuthorities(new HashSet<>(dto.authorities()));

        Visitante savedUser = visitanteRepository.save(user);

        log.info("Updating user: {}", savedUser.getEmail());

        return toResponseDto(savedUser);
    }

    private UserResponseDto toResponseDto(Visitante user) {
        Set<String> authorities = user.getAuthorities() == null
                ? Set.of()
                : user.getAuthorities()
                        .stream()
                        .map(Enum::name)
                        .collect(Collectors.toSet());

        return new UserResponseDto(
                user.getId(),
                user.getNombre(),
                user.getDocumento(),
                user.getEmail(),
                user.getTelefono(),
                authorities,
                user.getIsActive()
        );
    }
}
