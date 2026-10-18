package com.aparcar.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Verificacion de discapacidad con certificado ({@code aparcar.discapacidad.*}).
 *
 * @param documentoMaxMb          Tamaño maximo del certificado, en MB.
 * @param documentoRetencionDias  Dias que se conserva el archivo despues de resolver la solicitud.
 *                                El registro de la decision se conserva siempre; el archivo no.
 * @param graciaMigracionDias     Dias que conservan el acceso las cuentas que se habian autodeclarado
 *                                antes de esta verificacion, para que suban su certificado.
 * @param documentosDir           Directorio privado de los certificados. Nunca dentro del repo ni
 *                                servido como estatico.
 * @param subidasPorHora          Certificados que una cuenta puede subir por hora.
 * @param notificarPorMail        Si se avisa por mail a los administradores (solicitud nueva) y al
 *                                visitante (resultado). Los avisos nunca llevan datos de salud.
 */
@ConfigurationProperties(prefix = "aparcar.discapacidad")
public record DiscapacidadProperties(
        @DefaultValue("5") int documentoMaxMb,
        @DefaultValue("30") int documentoRetencionDias,
        @DefaultValue("30") int graciaMigracionDias,
        @DefaultValue("data/discapacidad-docs") String documentosDir,
        @DefaultValue("5") int subidasPorHora,
        @DefaultValue("true") boolean notificarPorMail
) {
    public long documentoMaxBytes() {
        return documentoMaxMb * 1024L * 1024L;
    }
}
