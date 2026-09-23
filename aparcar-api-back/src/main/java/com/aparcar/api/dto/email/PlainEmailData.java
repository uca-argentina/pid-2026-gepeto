package com.aparcar.api.dto.email;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlainEmailData {
    private String body;
    private String subject;
    private List<String> recipients;
}
