import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
const { get, post, error } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), error: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get, post } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error } }));
import ReservasContent from "@/components/ReservasContent";
import VisitantesContent from "@/app/dashboard-admin/VisitantesContent";

let cotizar;
let historial;
beforeEach(() => {
  vi.resetAllMocks();
  historial = [];
  cotizar = (params) => Promise.resolve({ data: { tipo: params.tipo, total: params.tipo === "ACCESIBLE" ? 800 : 1000, horas: 1 } });
  get.mockImplementation((url, config) => {
    if (url.endsWith("/cotizacion")) return cotizar(config.params);
    if (url.endsWith("/vehiculos")) return Promise.resolve({ data: [{ id: "v1", patente: "ABC123", tipo: "AUTO", visitanteId: "u1" }] });
    if (url.endsWith("/visitantes")) return Promise.resolve({ data: [{ id: "u1", nombre: "Juan", tieneDiscapacidad: true }] });
    if (url.endsWith("/disponibles")) return Promise.resolve({ data: [
      { id: "c1", tipo: "AUTO", numero: "A-01", sector: "PB" },
      { id: "c2", tipo: "ACCESIBLE", numero: "AC-01", sector: "PB" },
    ] });
    if (url.endsWith("/reservas")) return Promise.resolve({ data: historial });
    return Promise.reject(new Error(url));
  });
  post.mockResolvedValue({ data: {} });
});

async function elegirVehiculo(modo, user) {
  if (modo === "admin") await user.type(await screen.findByLabelText("Patente"), "ABC123");
  else {
    await screen.findByRole("option", { name: /ABC123/ });
    await user.selectOptions(screen.getByLabelText("Patente"), "ABC123");
  }
}

it.each(["admin", "user"])("%s muestra precio antes de elegir cochera y envía el importe cotizado", async (modo) => {
  const user = userEvent.setup();
  render(<ReservasContent modo={modo} />);
  await elegirVehiculo(modo, user);
  expect(await screen.findByText(/\$\s*1\.000,00/)).toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Confirmar reserva" })).toBeDisabled();
  await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
  await user.click(screen.getByRole("button", { name: "Confirmar reserva" }));
  await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/reservas", expect.objectContaining({ precioEsperado: 1000 })));
});

it.each(["admin", "user"])("%s cambia a tarifa Accesible al elegir esa cochera", async (modo) => {
  const user = userEvent.setup();
  render(<ReservasContent modo={modo} />);
  await elegirVehiculo(modo, user);
  await screen.findByRole("option", { name: /AC-01/ });
  await user.selectOptions(screen.getByLabelText("Cochera"), "c2");
  expect(await screen.findByText(/\$\s*800,00/)).toBeInTheDocument();
  expect(screen.getByText(/Tarifa Accesible/)).toBeInTheDocument();
});

it("bloquea la confirmación hasta recibir la cotización", async () => {
  let resolver;
  cotizar = () => new Promise((resolve) => { resolver = resolve; });
  const user = userEvent.setup();
  render(<ReservasContent modo="user" />);
  await elegirVehiculo("user", user);
  await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
  expect(screen.getByRole("button", { name: "Confirmar reserva" })).toBeDisabled();
  expect(screen.getByText("Calculando precio…")).toBeInTheDocument();
  await act(async () => resolver({ data: { total: 1000, tipo: "AUTO" } }));
  expect(screen.getByRole("button", { name: "Confirmar reserva" })).toBeEnabled();
});

it("si cambia el precio al confirmar, lo actualiza sin reenviar la reserva", async () => {
  const user = userEvent.setup();
  post.mockRejectedValue({ response: { data: { message: "El precio cambió." } } });
  render(<ReservasContent modo="user" />);
  await elegirVehiculo("user", user);
  await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
  cotizar = () => Promise.resolve({ data: { total: 2000, tipo: "AUTO" } });
  await user.click(screen.getByRole("button", { name: "Confirmar reserva" }));
  expect(await screen.findByText(/\$\s*2\.000,00/)).toBeInTheDocument();
  expect(error).toHaveBeenCalledWith("El precio cambió.");
  expect(post).toHaveBeenCalledTimes(1);
});

it("muestra el importe histórico y distingue las reservas anteriores a las tarifas", async () => {
  historial = [100, null].map((precioTotal, i) => ({ id: `r${i}`, precioTotal, estado: "FINALIZADA", vehiculo: { patente: "ABC123" } }));
  render(<ReservasContent modo="user" />);
  expect(await screen.findByText(/Total:.*100,00/)).toBeInTheDocument();
  expect(screen.getByText("Sin importe registrado")).toBeInTheDocument();
});

it("el alta operativa también muestra y envía el precio Accesible", async () => {
  const user = userEvent.setup();
  render(<VisitantesContent />);
  expect(await screen.findByText(/\$\s*1\.000,00/)).toBeInTheDocument();
  for (const [label, value] of [["Nombre", "Juan"], ["Documento", "12345678"], ["Email", "juan@test.com"], ["Patente", "ABC123"]]) {
    fireEvent.change(screen.getByLabelText(label), { target: { value } });
  }
  await user.click(screen.getByLabelText(/Persona con discapacidad/));
  await user.selectOptions(screen.getByLabelText("Cochera"), "c2");
  expect(await screen.findByText(/\$\s*800,00/)).toBeInTheDocument();
  await user.click(screen.getByRole("button", { name: "Dar de alta y reservar" }));
  await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/visitantes/alta", expect.objectContaining({ precioEsperado: 800, cocheraId: "c2" })));
});

it("sin tarifas configuradas no permite crear la reserva", async () => {
  cotizar = () => Promise.reject({ response: { data: { message: "Todavía no hay tarifas configuradas" } } });
  const user = userEvent.setup();
  render(<ReservasContent modo="user" />);
  await elegirVehiculo("user", user);
  await user.selectOptions(screen.getByLabelText("Cochera"), "c1");
  expect(await screen.findByRole("alert")).toHaveTextContent("Todavía no hay tarifas");
  expect(screen.getByRole("button", { name: "Confirmar reserva" })).toBeDisabled();
  expect(post).not.toHaveBeenCalled();
});
