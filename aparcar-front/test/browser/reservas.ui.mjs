import { test, expect } from "@playwright/test";

// Datos aislados: las pruebas nunca consultan ni modifican reservas reales.
const reservas = Array.from({ length: 15 }, (_, indice) => ({
  id: `reserva-${indice}`, estado: indice < 12 ? "CONFIRMADA" : indice === 12 ? "FINALIZADA" : "CANCELADA",
  motivoCancelacion: indice === 14 ? "DESHABILITACION" : "USUARIO",
  desde: "2026-10-10T16:45:00", hasta: "2026-10-10T17:45:00",
  fechaCreacion: "2026-10-09T19:53:00Z", precioTotal: 8400, modalidad: "FRANJA",
  visitante: { nombre: "María de los Ángeles Fernández de la Cruz" },
  vehiculo: { patente: "PPL123", tipo: "AUTO" },
  cochera: { numero: "A-02", sector: "Subsuelo del edificio de administración" },
  historial: [{
    id: `alta-${indice}`, accion: "ALTA", fecha: "2026-10-09T19:53:00Z",
    actorNombre: "Administrador de prueba", actorRol: "ADMIN",
    actorEmail: "administrador.con.nombre.extremadamente.largo@estacionamiento-institucional.example.com",
  }],
}));

async function prepararPagina(page, context, baseURL, tema, rol) {
  const token = [
    Buffer.from("{}").toString("base64url"),
    Buffer.from(JSON.stringify({ sub: "ui@test.invalid", authorities: rol, exp: 4102444800 })).toString("base64url"),
    "prueba-ui",
  ].join(".");
  await context.addCookies([{ name: "JWT", value: token, url: baseURL }]);
  await page.addInitScript(({ baseURL, tema }) => {
    window.__ENV = { NEXT_PUBLIC_API_BASE_URL: baseURL };
    localStorage.setItem("aparcar-theme", tema);
  }, { baseURL, tema });
  await page.route("**/api/v1/**", (route) => route.fulfill({
    status: 200, contentType: "application/json",
    body: JSON.stringify(new URL(route.request().url()).pathname === "/api/v1/reservas" ? reservas : []),
  }));
}

