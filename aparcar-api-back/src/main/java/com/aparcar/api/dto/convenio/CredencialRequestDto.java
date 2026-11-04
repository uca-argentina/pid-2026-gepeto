package com.aparcar.api.dto.convenio;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CredencialRequestDto(@NotNull UUID convenioId, @NotBlank @Size(max = 100) String credencial,
                                   UUID visitanteId, @Size(max = 100) String documento) { }
