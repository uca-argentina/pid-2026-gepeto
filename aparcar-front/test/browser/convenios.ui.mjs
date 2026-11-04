import { test, expect } from "@playwright/test";

const titular = { id: "titular", nombre: "Visitante convenios", documento: "30111222", email: "convenios@example.invalid", telefono: "", tieneDiscapacidad: false };
const vehiculo = { id: "vehiculo", patente: "ABC123", tipo: "AUTO", visitanteId: titular.id };
const cochera = { id: "cochera", numero: "A-01", tipo: "AUTO", sector: "Planta baja" };
const iniciales = [
  { id: "uca", nombre: "UCA", formatoCredencial: "UCA-XXX", descuentoPct: 15 },
  { id: "nacion", nombre: "Club Nación", formatoCredencial: "NACION-XXX", descuentoPct: 10 },
];

// Navegación y estilos reales de Next, con datos aislados del estacionamiento.
async function preparar(page, context, baseURL, rol) {
  const token = [Buffer.from("{}").toString("base64url"), Buffer.from(JSON.stringify({
    sub: titular.email, authorities: rol, exp: 4102444800,
  })).toString("base64url"), "prueba-ui"].join(".");
  await context.addCookies([{ name: "JWT", value: token, url: baseURL }]);
  await page.addInitScript((url) => { window.__ENV = { NEXT_PUBLIC_API_BASE_URL: url }; }, baseURL);
  const estado = { convenios: iniciales.map((c) => ({ ...c })), credenciales: [], reservas: [], altas: [] };
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const ruta = new URL(request.url()).pathname;
    const metodo = request.method();
    const body = ["POST", "PUT"].includes(metodo) ? request.postDataJSON() : null;
    if (ruta === "/api/v1/convenios") {
      if (metodo === "POST") {
        const convenio = { id: "nuevo", ...body };
        estado.convenios.push(convenio);
        return route.fulfill({ status: 201, json: convenio });
      }
      return route.fulfill({ json: estado.convenios });
    }
    if (ruta === "/api/v1/convenios/credenciales" && metodo === "GET") return route.fulfill({ json: estado.credenciales });
    if (ruta === "/api/v1/convenios/verificar" || (ruta === "/api/v1/convenios/credenciales" && metodo === "PUT")) {
      const convenio = estado.convenios.find((c) => c.id === body.convenioId);
      if (body.credencial !== convenio.formatoCredencial.replace("XXX", "222")) {
        return route.fulfill({ status: 400, json: { message: "La credencial no coincide con los últimos 3 dígitos del documento." } });
      }
      const credencial = { ...convenio, convenioId: convenio.id, credencial: body.credencial };
      if (metodo === "PUT") estado.credenciales = [...estado.credenciales.filter((c) => c.convenioId !== convenio.id), credencial];
      return route.fulfill({ json: credencial });
    }
    if (ruta.startsWith("/api/v1/convenios/credenciales/") && metodo === "DELETE") {
      estado.credenciales = estado.credenciales.filter((c) => c.convenioId !== ruta.split("/").at(-1));
      return route.fulfill({ status: 204 });
    }
    if (ruta.startsWith("/api/v1/convenios/")) {
      const id = ruta.split("/").at(-1);
      if (metodo === "DELETE") {
        estado.convenios = estado.convenios.filter((c) => c.id !== id);
        return route.fulfill({ status: 204 });
      }
      estado.convenios = estado.convenios.map((c) => c.id === id ? { id, ...body } : c);
      return route.fulfill({ json: { id, ...body } });
    }
    if (ruta === "/api/v1/visitantes/me") return route.fulfill({ json: titular });
    if (ruta === "/api/v1/visitantes") return route.fulfill({ json: [titular] });
    if (["/api/v1/vehiculos", "/api/v1/vehiculos/mios"].includes(ruta)) return route.fulfill({ json: [vehiculo] });
    if (ruta === "/api/v1/cocheras/disponibles") return route.fulfill({ json: [cochera] });
    if (ruta === "/api/v1/tarifas/cotizacion") {
      const ids = [...estado.credenciales, ...(body?.credencialesConvenio ?? [])].map((c) => c.convenioId);
      const mejor = estado.convenios.filter((c) => ids.includes(c.id)).sort((a, b) => b.descuentoPct - a.descuentoPct)[0];
      const convenio = mejor ? { convenioId: mejor.id, nombre: mejor.nombre, descuentoPct: mejor.descuentoPct, descuentoMonto: 1000 * mejor.descuentoPct / 100 } : null;
      return route.fulfill({ json: { tipo: "AUTO", subtotal: 1000, total: 1000 - (convenio?.descuentoMonto ?? 0), moneda: "ARS", horas: 1, convenio } });
    }
    if (ruta === "/api/v1/reservas") {
      if (metodo === "POST") {
        estado.altas.push(body);
        const reserva = { id: "reserva", desde: body.desde, hasta: body.hasta, visitante: titular, vehiculo, cochera,
          estado: "CONFIRMADA", modalidad: "FRANJA", precioTotal: 1000, precioFinal: 850,
          convenio: { convenioId: "uca", nombre: "UCA", descuentoPct: 15, descuentoMonto: 150 }, historial: [] };
        estado.reservas.push(reserva);
        return route.fulfill({ status: 201, json: reserva });
      }
      return route.fulfill({ json: estado.reservas });
    }
    if (ruta === "/api/v1/verificaciones-discapacidad/mia") return route.fulfill({ json: { estadoDiscapacidad: "NO_DECLARADA" } });
    return route.fulfill({ json: [] });
  });
  return estado;
}

async function sinDesborde(page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
}

