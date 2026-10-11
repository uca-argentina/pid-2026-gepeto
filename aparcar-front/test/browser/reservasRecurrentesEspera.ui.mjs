import { test, expect } from "@playwright/test";

const visitante = { id: "titular-ui", nombre: "Titular de prueba", tieneDiscapacidad: false };
const vehiculo = { id: "vehiculo-ui", patente: "ABC123", tipo: "AUTO", visitanteId: visitante.id };
const cochera = { id: "cochera-ui", numero: "A-01", sector: "Planta baja", tipo: "AUTO" };
const desde = "2030-10-14T09:00";
const hasta = "2030-10-14T11:00";
const reserva = {
  id: "espera-ui", visitante, vehiculo, cochera: null, desde, hasta,
  fechaCreacion: "2030-10-01T12:00:00Z", estado: "LISTA_ESPERA", modalidad: "FRANJA",
  precioTotal: 2000, tipoTarifa: "AUTO", historial: [], acceso: null,
};

// API aislada para comprobar interfaz y contratos sin alterar reservas reales.
async function preparar(page, context, baseURL, rol, disponibles = []) {
  const token = [Buffer.from("{}").toString("base64url"), Buffer.from(JSON.stringify({
    email: "espera-ui@test.invalid", authorities: rol, exp: 4102444800,
  })).toString("base64url"), "prueba-ui"].join(".");
  await context.addCookies([{ name: "JWT", value: token, url: baseURL }]);
  await page.addInitScript((url) => { window.__ENV = { NEXT_PUBLIC_API_BASE_URL: url }; }, baseURL);
  const datos = { reservas: [], vehiculos: [vehiculo], notificaciones: [], altas: [], altasVehiculos: [], leidas: 0, errorDisponibilidad: false };
  await page.route("**/api/v1/**", async (route) => {
    const req = route.request();
    const ruta = new URL(req.url()).pathname;
    if (ruta === "/api/v1/vehiculos") return route.fulfill({ json: [vehiculo] });
    if (ruta === "/api/v1/vehiculos/mios") {
      if (req.method() === "POST") {
        datos.altasVehiculos.push(req.postDataJSON());
        datos.vehiculos = [vehiculo];
        return route.fulfill({ status: 201, json: vehiculo });
      }
      return route.fulfill({ json: datos.vehiculos });
    }
    if (ruta === "/api/v1/visitantes") return route.fulfill({ json: [visitante] });
    if (ruta === "/api/v1/visitantes/alta") {
      datos.altas.push(req.postDataJSON());
      return route.fulfill({ status: 201, json: { visitante, vehiculo, reserva } });
    }
    if (ruta === "/api/v1/cocheras/disponibles") return route.fulfill(datos.errorDisponibilidad
      ? { status: 500, json: { message: "Consulta fallida" } } : { json: disponibles });
    if (ruta === "/api/v1/tarifas/cotizacion") return route.fulfill({ json: { tipo: "AUTO", total: 2000, horas: 2 } });
    if (ruta === "/api/v1/reservas/notificaciones") return route.fulfill({ json: datos.notificaciones });
    if (ruta.endsWith("/notificacion/leida")) {
      datos.leidas += 1;
      datos.notificaciones = [];
      return route.fulfill({ json: { ...datos.reservas[0], notificacionLeidaAt: "2030-10-01T13:00:00Z" } });
    }
    if (ruta.endsWith("/cancelar")) {
      datos.reservas = datos.reservas.map((r) => ({ ...r, estado: "CANCELADA", motivoCancelacion: "USUARIO" }));
      return route.fulfill({ json: datos.reservas[0] });
    }
    if (ruta === "/api/v1/reservas/recurrentes") {
      if (req.method() === "GET") return route.fulfill({ json: datos.reservas.filter((r) => r.recurrenciaId) });
      datos.altas.push(req.postDataJSON());
      datos.reservas = [0, 1].map((i) => ({ ...reserva, id: `serie-${i}`, cochera,
        estado: "CONFIRMADA", recurrenciaId: "serie-ui", desde: `2030-10-${14 + i * 7}T09:00:00`, hasta: `2030-10-${14 + i * 7}T11:00:00` }));
      return route.fulfill({ status: 201, json: datos.reservas });
    }
    if (ruta === "/api/v1/reservas") {
      if (req.method() === "POST") {
        datos.altas.push(req.postDataJSON());
        datos.reservas = [reserva];
        return route.fulfill({ status: 201, json: reserva });
      }
      return route.fulfill({ json: datos.reservas });
    }
    return route.fulfill({ json: [] });
  });
  return datos;
}

