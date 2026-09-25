package com.aparcar.api.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

/**
 * Fija la zona horaria de la aplicacion.
 *
 * <p>Las reservas se guardan como {@link java.time.LocalDateTime}: un reloj de
 * pared, sin zona. Eso es lo correcto para un predio —"de 10 a 12" significa
 * las 10 y las 12 del lugar, pase lo que pase con los husos— pero solo funciona
 * si el backend y el navegador coinciden en cual es ese reloj.
 *
 * <p>Sin esto, el contenedor arranca en UTC mientras el navegador manda hora
 * local: una reserva de las 16 a las 17 llegaba a un servidor que creia que
 * eran las 19, y la rechazaba por "terminada en el pasado". Depender de la zona
 * que traiga la imagen de Docker es fragil, asi que se fija explicitamente y la
 * app se comporta igual donde sea que corra.
 */
@Slf4j
@Configuration
public class ZonaHorariaConfig {

    @Value("${app.zona-horaria:America/Argentina/Buenos_Aires}")
    private String zonaHoraria;

    @PostConstruct
    public void fijarZonaHoraria() {
        TimeZone.setDefault(TimeZone.getTimeZone(zonaHoraria));
        log.info("Zona horaria de la aplicacion fijada en {} (hora actual: {})",
                zonaHoraria, java.time.LocalDateTime.now());
    }
}
