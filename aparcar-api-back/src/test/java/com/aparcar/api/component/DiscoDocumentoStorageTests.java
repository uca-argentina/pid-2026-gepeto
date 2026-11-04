package com.aparcar.api.component;

import com.aparcar.api.component.impl.DiscoDocumentoStorage;
import com.aparcar.api.config.DiscapacidadDePrueba;
import com.aparcar.api.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Almacenamiento en disco de los certificados, sobre un directorio temporal. */
class DiscoDocumentoStorageTests {
    @TempDir
    Path directorio;

    private DiscoDocumentoStorage storage;

    @BeforeEach
    void setUp() {
        storage = new DiscoDocumentoStorage(DiscapacidadDePrueba.propiedades(directorio.toString()));
    }

    @Test
    @DisplayName("guarda con un nombre generado (UUID + extension), lo lee y lo borra")
    void guardaLeeYBorra() {
        String clave = storage.guardar(DiscapacidadDePrueba.pdf(), "pdf");

        assertTrue(clave.matches("[0-9a-f-]{36}\\.pdf"), clave);
        assertTrue(Files.exists(directorio.resolve(clave)));
        assertArrayEquals(DiscapacidadDePrueba.pdf(), storage.leer(clave));

        storage.eliminar(clave);
        assertFalse(Files.exists(directorio.resolve(clave)));
        assertDoesNotThrow(() -> storage.eliminar(clave));
    }

    @Test
    @DisplayName("el archivo queda legible solo por el usuario del proceso")
    void permisosPrivados() throws Exception {
        String clave = storage.guardar(DiscapacidadDePrueba.pdf(), "pdf");
        if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(directorio.resolve(clave))));
        }
    }

    @Test
    @DisplayName("crea el directorio si no existe")
    void creaElDirectorio() {
        var anidado = new DiscoDocumentoStorage(DiscapacidadDePrueba.propiedades(directorio.resolve("a/b").toString()));

        String clave = anidado.guardar(DiscapacidadDePrueba.png(), "png");

        assertTrue(Files.exists(directorio.resolve("a/b").resolve(clave)));
    }

    @Test
    @DisplayName("un documento que ya no esta es 404")
    void inexistenteEs404() {
        assertThrows(NotFoundException.class, () -> storage.leer("3f2b8c1e-9a4d-4e57-b0c6-2d9f1a7e5c33.pdf"));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"../secreto.pdf", "../../etc/passwd", "/etc/passwd", "cud.pdf",
            "3f2b8c1e-9a4d-4e57-b0c6-2d9f1a7e5c33.exe", "3f2b8c1e-9a4d-4e57-b0c6-2d9f1a7e5c33.pdf/../x.pdf", ""})
    @DisplayName("rechaza cualquier clave que no haya generado el: no hay forma de salir del directorio")
    void rechazaClavesManipuladas(String clave) {
        assertThrows(IllegalArgumentException.class, () -> storage.leer(clave));
        assertThrows(IllegalArgumentException.class, () -> storage.eliminar(clave));
    }
}
