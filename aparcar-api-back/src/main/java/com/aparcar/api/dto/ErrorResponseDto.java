package com.aparcar.api.dto;

import java.util.List;

public record ErrorResponseDto(
        Integer code,
        String message,
        List<String> details
) { }