async function completar(page, rol) {
  await page.goto(rol === "ADMIN" ? "/dashboard-admin/reservas" : "/dashboard-user");
  const patente = page.getByLabel("Patente", { exact: true });
  if (rol === "ADMIN") await patente.fill(vehiculo.patente);
  else await patente.selectOption(vehiculo.patente);
  await page.getByLabel("Desde", { exact: true }).fill(desde);
  await page.getByLabel("Hasta", { exact: true }).fill(hasta);
  await page.getByLabel("Hasta", { exact: true }).blur();
}

for (const rol of ["USER", "ADMIN"]) {
  test(`${rol} acepta la espera, ve su estado sin QR y puede cancelarla`, async ({ page, context, baseURL }, testInfo) => {
    await page.setViewportSize({ width: 390, height: 1000 });
    const datos = await preparar(page, context, baseURL, rol);
    await completar(page, rol);
    await expect(page.getByText("No hay cocheras disponibles para esa franja.", { exact: true })).toBeVisible();
    await expect(page.getByRole("button", { name: "Confirmar reserva", exact: true })).toBeDisabled();
    await testInfo.attach("aviso-espera", { body: await page.screenshot({ path: testInfo.outputPath("aviso-espera.png"), fullPage: true }), contentType: "image/png" });
    await page.getByLabel("Acepto ingresar a la lista de espera").check();
    await page.getByRole("button", { name: "Ingresar a lista de espera", exact: true }).click();
    await expect(page.getByRole("button", { name: "En espera 1", exact: true })).toBeVisible();
    await page.getByRole("button", { name: "En espera 1", exact: true }).click();
    await expect(page.getByRole("button", { name: "Ver QR", exact: true })).toHaveCount(0);
    expect(datos.altas).toEqual([{ visitanteId: rol === "ADMIN" ? visitante.id : undefined,
      vehiculoId: vehiculo.id, cocheraId: null, listaEspera: true, tipoCochera: "AUTO", desde, hasta, precioEsperado: 2000 }]);
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    page.on("dialog", (dialogo) => {
      expect(dialogo.message()).not.toContain("undefined");
      return dialogo.accept();
    });
    await page.getByRole("button", { name: "Cancelar", exact: true }).click();
    await expect(page.getByRole("button", { name: "En espera 0", exact: true })).toBeVisible();
  });
}