for (const ancho of [320, 768, 1440]) {
  test(`ADMIN gestiona convenios a ${ancho}px`, async ({ page, context, baseURL }, testInfo) => {
    await page.setViewportSize({ width: ancho, height: 1000 });
    const estado = await preparar(page, context, baseURL, "ADMIN");
    await page.goto("/dashboard-admin/convenios");
    await expect(page.getByRole("heading", { name: "UCA", exact: true })).toBeVisible();
    await page.getByLabel("Nombre del convenio", { exact: true }).fill("Instituto de formación profesional y desarrollo universitario");
    await page.getByLabel("Formato de credencial", { exact: true }).fill("INSTITUTO-XXX");
    await page.getByLabel("Descuento (%)", { exact: true }).fill("22,5");
    await page.getByRole("button", { name: "Agregar convenio", exact: true }).click();
    await expect(page.getByText("22,5% de descuento", { exact: true })).toBeVisible();
    expect(estado.convenios.at(-1).descuentoPct).toBe(22.5);
    await page.getByRole("button", { name: "Editar UCA", exact: true }).click();
    await page.getByLabel("Descuento (%)", { exact: true }).fill("18");
    await page.getByRole("button", { name: "Guardar convenio", exact: true }).click();
    await expect(page.getByText("18% de descuento", { exact: true })).toBeVisible();
    await sinDesborde(page);
    await testInfo.attach("convenios-admin", { body: await page.screenshot({ fullPage: true }), contentType: "image/png" });
    page.on("dialog", (dialogo) => dialogo.accept());
    await page.getByRole("button", { name: "Eliminar Club Nación", exact: true }).click();
    await expect(page.getByRole("heading", { name: "Club Nación", exact: true })).toHaveCount(0);
  });
}

test("Mis datos valida, guarda y elimina la credencial; la reserva la recupera", async ({ page, context, baseURL }, testInfo) => {
  await page.setViewportSize({ width: 320, height: 1000 });
  const estado = await preparar(page, context, baseURL, "USER");
  await page.goto("/dashboard-user/perfil");
  const misConvenios = page.getByRole("region", { name: "Mis convenios", exact: true });
  await misConvenios.getByLabel("Credencial UCA", { exact: true }).fill("UCA-999");
  await misConvenios.getByRole("button", { name: "Verificar y guardar", exact: true }).first().click();
  await expect(misConvenios.getByRole("alert")).toContainText("no coincide");
  expect(estado.credenciales).toHaveLength(0);
  await misConvenios.getByLabel("Credencial UCA", { exact: true }).fill("UCA-222");
  await misConvenios.getByRole("button", { name: "Verificar y guardar", exact: true }).first().click();
  await expect(misConvenios.getByText("Credencial validada y guardada en Mis datos.", { exact: true })).toBeVisible();
  await sinDesborde(page);
  await testInfo.attach("convenios-perfil", { body: await misConvenios.screenshot(), contentType: "image/png" });
  await page.goto("/dashboard-user");
  await expect(page.getByLabel("Credencial UCA", { exact: true })).toHaveValue("UCA-222");
  await expect(page.getByText("Credencial validada y guardada en Mis datos.", { exact: true })).toBeVisible();
  await page.goto("/dashboard-user/perfil");
  await misConvenios.getByRole("button", { name: "Eliminar", exact: true }).click();
  await expect(misConvenios.getByLabel("Credencial UCA", { exact: true })).toHaveValue("");
  expect(estado.credenciales).toHaveLength(0);
});

for (const rol of ["USER", "ADMIN", "EMPLEADO"]) {
  test(`${rol} reserva con ambas credenciales y muestra solo el mayor descuento`, async ({ page, context, baseURL }, testInfo) => {
    await page.setViewportSize({ width: 390, height: 1000 });
    const estado = await preparar(page, context, baseURL, rol);
    await page.goto(rol === "USER" ? "/dashboard-user" : "/dashboard-admin/reservas");
    const patente = page.getByLabel("Patente", { exact: true });
    if (rol === "USER") await patente.selectOption(vehiculo.patente);
    else await patente.fill(vehiculo.patente);
    await page.getByLabel("Desde", { exact: true }).fill("2030-10-14T09:00");
    await page.getByLabel("Hasta", { exact: true }).fill("2030-10-14T10:00");
    await page.getByLabel("Cochera", { exact: true }).selectOption(cochera.id);
    for (const [nombre, valor] of [["UCA", "UCA-222"], ["Club Nación", "NACION-222"]]) {
      const input = page.getByLabel(`Credencial ${nombre}`, { exact: true });
      await input.fill(valor);
      await input.locator("..").getByRole("button", { name: "Verificar", exact: true }).click();
    }
    await expect(page.getByText("Credencial validada para esta reserva.", { exact: true })).toHaveCount(2);
    await expect(page.getByText(/Descuento.*UCA.*15/)).toBeVisible();
    await sinDesborde(page);
    await testInfo.attach(`convenios-${rol}`, { body: await page.screenshot({ fullPage: true }), contentType: "image/png" });
    await page.getByRole("button", { name: "Confirmar reserva", exact: true }).click();
    await expect.poll(() => estado.altas.length).toBe(1);
    expect(estado.altas[0]).toMatchObject({ precioEsperado: 850, credencialesConvenio: [
      { convenioId: "uca", credencial: "UCA-222" }, { convenioId: "nacion", credencial: "NACION-222" },
    ] });
    if (rol === "USER") await page.goto("/dashboard-user/reservas");
    await expect(page.getByText(/UCA.*15/).last()).toBeVisible();
    await sinDesborde(page);
  });
}

test("EMPLEADO no accede a la administración de convenios", async ({ page, context, baseURL }) => {
  await preparar(page, context, baseURL, "EMPLEADO");
  await page.goto("/dashboard-admin/convenios");
  await expect(page).toHaveURL(/\/unauthorized$/);
});
