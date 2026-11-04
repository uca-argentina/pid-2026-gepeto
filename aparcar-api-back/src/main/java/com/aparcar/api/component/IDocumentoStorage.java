package com.aparcar.api.component;

/**
 * Almacenamiento privado de documentos sensibles (los certificados de
 * discapacidad).
 *
 * <p>Las claves las genera el servidor: nunca se usa el nombre que manda el
 * cliente. La implementacion tiene que rechazar cualquier clave que no haya
 * generado ella, para que una clave manipulada no pueda salir del directorio.
 */
public interface IDocumentoStorage {
    /**
     * Guarda el contenido y devuelve la clave con la que se recupera.
     *
     * @param extension Extension ya validada (pdf, jpg o png), sin punto.
     */
    String guardar(byte[] contenido, String extension);

    /** @throws com.aparcar.api.exception.NotFoundException Si el archivo ya no existe. */
    byte[] leer(String clave);

    /** Borra el archivo. No falla si ya no estaba. */
    void eliminar(String clave);
}
