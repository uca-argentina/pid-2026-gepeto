import { test, expect } from "@playwright/test";
import jsQR from "jsqr";

const CODIGO = "ABCDE12345";
const PAYLOAD = `APARCAR:${CODIGO}`;
const vehiculo = { id: "vehiculo-ui", patente: "ABC123", tipo: "AUTO" };
const cochera = { id: "cochera-ui", numero: "A-01", sector: "Planta baja", tipo: "AUTO" };
const reserva = {
  id: "reserva-qr-ui", estado: "CONFIRMADA", modalidad: "FRANJA", precioTotal: 2000,
  desde: "2026-10-11T15:00:00", hasta: "2026-10-11T17:00:00", fechaCreacion: "2026-10-11T14:00:00Z",
  vehiculo, cochera, visitante: { nombre: "Visitante de prueba" }, historial: [],
  acceso: { codigo: CODIGO, ingresoDesde: "2026-10-11T14:45:00", ingresoHasta: "2026-10-11T15:30:00" },
};
const resumen = {
  id: reserva.id, estado: reserva.estado, desde: reserva.desde, hasta: reserva.hasta,
  visitanteNombre: reserva.visitante.nombre, patente: vehiculo.patente, tipoVehiculo: vehiculo.tipo,
  cocheraNumero: cochera.numero, cocheraSector: cochera.sector, cocheraTipo: cochera.tipo,
  ingresoDesde: reserva.acceso.ingresoDesde, ingresoHasta: reserva.acceso.ingresoHasta,
  ingresoAt: null, egresoAt: null, minutosExceso: null,
};

async function autenticar(context, baseURL, rol) {
  const token = [
    Buffer.from("{}").toString("base64url"),
    Buffer.from(JSON.stringify({ sub: "qr-ui@test.invalid", authorities: rol, exp: 4102444800 })).toString("base64url"),
    "prueba-ui",
  ].join(".");
  await context.addCookies([{ name: "JWT", value: token, url: baseURL }]);
}

// Toda la API queda interceptada; estas pruebas nunca escriben datos reales.
async function prepararPagina(page, context, baseURL, { rol = "USER", nueva = false, tema = "dark" } = {}) {
  await autenticar(context, baseURL, rol);
  await page.addInitScript(({ baseURL, tema }) => {
    window.__ENV = { NEXT_PUBLIC_API_BASE_URL: baseURL };
    localStorage.setItem("aparcar-theme", tema);
  }, { baseURL, tema });
  const datos = { reservas: nueva ? [] : [reserva], altas: [], consultas: [], registros: [], movimientos: [] };
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const ruta = new URL(request.url()).pathname;
    if (ruta === "/api/v1/reservas") {
      if (request.method() === "POST") {
        datos.altas.push(request.postDataJSON());
        datos.reservas = [reserva];
        return route.fulfill({ status: 201, json: reserva });
      }
      return route.fulfill({ json: datos.reservas });
    }
    if (ruta === "/api/v1/vehiculos") return route.fulfill({ json: [vehiculo] });
    if (ruta === "/api/v1/cocheras/disponibles") return route.fulfill({ json: [cochera] });
    if (ruta === "/api/v1/tarifas/cotizacion") {
      return route.fulfill({ json: { tipo: "AUTO", total: 2000, horas: 2 } });
    }
    if (ruta === "/api/v1/accesos/movimientos") return route.fulfill({ json: datos.movimientos });
    if (ruta === "/api/v1/accesos/consulta") {
      datos.consultas.push(request.postDataJSON());
      const finalizada = datos.registros.length === 2;
      const accion = datos.registros.length === 0 ? "INGRESO" : "EGRESO";
      return route.fulfill({ json: {
        codigo: CODIGO, valido: !finalizada, accion: finalizada ? null : accion,
        motivo: finalizada ? "YA_UTILIZADA" : null,
        mensaje: finalizada ? "Esta reserva ya registró su ingreso y egreso." : "Puede continuar.",
        reserva: { ...resumen, estado: finalizada ? "FINALIZADA" : accion === "INGRESO" ? "CONFIRMADA" : "EN_CURSO" },
      } });
    }
    if (ruta === "/api/v1/accesos/registro") {
      const registro = request.postDataJSON();
      datos.registros.push(registro);
      const fecha = registro.accion === "INGRESO" ? "2026-10-11T15:05:00" : "2026-10-11T16:45:00";
      datos.movimientos.unshift({
        ...resumen, id: `movimiento-${datos.registros.length}`, accion: registro.accion, fecha, operador: "Operador de prueba",
      });
      return route.fulfill({ json: {
        accion: registro.accion, fecha, yaRegistrado: false, excedido: false, reserva: resumen,
        mensaje: registro.accion === "INGRESO" ? "Ingreso registrado correctamente." : "Egreso registrado correctamente.",
      } });
    }
    return route.fulfill({ json: [] });
  });
  return datos;
}

