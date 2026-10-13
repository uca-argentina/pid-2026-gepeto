package com.aparcar.api.security;

import com.aparcar.api.entity.auth.AppAuthority;
import org.springframework.security.core.Authentication;

/**
 * Responde las dos preguntas de rol que hacen los controllers.
 *
 * <p>Existe porque la pregunta que se hacian antes estaba mal planteada. Tres
 * controllers repetian un helper {@code esAdmin} y le pasaban el resultado a
 * los services, pero lo que esos services necesitaban saber no era si quien
 * pedia administraba el sistema, sino si trabajaba en el estacionamiento: de
 * eso depende ver todas las reservas en vez de las propias, reservar a nombre
 * de otro, o recibir el catalogo completo de vehiculos.
 *
 * <p>Con un solo rol con esos permisos las dos preguntas daban la misma
 * respuesta y la diferencia no se notaba. Al aparecer EMPLEADO dejan de
 * coincidir, y mezclarlas significaria o darle al empleado la configuracion del
 * sistema, o dejarlo sin poder hacer su trabajo.
 */
public final class Roles {
    private Roles() {
    }

    /**
     * Quien trabaja en el estacionamiento: ADMIN o EMPLEADO.
     *
     * <p>Es la pregunta que corresponde para decidir si alguien opera sobre lo
     * de todos o solo sobre lo suyo.
     */
    public static boolean esPersonal(Authentication authentication) {
        return tiene(authentication, AppAuthority.ADMIN)
                || tiene(authentication, AppAuthority.EMPLEADO);
    }

    /**
     * Solo ADMIN.
     *
     * <p>Para lo que configura el sistema y no forma parte de la operacion
     * diaria: tarifas, cuentas, estado de las cocheras y reservas recurrentes.
     */
    public static boolean esAdmin(Authentication authentication) {
        return tiene(authentication, AppAuthority.ADMIN);
    }

    private static boolean tiene(Authentication authentication, AppAuthority rol) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> rol.name().equals(a.getAuthority()));
    }
}
