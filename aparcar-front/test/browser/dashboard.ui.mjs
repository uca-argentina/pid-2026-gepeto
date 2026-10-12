import { test, expect } from "@playwright/test";

// El tráfico de la API se intercepta por completo: ninguna prueba modifica
// reservas, accesos ni otros datos del estacionamiento real.
const resumen = {
  fecha: "2026-10-11", actualizadoAt: "2026-10-11T14:35:00", zonaHoraria: "America/Argentina/Buenos_Aires",
  ocupacion: { total: 10, habilitadas: 8, fueraServicio: 2, ocupadas: 6, ocupadasHabilitadas: 6, ocupadasFueraServicio: 0, libres: 2, porcentaje: 75 },
  reservasHoy: { total: 17, pendientes: 4, enCurso: 6, finalizadas: 3, canceladas: 1, noPresentados: 3 },
  operacion: { ingresosHoy: 9, egresosHoy: 3, permanenciasExcedidas: 2, proximosIngresos: 2 },
  actividadPorHora: Array.from({ length: 24 }, (_, hora) => ({ hora, ingresos: hora === 10 ? 9 : 0, egresos: hora === 13 ? 3 : 0 })),
  sectores: [
    { sector: "Planta baja del edificio de administración", total: 7, habilitadas: 6, ocupadas: 5, ocupadasHabilitadas: 5, ocupadasFueraServicio: 0, libres: 1, ingresosHoy: 7, porcentajeOcupacion: 83.3 },
    { sector: "Subsuelo", total: 3, habilitadas: 2, ocupadas: 1, ocupadasHabilitadas: 1, ocupadasFueraServicio: 0, libres: 1, ingresosHoy: 2, porcentajeOcupacion: 50 },
  ],
  proximasReservas: [{ id: "r-ui", desde: "2026-10-11T15:00:00", hasta: "2026-10-11T17:00:00", cocheraNumero: "A-07", sector: "Planta baja del edificio de administración" }],
};

async function prepararPagina(page, context, baseURL, { tema = "light", rol = "ADMIN" } = {}) {
  const token = [
    Buffer.from("{}").toString("base64url"),
    Buffer.from(JSON.stringify({ sub: "dashboard-ui@test.invalid", authorities: rol, exp: 4102444800 })).toString("base64url"),
    "prueba-ui",
  ].join(".");
  await context.addCookies([{ name: "JWT", value: token, url: baseURL }]);
  await page.addInitScript(({ baseURL, tema }) => {
    window.__ENV = { NEXT_PUBLIC_API_BASE_URL: baseURL };
    localStorage.setItem("aparcar-theme", tema);
  }, { baseURL, tema });
  const estado = { consultas: 0, fallar: false, solicitudes: [] };
  await page.route("**/api/v1/**", (route) => {
    const request = route.request();
    const ruta = new URL(request.url()).pathname;
    estado.solicitudes.push({ ruta, metodo: request.method() });
    if (ruta === "/api/v1/dashboard") {
      estado.consultas += 1;
      return route.fulfill(estado.fallar
        ? { status: 503, json: { message: "Servicio temporalmente no disponible." } }
        : { json: resumen });
    }
    if (ruta === "/api/v1/visitantes/me") return route.fulfill({ json: { nombre: "Administrador de prueba" } });
    return route.fulfill({ json: [] });
  });
  return estado;
}

for (const tema of ["light", "dark"]) {
  for (const ancho of [360, 768, 1440]) {
    test(`Dashboard responsive y accesible: ${tema}, ${ancho}px`, async ({ page, context, baseURL }, testInfo) => {
      await page.setViewportSize({ width: ancho, height: 960 });
      const estado = await prepararPagina(page, context, baseURL, { tema });
      const errores = [];
      page.on("pageerror", (error) => errores.push(error.message));
      await page.goto("/dashboard-admin/dashboard");

      await expect(page.getByRole("heading", { name: "Dashboard", exact: true })).toBeVisible();
      const ocupacion = page.getByRole("article", { name: "Ocupación actual" });
      await expect(ocupacion).toContainText("75%");
      await expect(page.getByRole("article", { name: "Reservas del día" })).toContainText("17");
      await expect(page.getByRole("article", { name: "No presentados" })).toContainText("3");
      await expect(page.getByRole("article", { name: "Estadías excedidas" })).toContainText("2");
      expect(await page.locator("html").evaluate((elemento) => elemento.classList.contains("dark"))).toBe(tema === "dark");

      // Verifica geometría y CSS para que una hoja de estilos ausente no pase
      // por casualidad el chequeo de desbordes.
      expect(await ocupacion.evaluate((elemento) => parseFloat(getComputedStyle(elemento).paddingTop))).toBeGreaterThanOrEqual(12);
      for (const tarjeta of await page.getByRole("article").all()) {
        const caja = await tarjeta.boundingBox();
        expect(caja.x).toBeGreaterThanOrEqual(0);
        expect(caja.x + caja.width).toBeLessThanOrEqual(ancho + 1);
      }
      const detalle = page.getByText("Ver detalle por hora");
      await detalle.scrollIntoViewIfNeeded();
      await detalle.focus();
      await page.keyboard.press("Enter");
      const tabla = page.getByRole("table");
      await expect(tabla).toBeVisible();
      await expect(tabla.getByRole("row")).toHaveCount(25);
      await expect(tabla.getByRole("row", { name: /10:00/ })).toContainText("9");
      expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
      expect(estado.solicitudes.every(({ metodo }) => metodo === "GET")).toBe(true);
      expect(errores).toEqual([]);
      await detalle.click();
      await page.evaluate(() => window.scrollTo(0, 0));
      const captura = await page.screenshot({ fullPage: true, path: `test-results/dashboard-${tema}-${ancho}.png` });
      await testInfo.attach(`dashboard-${tema}-${ancho}`, { body: captura, contentType: "image/png" });
    });
  }
}

test("reintenta el error inicial y conserva métricas si la siguiente actualización falla", async ({ page, context, baseURL }) => {
  const estado = await prepararPagina(page, context, baseURL);
  estado.fallar = true;
  await page.goto("/dashboard-admin/dashboard");
  await expect(page.getByRole("main").getByRole("alert")).toContainText("No se pudo cargar el dashboard.");
  await expect(page.getByRole("article", { name: "Ocupación actual" })).toHaveCount(0);

  estado.fallar = false;
  await page.getByRole("button", { name: "Reintentar" }).click();
  await expect(page.getByRole("article", { name: "Ocupación actual" })).toContainText("75%");
  estado.fallar = true;
  await page.getByRole("button", { name: "Actualizar", exact: true }).click();
  await expect(page.getByRole("main").getByRole("alert")).toContainText("Mostramos los últimos datos disponibles.");
  await expect(page.getByRole("article", { name: "Reservas del día" })).toContainText("17");
  expect(estado.consultas).toBe(3);
});

test("un USER no accede al Dashboard ni consulta sus datos", async ({ page, context, baseURL }) => {
  const estado = await prepararPagina(page, context, baseURL, { rol: "USER" });
  await page.goto("/dashboard-admin/dashboard");

  await expect(page).toHaveURL(/\/unauthorized$/);
  await expect(page.getByRole("heading", { name: "Acceso Denegado" })).toBeVisible();
  expect(estado.consultas).toBe(0);
});