test("ADMIN agrega su vehículo y confirma su serie desde la sección exclusiva", async ({ page, context, baseURL }, testInfo) => {
  const datos = await preparar(page, context, baseURL, "ADMIN", [cochera]);
  datos.vehiculos = [];
  await page.setViewportSize({ width: 390, height: 1000 });
  await page.goto("/dashboard-admin/recurrentes");
  const vehiculosPropios = page.getByRole("region", { name: "Mis vehículos" });
  await vehiculosPropios.getByLabel("Patente", { exact: true }).fill("ABC123");
  await vehiculosPropios.getByRole("button", { name: "Agregar", exact: true }).click();
  await expect(vehiculosPropios.getByRole("img", { name: "Patente ABC123" })).toBeVisible();
  expect(datos.altasVehiculos).toEqual([{ patente: "ABC123", tipo: "AUTO" }]);
  await page.locator("#reserva-patente").selectOption(vehiculo.patente);
  await page.getByLabel("Desde", { exact: true }).fill(desde);
  await page.getByLabel("Hasta", { exact: true }).fill(hasta);
  await page.getByLabel("Cochera", { exact: true }).selectOption(cochera.id);
  await page.getByRole("group", { name: "Días de la semana" }).getByLabel("Lunes", { exact: true }).check();
  await page.getByLabel("Repetir hasta (inclusive)").fill("2030-10-21");
  await page.screenshot({ path: testInfo.outputPath("mis-recurrentes.png"), fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
  await page.getByRole("button", { name: "Confirmar reservas recurrentes", exact: true }).click();
  await expect(page.getByRole("button", { name: "Activas 2", exact: true })).toBeVisible();
  expect(datos.altas[0]).toMatchObject({ diasSemana: [1], repetirHasta: "2030-10-21", cocheraId: cochera.id });
  expect(datos.altas[0]).not.toHaveProperty("visitanteId");
  await completar(page, "ADMIN");
  await expect(page.getByRole("group", { name: "Días de la semana" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Confirmar reservas recurrentes", exact: true })).toHaveCount(0);
  await preparar(page, context, baseURL, "USER");
  await page.goto("/dashboard-user");
  await expect(page.getByRole("group", { name: "Días de la semana" })).toHaveCount(0);
  await page.goto("/dashboard-admin/recurrentes");
  await expect(page).toHaveURL(/\/unauthorized$/);
});

test("ADMIN da de alta un visitante con moto en lista de espera", async ({ page, context, baseURL }) => {
  const datos = await preparar(page, context, baseURL, "ADMIN");
  await page.goto("/dashboard-admin/visitantes");
  await page.getByLabel("Nombre", { exact: true }).fill("Visitante de prueba");
  await page.getByLabel("Documento", { exact: true }).fill("30111222");
  await page.getByLabel("Email", { exact: true }).fill("qa-moto@example.invalid");
  await page.getByLabel("Tipo de vehículo", { exact: true }).selectOption("MOTO");
  await page.getByLabel("Patente", { exact: true }).fill("A 123 BCD");
  await page.getByLabel("Desde", { exact: true }).fill(desde);
  await page.getByLabel("Hasta", { exact: true }).fill(hasta);
  await page.getByLabel("Acepto ingresar a la lista de espera").check();
  await page.getByRole("button", { name: "Dar de alta e ingresar a lista de espera", exact: true }).click();
  await expect(page.getByText(/Visitante dado de alta y reserva en lista de espera/)).toBeVisible();
  expect(datos.altas[0]).toMatchObject({ patente: "A123BCD", tipoVehiculo: "MOTO", tipoCochera: "MOTO", cocheraId: null, listaEspera: true });
});

test("la notificación de promoción permanece al recargar hasta marcarla leída", async ({ page, context, baseURL }) => {
  const datos = await preparar(page, context, baseURL, "USER");
  const confirmada = { ...reserva, estado: "CONFIRMADA", cochera,
    confirmadaDesdeEsperaAt: "2030-10-01T12:30:00Z", acceso: { codigo: "ABCDE12345" } };
  datos.reservas = datos.notificaciones = [confirmada];
  await page.goto("/dashboard-user");
  const aviso = page.getByRole("region", { name: "Notificaciones de reservas" });
  await expect(aviso).toBeVisible();
  await page.reload();
  await expect(aviso).toBeVisible();
  await aviso.getByRole("button", { name: "Marcar como leída" }).click();
  await expect(aviso).toHaveCount(0);
  expect(datos.leidas).toBe(1);
  await page.reload();
  await expect(aviso).toHaveCount(0);
});

test("una consulta fallida no ofrece ingresar a lista de espera", async ({ page, context, baseURL }) => {
  const datos = await preparar(page, context, baseURL, "USER");
  datos.errorDisponibilidad = true;
  await completar(page, "USER");
  await expect(page.getByRole("button", { name: "Reintentar disponibilidad" })).toBeVisible();
  await expect(page.getByLabel("Acepto ingresar a la lista de espera")).toHaveCount(0);
});