for (const { rol, ruta, titulo } of [
  { rol: "ADMIN", ruta: "/dashboard-admin/reservas", titulo: "Todas las reservas" },
  { rol: "USER", ruta: "/dashboard-user", titulo: "Mis reservas" },
]) {
  for (const tema of ["light", "dark"]) {
    for (const ancho of [320, 390, 768, 1024, 1440]) {
      test(`listado ${rol} con estilos y sin desbordes: ${tema} ${ancho}px`, async ({ page, context, baseURL }, testInfo) => {
        await page.setViewportSize({ width: ancho, height: 1000 });
        await prepararPagina(page, context, baseURL, tema, rol);
        const errores = [];
        page.on("pageerror", (error) => errores.push(error.message));
        await page.goto(ruta);
        const listado = page.getByRole("region", { name: titulo });
        const filtros = listado.getByRole("group", { name: "Filtrar reservas por estado" });
        await expect(filtros.getByRole("button", { name: "Activas 12", exact: true })).toBeVisible();

        // Un listado sin CSS puede no desbordarse: también comprobamos estilos
        // calculados y geometría de los controles para detectar esa regresión.
        await expect(filtros).toHaveCSS("display", "grid");
        await expect(filtros).toHaveCSS("gap", "4px");
        const columnas = await filtros.evaluate((elemento) => getComputedStyle(elemento).gridTemplateColumns.split(" ").length);
        const anchoListado = (await listado.boundingBox()).width;
        expect(columnas).toBe(anchoListado <= 560 ? 2 : 4);
        for (const boton of await filtros.getByRole("button").all()) {
          expect((await boton.boundingBox()).height).toBeGreaterThanOrEqual(44);
        }
        const filas = listado.getByRole("list", { name: rol === "ADMIN" ? "Reservas del administrador" : "Mis reservas" }).locator(":scope > li");
        expect(await filas.first().evaluate((elemento) => parseFloat(getComputedStyle(elemento).paddingTop))).toBeGreaterThanOrEqual(16);
        await expect(filas).toHaveCount(10);
        await listado.getByRole("button", { name: "Siguiente" }).click();
        await expect(filas).toHaveCount(2);
        await listado.getByRole("button", { name: "Anterior" }).click();
        await listado.getByLabel("Ordenar por").selectOption("ALTA_ASC");

        for (const filtro of ["Activas 12", "Finalizadas 1", "Todas 15", "Canceladas 2"]) {
          await filtros.getByRole("button", { name: filtro, exact: true }).click();
          if (rol === "ADMIN") {
            await listado.locator("summary").first().click();
            await expect(listado.locator("details").first()).toHaveAttribute("open", "");
          } else {
            await expect(listado.locator("details")).toHaveCount(0);
            await expect(listado.getByText(/Administrador de prueba|estacionamiento-institucional/)).toHaveCount(0);
            await expect(listado.getByText(reservas[0].visitante.nombre, { exact: true })).toHaveCount(0);
            await expect(filas.first().getByRole("img", { name: "Patente PPL123" })).toBeVisible();
          }
          const desborde = await listado.evaluate((elemento) => ({
            pagina: document.documentElement.scrollWidth - innerWidth,
            listado: elemento.scrollWidth - elemento.clientWidth,
          }));
          expect(desborde.pagina).toBeLessThanOrEqual(1);
          expect(desborde.listado).toBeLessThanOrEqual(1);
        }
        await expect(listado.getByText("DESHABILITADA POR ADMINISTRACIÓN", { exact: true })).toBeVisible();
        await expect(listado.getByText(rol === "ADMIN" ? "CANCELADA POR USUARIO" : "CANCELASTE ESTA RESERVA", { exact: true })).toBeVisible();
        await testInfo.attach("listado", { body: await listado.screenshot(), contentType: "image/png" });

        // También debe cargar su hoja de estilos al volver a entrar a la ruta.
        await page.reload();
        await expect(filtros).toHaveCSS("display", "grid");
        expect(errores).toEqual([]);
      });
    }
  }
}

test("USER cancela su reserva y puede consultarla en Canceladas", async ({ page, context, baseURL }) => {
  await page.setViewportSize({ width: 390, height: 1000 });
  await prepararPagina(page, context, baseURL, "dark", "USER");
  let datos = reservas.map((reserva) => ({ ...reserva }));
  await page.route("**/api/v1/reservas", (route) => route.fulfill({ json: datos }));
  await page.route("**/api/v1/reservas/*/cancelar", (route) => {
    expect(route.request().method()).toBe("POST");
    const id = new URL(route.request().url()).pathname.split("/").at(-2);
    datos = datos.map((reserva) => reserva.id === id
      ? { ...reserva, estado: "CANCELADA", motivoCancelacion: "USUARIO" } : reserva);
    return route.fulfill({ json: {} });
  });
  page.on("dialog", (dialogo) => dialogo.accept());
  await page.goto("/dashboard-user");
  const listado = page.getByRole("region", { name: "Mis reservas" });
  await expect(listado.getByRole("button", { name: "Activas 12", exact: true })).toBeVisible();
  await listado.getByLabel("Ordenar por").selectOption("ALTA_DESC");
  await listado.getByRole("button", { name: "Cancelar", exact: true }).first().click();
  await expect(listado.getByRole("button", { name: "Activas 11", exact: true })).toHaveAttribute("aria-pressed", "true");
  await expect(listado.getByLabel("Ordenar por")).toHaveValue("ALTA_DESC");
  await listado.getByRole("button", { name: "Canceladas 3", exact: true }).click();
  await expect(listado.getByText("CANCELASTE ESTA RESERVA", { exact: true })).toHaveCount(2);
  await expect(listado.getByRole("button", { name: "Cancelar", exact: true })).toHaveCount(0);
  await expect(listado.locator("details")).toHaveCount(0);
});
