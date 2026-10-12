import { test, expect } from "@playwright/test";

test("Mi cuenta se hidrata al recargar usuarios y conserva el vínculo accesible del menú", async ({ page, context, baseURL }) => {
  const token = [
    Buffer.from("{}").toString("base64url"),
    Buffer.from(JSON.stringify({ sub: "cuenta-ui@test.invalid", authorities: "ADMIN", exp: 4102444800 })).toString("base64url"),
    "prueba-ui",
  ].join(".");
  await context.addCookies([{ name: "JWT", value: token, url: baseURL }]);
  await page.addInitScript((url) => { window.__ENV = { NEXT_PUBLIC_API_BASE_URL: url }; }, baseURL);
  // La navegación real de Next se mantiene; sólo aislamos las consultas al backend.
  await page.route("**/api/v1/**", (route) => route.fulfill({ json: [] }));
  const errores = [];
  page.on("console", (message) => {
    if (message.type() === "error" && /hydrat|server rendered HTML/i.test(message.text())) errores.push(message.text());
  });
  page.on("pageerror", (error) => errores.push(error.message));

  await page.goto("/dashboard-admin/usuarios");
  for (let carga = 0; carga < 2; carga += 1) {
    await expect(page.getByText("No hay usuarios registrados.")).toBeVisible();
    const trigger = page.getByRole("button", { name: "Mi cuenta", exact: true });
    await expect(trigger).toHaveAttribute("aria-expanded", "false");
    await expect(trigger).not.toHaveAttribute("aria-controls");
    await trigger.click();
    await expect(trigger).toHaveAttribute("aria-expanded", "true");
    const id = await trigger.getAttribute("aria-controls");
    expect(id).toBeTruthy();
    const menu = page.locator(".account-dropdown");
    await expect(menu).toHaveAttribute("id", id);
    await expect(menu.getByRole("link", { name: /Mis datos/ })).toHaveAttribute("href", "/dashboard-admin/perfil");
    await page.keyboard.press("Escape");
    await expect(trigger).toBeFocused();
    await expect(trigger).not.toHaveAttribute("aria-controls");
    await expect(menu).toHaveCount(0);
    if (carga === 0) await page.reload();
  }
  expect(errores).toEqual([]);
});
