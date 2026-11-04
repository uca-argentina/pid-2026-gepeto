import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get, post } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
import ReservasContent from "@/components/ReservasContent";
import ReservasListado from "@/components/ReservasListado";

/**
 * "Usar puntos" en el alta de una reserva. El servidor hace las cuentas: el
 * mock imita la cotizacion (100 puntos = 10 %) para ver que se pide y se
 * muestra lo que corresponde.
 */
let saldo;
let puntosAGanar;
let historial;

const cotizar = ({ puntos, usarTodos }) => {
  const maximo = Math.min(saldo, 500);
  const aplicados = usarTodos ? maximo : puntos ? Number(puntos) : 0;
  const descuento = 1000 * aplicados / 1000;
  return Promise.resolve({ data: {
    tipo: "AUTO", total: 1000 - descuento, horas: 1, subtotal: 1000, descuentoPct: aplicados / 10, descuento,
    puntosAplicados: aplicados, saldoDisponible: saldo, maxPuntosCanjeables: maximo, puntosAGanar,
    motivoSinPuntos: puntosAGanar ? null : "La reserva dura menos de 60 minutos: no suma puntos.",
  } });
};

beforeEach(() => {
  vi.resetAllMocks();
  saldo = 300;
  puntosAGanar = 20;
  historial = [];
  get.mockImplementation((url, config) => {
    if (url.endsWith("/cotizacion")) return cotizar(config.params);
    if (url.endsWith("/vehiculos")) return Promise.resolve({ data: [{ id: "v1", patente: "ABC123", tipo: "AUTO", visitanteId: "u1" }] });
    if (url.endsWith("/visitantes")) return Promise.resolve({ data: [{ id: "u1", nombre: "Juan" }] });
    if (url.endsWith("/disponibles")) return Promise.resolve({ data: [{ id: "c1", tipo: "AUTO", numero: "A-01", sector: "PB" }] });
    if (url.endsWith("/reservas")) return Promise.resolve({ data: historial });
    return Promise.resolve({ data: [] });
  });
  post.mockResolvedValue({ data: {} });
});

async function prepararReserva(user, modo = "user") {
  render(<ReservasContent modo={modo} />);
  if (modo === "admin") await user.type(await screen.findByLabelText("Patente"), "ABC123");
  else {
    await screen.findByRole("option", { name: /ABC123/ });
    await user.selectOptions(screen.getByLabelText("Patente"), "ABC123");
  }
  await screen.findByRole("option", { name: /A-01/ });
  await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
}

it("muestra el saldo, aplica los puntos con el precio del servidor y los envía", async () => {
  const user = userEvent.setup();
  await prepararReserva(user);
  const caja = await screen.findByRole("group", { name: "Usar puntos" });
  expect(caja).toHaveTextContent("Tenés 300 puntos");

  await user.type(within(caja).getByLabelText("Puntos a usar"), "100");

  await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/tarifas/cotizacion",
    { params: expect.objectContaining({ puntos: "100" }) }));
  expect(await screen.findByText(/\$\s*900,00/)).toBeInTheDocument();
  expect(screen.getByText("Descuento por 100 puntos (10 %)")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Confirmar reserva" }));
  await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/reservas",
    expect.objectContaining({ puntosAUsar: 100, precioEsperado: 900 })));
});

it("'Usar todos' pide el máximo al servidor y lo envía sin cantidad", async () => {
  saldo = 800;
  const user = userEvent.setup();
  await prepararReserva(user);
  await user.click(await screen.findByRole("button", { name: "Usar todos" }));

  expect(screen.getByRole("button", { name: "Usar todos" })).toHaveAttribute("aria-pressed", "true");
  expect(screen.getByLabelText("Puntos a usar")).toHaveValue("500");
  expect(await screen.findByText("Descuento por 500 puntos (50 %)")).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Confirmar reserva" }));
  await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/reservas",
    expect.objectContaining({ usarTodosLosPuntos: true, precioEsperado: 500 })));
  expect(post.mock.calls[0][1]).not.toHaveProperty("puntosAUsar");
});

