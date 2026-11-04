package com.aparcar.api.component;

import com.aparcar.api.config.UnitTests;
import com.aparcar.api.service.IVerificacionDiscapacidadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@UnitTests
class VerificacionesDiscapacidadBarridoTests {
    @Mock
    private IVerificacionDiscapacidadService service;

    @InjectMocks
    private VerificacionesDiscapacidadBarrido barrido;

    @Test
    @DisplayName("corre los tres pasos: vencer, terminar la gracia y retirar certificados")
    void correLosTresPasos() {
        barrido.barrer();

        verify(service).vencerVerificaciones();
        verify(service).terminarAccesosProvisorios();
        verify(service).retirarDocumentos();
    }

    @Test
    @DisplayName("si un paso falla, los otros corren igual")
    void unPasoQueFallaNoFrenaAlResto() {
        when(service.vencerVerificaciones()).thenThrow(new RuntimeException("base"));

        barrido.barrer();

        verify(service).terminarAccesosProvisorios();
        verify(service).retirarDocumentos();
    }
}