async function leerQR(dialogo) {
  const imagen = dialogo.getByRole("img", { name: "Código QR de acceso ABCDE-12345" });
  await expect(imagen).toBeVisible();
  // Rasterizamos el SVG que generó el componente y lo decodificamos con la
  // biblioteca real: no alcanza con verificar que exista una etiqueta <img>.
  const pixeles = await imagen.evaluate(async (elemento) => {
    await elemento.decode();
    const canvas = document.createElement("canvas");
    canvas.width = canvas.height = 480;
    const contexto = canvas.getContext("2d");
    contexto.drawImage(elemento, 0, 0, 480, 480);
    return { data: Array.from(contexto.getImageData(0, 0, 480, 480).data), src: elemento.src };
  });
  const lectura = jsQR(new Uint8ClampedArray(pixeles.data), 480, 480);
  expect(lectura?.data).toBe(PAYLOAD);
  return pixeles.src;
}

for (const { tema, ancho } of [{ tema: "dark", ancho: 390 }, { tema: "light", ancho: 1280 }]) {
  test(`USER recibe un QR legible al confirmar y puede volver a abrirlo: ${tema}`, async ({ page, context, baseURL }) => {
    await page.setViewportSize({ width: ancho, height: 1000 });
    const datos = await prepararPagina(page, context, baseURL, { nueva: true, tema });
    const errores = [];
    page.on("pageerror", (error) => errores.push(error.message));
    await page.goto("/dashboard-user");
    await page.getByLabel("Patente", { exact: true }).selectOption(vehiculo.patente);
    await page.getByLabel("Desde", { exact: true }).fill("2026-10-11T15:00");
    await page.getByLabel("Hasta", { exact: true }).fill("2026-10-11T17:00");
    await page.getByLabel("Cochera", { exact: true }).selectOption(cochera.id);
    await page.getByRole("button", { name: "Confirmar reserva", exact: true }).click();
    const dialogo = page.getByRole("dialog", { name: "Reserva confirmada: tu código de acceso" });
    await expect(dialogo).toBeVisible();
    await leerQR(dialogo);
    await expect(dialogo.getByLabel("Código ABCDE-12345", { exact: true })).toHaveText("ABCDE-12345");
    await expect(dialogo.getByRole("note")).toHaveText("Ingresá entre 14:45 y 15:30.");
    expect(datos.altas).toEqual([{
      vehiculoId: vehiculo.id, cocheraId: cochera.id,
      desde: "2026-10-11T15:00", hasta: "2026-10-11T17:00", precioEsperado: 2000,
    }]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    await page.keyboard.press("Escape");
    await expect(dialogo).toHaveCount(0);
    const abrir = page.getByRole("button", { name: "Ver QR", exact: true });
    await abrir.click();
    const reabierto = page.getByRole("dialog", { name: "Tu código de acceso", exact: true });
    await leerQR(reabierto);
    await reabierto.getByRole("button", { name: "Cerrar ventana" }).click();
    await expect(abrir).toBeFocused();
    expect(errores).toEqual([]);
  });
}

test("ADMIN usa el mismo código para ingreso y egreso, y muestra el rechazo al reutilizarlo", async ({ page, context, baseURL }) => {
  const datos = await prepararPagina(page, context, baseURL, { rol: "ADMIN" });
  await page.goto("/dashboard-admin/accesos");
  await page.getByRole("tab", { name: "Escribir código", exact: true }).click();
  const campo = page.getByLabel("Código de acceso", { exact: true });
  for (const [texto, accion, etiqueta] of [["abcde-12345", "INGRESO", "ingreso"], [PAYLOAD, "EGRESO", "egreso"]]) {
    await campo.fill(texto);
    await page.getByRole("button", { name: "Validar", exact: true }).click();
    await expect(page.getByRole("article", { name: `Corresponde: ${accion}` })).toBeVisible();
    await page.getByRole("button", { name: `Registrar ${etiqueta}`, exact: true }).click();
    await expect(page.getByRole("status").filter({ hasText: `${etiqueta === "ingreso" ? "Ingreso" : "Egreso"} registrado correctamente.` })).toBeVisible();
  }
  await campo.fill(CODIGO);
  await page.getByRole("button", { name: "Validar", exact: true }).click();
  await expect(page.getByRole("article", { name: "Código ya utilizado" })).toBeVisible();
  await expect(page.getByRole("button", { name: /Registrar (ingreso|egreso)/ })).toHaveCount(0);
  await expect(page.getByRole("list", { name: "Ingresos y egresos de hoy" }).getByRole("listitem")).toHaveCount(2);
  expect(datos.consultas).toEqual(Array.from({ length: 3 }, () => ({ codigo: CODIGO })));
  expect(datos.registros).toEqual([{ codigo: CODIGO, accion: "INGRESO" }, { codigo: CODIGO, accion: "EGRESO" }]);
});

for (const detectorNativo of ["ausente", "falla"]) {
  test(`el lector decodifica el QR y libera el video con detector nativo ${detectorNativo}`, async ({ page, context, baseURL }) => {
    const datos = await prepararPagina(page, context, baseURL);
    await page.goto("/dashboard-user");
    await page.getByRole("button", { name: "Ver QR", exact: true }).click();
    const src = await leerQR(page.getByRole("dialog"));
    await autenticar(context, baseURL, "ADMIN");
    await page.addInitScript(({ src, detectorNativo }) => {
      // Se reemplaza únicamente la entrada física de cámara. El video, canvas
      // y decodificador jsqr de la aplicación funcionan sin mocks.
      Object.defineProperty(window, "BarcodeDetector", {
        configurable: true,
        value: detectorNativo === "ausente" ? undefined : class {
          static async getSupportedFormats() { return ["qr_code"]; }
          async detect() { throw new DOMException("Video no soportado", "NotSupportedError"); }
        },
      });
      Object.defineProperty(navigator.mediaDevices, "getUserMedia", { configurable: true, value: async () => {
        const imagen = new Image();
        imagen.src = src;
        await imagen.decode();
        const canvas = document.createElement("canvas");
        canvas.width = canvas.height = 480;
        canvas.getContext("2d").drawImage(imagen, 0, 0, 480, 480);
        window.__camaraPrueba = canvas.captureStream(10);
        return window.__camaraPrueba;
      } });
    }, { src, detectorNativo });
    await page.goto("/dashboard-admin/accesos");
    await page.getByRole("button", { name: "Activar cámara", exact: true }).click();
    await expect(page.getByRole("article", { name: "Corresponde: INGRESO" })).toBeVisible();
    expect(datos.consultas).toEqual([{ codigo: CODIGO }]);
    expect(datos.registros).toEqual([]);
    expect(await page.evaluate(() => window.__camaraPrueba.getTracks().every((track) => track.readyState === "ended"))).toBe(true);
  });
}

test("sin permiso de cámara el operador puede validar el código escrito", async ({ page, context, baseURL }) => {
  const datos = await prepararPagina(page, context, baseURL, { rol: "ADMIN" });
  await page.addInitScript(() => {
    Object.defineProperty(navigator.mediaDevices, "getUserMedia", { configurable: true, value: async () => {
      throw new DOMException("Permiso denegado en la prueba", "NotAllowedError");
    } });
  });
  await page.goto("/dashboard-admin/accesos");
  await page.getByRole("button", { name: "Activar cámara", exact: true }).click();
  await expect(page.getByRole("tabpanel", { name: "Escanear QR" }).getByRole("alert"))
    .toContainText("No diste permiso para usar la cámara");
  await page.getByRole("button", { name: "Escribir el código", exact: true }).click();
  await page.getByLabel("Código de acceso", { exact: true }).fill("ABCDE-12345");
  await page.getByRole("button", { name: "Validar", exact: true }).click();
  await expect(page.getByRole("article", { name: "Corresponde: INGRESO" })).toBeVisible();
  expect(datos.consultas).toEqual([{ codigo: CODIGO }]);
});
