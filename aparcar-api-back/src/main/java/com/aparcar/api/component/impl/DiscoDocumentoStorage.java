package com.aparcar.api.component.impl;

import com.aparcar.api.component.IDocumentoStorage;
import com.aparcar.api.config.DiscapacidadProperties;
import com.aparcar.api.exception.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * {@link IDocumentoStorage} sobre un directorio del disco
 * ({@code DISCAPACIDAD_DOCS_DIR}).
 *
 * <p>El directorio es privado: no se sirve como estatico y no vive dentro de
 * {@code src/}. Cada archivo se llama con un UUID generado aca mas su
 * extension, y una clave que no tenga exactamente esa forma se rechaza antes
 * de tocar el disco: ni {@code ../}, ni rutas absolutas, ni nombres del
 * cliente. Donde el sistema lo permite, los archivos quedan legibles solo por
 * el usuario del proceso.
 */
@Slf4j
@Component
public class DiscoDocumentoStorage implements IDocumentoStorage {
    private static final Pattern CLAVE = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(pdf|jpg|png)");

    private final Path directorio;

    public DiscoDocumentoStorage(DiscapacidadProperties properties) {
        this.directorio = Path.of(properties.documentosDir()).toAbsolutePath().normalize();
    }

    @Override
    public String guardar(byte[] contenido, String extension) {
        String clave = UUID.randomUUID() + "." + extension;
        Path destino = resolver(clave);
        try {
            Files.createDirectories(directorio);
            Files.write(destino, contenido, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
                Files.setPosixFilePermissions(destino, PosixFilePermissions.fromString("rw-------"));
            }
        } catch (IOException e) {
            // Sin el nombre ni el contenido en el log: es un dato de salud.
            throw new UncheckedIOException("No se pudo guardar el documento.", e);
        }
        return clave;
    }

    @Override
    public byte[] leer(String clave) {
        try {
            return Files.readAllBytes(resolver(clave));
        } catch (NoSuchFileException e) {
            throw new NotFoundException("El documento ya no está disponible.");
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer el documento.", e);
        }
    }

    @Override
    public void eliminar(String clave) {
        try {
            Files.deleteIfExists(resolver(clave));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo borrar el documento.", e);
        }
    }

    /** La clave valida, resuelta adentro del directorio. Cualquier otra cosa se rechaza. */
    Path resolver(String clave) {
        if (clave == null || !CLAVE.matcher(clave).matches()) {
            throw new IllegalArgumentException("Clave de documento invalida.");
        }
        Path ruta = directorio.resolve(clave).normalize();
        if (!ruta.getParent().equals(directorio)) {
            throw new IllegalArgumentException("Clave de documento invalida.");
        }
        return ruta;
    }
}
