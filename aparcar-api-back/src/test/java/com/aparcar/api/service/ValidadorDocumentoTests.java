package com.aparcar.api.service;

import com.aparcar.api.config.DiscapacidadDePrueba;
import com.aparcar.api.exception.ArchivoDemasiadoGrandeException;
import com.aparcar.api.exception.TipoArchivoNoPermitidoException;
import com.aparcar.api.exception.ValidationException;
import com.aparcar.api.service.impl.ValidadorDocumento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caja blanca de la validacion del certificado: no se confia en el nombre ni
 * en el content-type; los dos tienen que coincidir con la firma real del
 * archivo. Limite de prueba: 5 MB.
 */
class ValidadorDocumentoTests {
    private static final int CINCO_MB = 5 * 1024 * 1024;

    private final ValidadorDocumento validador =
            new ValidadorDocumento(DiscapacidadDePrueba.propiedades("no-se-usa"));

    @Test
    @DisplayName("acepta un PDF real y calcula todo del lado del servidor")
    void aceptaPdf() {
        var documento = validador.validar("CUD Juan.pdf", "application/pdf", DiscapacidadDePrueba.pdf());

        assertEquals("pdf", documento.extension());
        assertEquals("application/pdf", documento.tipo());
        assertEquals("CUD Juan.pdf", documento.nombre());
        assertEquals(64, documento.sha256().length());
    }

    @ParameterizedTest(name = "{0} con {1}")
    @CsvSource({
            "certificado.png,image/png,png",
            "certificado.jpg,image/jpeg,jpg",
            "certificado.jpeg,image/jpeg,jpg",
            "CERTIFICADO.PNG,image/png,png",
    })
    @DisplayName("acepta JPG y PNG reales, y normaliza la extension")
    void aceptaImagenes(String nombre, String tipo, String extension) {
        byte[] contenido = extension.equals("png") ? DiscapacidadDePrueba.png() : DiscapacidadDePrueba.jpg();

        assertEquals(extension, validador.validar(nombre, tipo, contenido).extension());
    }

    @Test
    @DisplayName("tolera parametros en el content-type")
    void toleraParametrosEnElTipo() {
        assertDoesNotThrow(() -> validador.validar("c.pdf", "application/pdf; charset=binary", DiscapacidadDePrueba.pdf()));
    }

    @Test
    @DisplayName("un archivo vacio es 400")
    void rechazaVacio() {
        assertThrows(ValidationException.class, () -> validador.validar("c.pdf", "application/pdf", new byte[0]));
    }

    @Test
    @DisplayName("justo en el limite (5 MB) se acepta; un byte mas es 413")
    void bordeDelTamano() {
        assertDoesNotThrow(() -> validador.validar("c.pdf", "application/pdf", DiscapacidadDePrueba.pdfDe(CINCO_MB)));
        assertThrows(ArchivoDemasiadoGrandeException.class,
                () -> validador.validar("c.pdf", "application/pdf", DiscapacidadDePrueba.pdfDe(CINCO_MB + 1)));
        assertThrows(ArchivoDemasiadoGrandeException.class, () -> validador.validarTamano(CINCO_MB + 1L));
        assertDoesNotThrow(() -> validador.validarTamano(CINCO_MB));
    }

    @ParameterizedTest
    @ValueSource(strings = {"virus.exe", "documento.docx", "foto.gif", "sin-extension", "certificado.pdf.exe"})
    @DisplayName("una extension no permitida es 415")
    void rechazaExtensiones(String nombre) {
        assertThrows(TipoArchivoNoPermitidoException.class,
                () -> validador.validar(nombre, "application/pdf", DiscapacidadDePrueba.pdf()));
    }

    @Test
    @DisplayName("un content-type que no coincide con la extension es 415")
    void rechazaTipoDistinto() {
        assertThrows(TipoArchivoNoPermitidoException.class,
                () -> validador.validar("c.pdf", "image/png", DiscapacidadDePrueba.pdf()));
        assertThrows(TipoArchivoNoPermitidoException.class,
                () -> validador.validar("c.pdf", null, DiscapacidadDePrueba.pdf()));
    }

    @Test
    @DisplayName("la firma real manda: una imagen renombrada a .pdf es 415, aunque declare application/pdf")
    void rechazaFirmaDistinta() {
        assertThrows(TipoArchivoNoPermitidoException.class,
                () -> validador.validar("c.pdf", "application/pdf", DiscapacidadDePrueba.png()));
        assertThrows(TipoArchivoNoPermitidoException.class,
                () -> validador.validar("c.png", "image/png", DiscapacidadDePrueba.jpg()));
        byte[] ejecutable = "MZ\u0090\u0000 un ejecutable".getBytes(StandardCharsets.ISO_8859_1);
        assertThrows(TipoArchivoNoPermitidoException.class,
                () -> validador.validar("foto.jpg", "image/jpeg", ejecutable));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"../../etc/passwd.pdf", "..\\..\\windows\\cud.pdf", "/var/lib/otro.pdf",
            "C:\\Users\\x\\cud.pdf"})
    @DisplayName("un nombre con path traversal se sanea: solo queda el nombre, sin carpetas")
    void saneaPathTraversal(String nombre) {
        String saneado = validador.validar(nombre, "application/pdf", DiscapacidadDePrueba.pdf()).nombre();

        assertFalse(saneado.contains("/"), saneado);
        assertFalse(saneado.contains("\\"), saneado);
        assertFalse(saneado.contains(".."), saneado);
        assertTrue(saneado.endsWith(".pdf"), saneado);
    }

    @Test
    @DisplayName("un nombre raro o larguisimo queda corto y legible")
    void saneaNombresRaros() {
        assertEquals("certificado.pdf", validador.validar(".pdf", "application/pdf", DiscapacidadDePrueba.pdf()).nombre());
        assertEquals("cud_scr_.pdf",
                validador.validar("cud<scr>.pdf", "application/pdf", DiscapacidadDePrueba.pdf()).nombre());
        String largo = validador.validar("a".repeat(300) + ".pdf", "application/pdf", DiscapacidadDePrueba.pdf()).nombre();
        assertEquals(104, largo.length());
    }
}