it.each([
  ["1.5", "Los puntos se usan enteros, sin decimales."],
  ["400", "No tenés puntos suficientes: tu saldo es de 300."],
  ["-3", "Indicá una cantidad de puntos mayor a cero."],
])("con %j avisa, no lo cotiza y no deja confirmar", async (texto, mensaje) => {
  const user = userEvent.setup();
  await prepararReserva(user);
  await user.type(await screen.findByLabelText("Puntos a usar"), texto);

  expect(screen.getByRole("alert")).toHaveTextContent(mensaje);
  expect(screen.getByLabelText("Puntos a usar")).toHaveAttribute("aria-invalid", "true");
  expect(screen.getByRole("button", { name: "Confirmar reserva" })).toBeDisabled();
  // Lo intermedio que sí era válido ("1", "40") se cotiza al tipear; el valor inválido, nunca.
  expect(get.mock.calls.some(([, config]) => config?.params?.puntos === texto)).toBe(false);

  await user.click(screen.getByRole("button", { name: "No usar puntos" }));
  expect(screen.getByRole("button", { name: "Confirmar reserva" })).toBeEnabled();
});

it("destaca lo que se gana en la franja que más suma", async () => {
  const user = userEvent.setup();
  await prepararReserva(user);
  const ganas = await screen.findByText(/al ingresar/);
  expect(ganas).toHaveTextContent("Ganás 20 puntos al ingresar · es la franja que más suma");
  expect(ganas).toHaveClass("is-destacado");
});

it("si no suma, dice por qué", async () => {
  puntosAGanar = 0;
  const user = userEvent.setup();
  await prepararReserva(user);
  expect(await screen.findByText("La reserva dura menos de 60 minutos: no suma puntos.")).toBeInTheDocument();
});

it("sin saldo lo dice, sin campo para cargar puntos", async () => {
  saldo = 0;
  const user = userEvent.setup();
  await prepararReserva(user);
  expect(await screen.findByText(/Todavía no tenés puntos para usar/)).toBeInTheDocument();
  expect(screen.queryByLabelText("Puntos a usar")).not.toBeInTheDocument();
});

it("el modo admin no ofrece puntos ni dice lo que se gana", async () => {
  const user = userEvent.setup();
  await prepararReserva(user, "admin");
  expect(await screen.findByText(/\$\s*1\.000,00/)).toBeInTheDocument();
  expect(screen.queryByRole("group", { name: "Usar puntos" })).not.toBeInTheDocument();
  expect(screen.queryByText(/al ingresar/)).not.toBeInTheDocument();
});

it("Mis reservas muestra el total con descuento, los puntos usados y lo que suma", () => {
  render(<ReservasListado modo="user" cargando={false} error={false} onReintentar={vi.fn()} onCancelar={vi.fn()}
    reservas={[{
      id: "r1", estado: "CONFIRMADA", desde: "2099-10-10T22:00", hasta: "2099-10-10T23:00", precioTotal: 1000,
      fechaCreacion: "2099-10-09T13:00:00Z", vehiculo: { patente: "ABC123" }, cochera: { numero: "A-01" },
      puntos: { puntosUsados: 100, descuentoPct: 10, descuentoMonto: 100, precioFinal: 900, reintegrados: false,
        puntosAcreditados: null, puntosAGanar: 20, motivoSinPuntos: null },
    }]} />);
  const item = within(screen.getByRole("list", { name: "Mis reservas" })).getByRole("listitem");
  expect(item).toHaveTextContent(/Total: \$\s*900,00 ARS/);
  expect(item).toHaveTextContent(/subtotal \$\s*1\.000,00 − \$\s*100,00/);
  expect(item).toHaveTextContent("Usaste 100 puntos: 10 % de descuento.");
  expect(item).toHaveTextContent("Suma 20 puntos al ingresar.");
});
