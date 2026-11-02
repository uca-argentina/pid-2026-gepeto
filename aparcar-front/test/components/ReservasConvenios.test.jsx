import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get, post } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
import ReservasContent from "@/components/ReservasContent";
import ReservasListado from "@/components/ReservasListado";

const catalogo = [
  { id: "uca", nombre: "UCA", formatoCredencial: "UCA-XXX", descuentoPct: 15 },
  { id: "nacion", nombre: "Club Nación", formatoCredencial: "NACION-XXX", descuentoPct: 10 },
];
const cotizar = (body) => {
  const mejor = catalogo.filter((convenio) => body.credencialesConvenio?.some((credencial) => credencial.convenioId === convenio.id))
    .sort((a, b) => b.descuentoPct - a.descuentoPct)[0];
  const puntos = Number(body.puntos ?? 0);
  const usaConvenio = mejor && mejor.descuentoPct >= puntos / 10;
  const puntosAplicados = usaConvenio ? 0 : puntos;
  return { tipo: "AUTO", subtotal: 1000, total: 1000 - (usaConvenio ? mejor.descuentoPct * 10 : puntos),
    puntosAplicados, descuentoPct: puntosAplicados / 10, descuento: puntosAplicados,
    saldoDisponible: 500, maxPuntosCanjeables: 500,
    convenio: usaConvenio ? { convenioId: mejor.id, nombre: mejor.nombre, descuentoPct: mejor.descuentoPct, descuentoMonto: mejor.descuentoPct * 10 } : null };
};

beforeEach(() => {
  vi.resetAllMocks();
  get.mockImplementation((url, config) => {
    if (url.endsWith("/convenios")) return Promise.resolve({ data: catalogo });
    if (url.endsWith("/credenciales")) return Promise.resolve({ data: [] });
    if (url.endsWith("/cotizacion")) return Promise.resolve({ data: cotizar(config.params) });
    if (url.endsWith("/vehiculos")) return Promise.resolve({ data: [{ id: "v1", patente: "ABC123", tipo: "AUTO", visitanteId: "u1" }] });
    if (url.endsWith("/visitantes")) return Promise.resolve({ data: [{ id: "u1", nombre: "Juan" }] });
    if (url.endsWith("/disponibles")) return Promise.resolve({ data: [{ id: "c1", tipo: "AUTO", numero: "A-01", sector: "PB" }] });
    return Promise.resolve({ data: [] });
  });
  post.mockImplementation((url, body) => Promise.resolve({ data: url.endsWith("/cotizacion") ? cotizar(body)
    : url.endsWith("/verificar") ? body : {} }));
});

it.each(["user", "admin"])("%s valida los convenios y envía el mayor descuento cotizado", async (modo) => {
  const user = userEvent.setup();
  render(<ReservasContent modo={modo} />);
  if (modo === "admin") await user.type(await screen.findByLabelText("Patente"), "ABC123");
  else {
    await screen.findByRole("option", { name: /ABC123/ });
    await user.selectOptions(screen.getByLabelText("Patente"), "ABC123");
  }
  await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
  for (const [nombre, credencial] of [["UCA", "UCA-123"], ["Club Nación", "NACION-123"]]) {
    const input = await screen.findByLabelText(`Credencial ${nombre}`);
    await user.type(input, credencial);
    await user.click(within(input.parentElement).getByRole("button", { name: "Verificar" }));
  }
  expect(await screen.findByText("Convenio UCA (15 %)")).toBeInTheDocument();
  expect(screen.getByText(/\$\s*850,00/)).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Confirmar reserva" }));
  await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/reservas", expect.objectContaining({
    precioEsperado: 850,
    credencialesConvenio: [{ convenioId: "uca", credencial: "UCA-123" }, { convenioId: "nacion", credencial: "NACION-123" }],
    ...(modo === "admin" ? { visitanteId: "u1" } : {}),
  })));
});

it("compara los puntos elegidos con el convenio sin sumar los descuentos", async () => {
  const user = userEvent.setup();
  render(<ReservasContent />);
  await screen.findByRole("option", { name: /ABC123/ });
  await user.selectOptions(screen.getByLabelText("Patente"), "ABC123");
  await user.type(await screen.findByLabelText("Credencial UCA"), "UCA-123");
  await user.click(screen.getAllByRole("button", { name: "Verificar" })[0]);
  await user.type(await screen.findByLabelText("Puntos a usar"), "100");
  expect(await screen.findByText(/Conservás tus puntos/)).toBeInTheDocument();
  expect(screen.getByText(/\$\s*850,00/)).toBeInTheDocument();
  await user.clear(screen.getByLabelText("Puntos a usar"));
  await user.type(screen.getByLabelText("Puntos a usar"), "200");
  expect(await screen.findByText("Descuento por 200 puntos (20 %)")).toBeInTheDocument();
  expect(screen.getByText(/\$\s*800,00/)).toBeInTheDocument();
  expect(screen.queryByText("Convenio UCA (15 %)")).not.toBeInTheDocument();
});

it.each(["admin", "user"])("%s muestra el convenio histórico sin depender del catálogo", (modo) => {
  render(<ReservasListado modo={modo} reservas={[{
    id: "r1", estado: "CONFIRMADA", precioTotal: 1000, precioFinal: 850,
    convenio: { convenioId: "borrado", nombre: "UCA histórico", descuentoPct: 15, descuentoMonto: 150 },
  }]} onCancelar={vi.fn()} />);
  expect(screen.getByText(/Total:.*850,00/)).toBeInTheDocument();
  expect(screen.getByText(/Convenio UCA histórico: 15 % de descuento/)).toBeInTheDocument();
  expect(get).not.toHaveBeenCalled();
});

it.each(["/convenios", "/credenciales"])("con %s caído cotiza para el visitante seleccionado y conserva sus descuentos guardados", async (endpoint) => {
  const implementarGet = get.getMockImplementation();
  get.mockImplementation((url, config) => url.endsWith(endpoint) ? Promise.reject(new Error("Sin conexión"))
    : implementarGet(url, config));
  post.mockImplementation((url, body) => Promise.resolve({ data: url.endsWith("/cotizacion")
    ? { ...cotizar({ ...body, credencialesConvenio: [{ convenioId: "uca", credencial: "UCA-123" }] }) }
    : {} }));
  const user = userEvent.setup();
  render(<ReservasContent modo="admin" />);
  await user.type(await screen.findByLabelText("Patente"), "ABC123");
  await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
  expect(await screen.findByRole("button", { name: "Reintentar convenios" })).toBeVisible();
  await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/tarifas/cotizacion", expect.objectContaining({
    visitanteId: "u1", credencialesConvenio: [],
  })));
  expect(get.mock.calls.some(([url]) => url.endsWith("/cotizacion"))).toBe(false);
  expect(await screen.findByText("Convenio UCA (15 %)")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Confirmar reserva" }));
  await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/reservas", expect.objectContaining({
    visitanteId: "u1", precioEsperado: 850,
  })));
});
