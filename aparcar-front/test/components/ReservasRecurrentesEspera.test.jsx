import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { get, post, success, error } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn(), success: vi.fn(), error: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get, post } }));
vi.mock("sonner", () => ({ toast: { success, error } }));
vi.mock("@/components/ModalCodigoAcceso", () => ({ default: () => <div>Código de acceso confirmado</div> }));

import ReservasContent from "@/components/ReservasContent";
import VisitantesContent from "@/app/dashboard-admin/VisitantesContent";
import ReservasListado from "@/components/ReservasListado";
import NotificacionesReservas from "@/components/NotificacionesReservas";
import { contarRepeticiones, limiteRepeticion } from "@/components/RepeticionReserva";

const vehiculo = { id: "v1", visitanteId: "p1", patente: "ABC123", tipo: "AUTO" };
const cochera = { id: "c1", numero: "A-01", sector: "PB", tipo: "AUTO" };
const confirmada = { id: "r1", estado: "CONFIRMADA", vehiculo, cochera,
  desde: "2026-10-12T08:00", hasta: "2026-10-12T10:00", acceso: { codigo: "1234567890" } };
let disponibles;

beforeEach(() => {
  vi.clearAllMocks();
  disponibles = [];
  get.mockImplementation(async (url) => {
    if (["/api/v1/vehiculos", "/api/v1/vehiculos/mios"].includes(url)) return { data: [vehiculo] };
    if (url === "/api/v1/visitantes") return { data: [{ id: "p1", nombre: "Visitante", tieneDiscapacidad: false }] };
    if (url === "/api/v1/cocheras/disponibles") return { data: disponibles };
    if (url === "/api/v1/tarifas/cotizacion") return { data: { total: 1000, tipo: "AUTO" } };
    return { data: [] };
  });
  post.mockResolvedValue({ data: { id: "r1", estado: "LISTA_ESPERA" } });
});

async function elegirVehiculo(modo = "user") {
  if (modo !== "admin") await screen.findByRole("option", { name: /ABC123/ });
  else await waitFor(() => expect(document.querySelector('datalist option[value="ABC123"]')).toBeInTheDocument());
  fireEvent.change(screen.getByLabelText("Patente"), { target: { value: "ABC123" } });
  await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/cocheras/disponibles", expect.anything()));
}

describe("Lista de espera", () => {
  it.each(["user", "admin"])("%s requiere consentimiento y envía espera sin cochera ni QR", async (modo) => {
    render(<ReservasContent modo={modo} />);
    await elegirVehiculo(modo);
    const aceptar = await screen.findByLabelText("Acepto ingresar a la lista de espera");
    expect(screen.getByRole("button", { name: "Confirmar reserva" })).toBeDisabled();
    expect(post).not.toHaveBeenCalled();
    await userEvent.click(aceptar);
    await userEvent.click(screen.getByRole("button", { name: "Ingresar a lista de espera" }));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/reservas", expect.objectContaining({
      cocheraId: null, listaEspera: true, tipoCochera: "AUTO", vehiculoId: "v1", precioEsperado: 1000,
      visitanteId: modo === "admin" ? "p1" : undefined,
    })));
    expect(success).toHaveBeenCalledWith("Reserva agregada a la lista de espera");
    expect(screen.queryByText("Código de acceso confirmado")).not.toBeInTheDocument();
  });

  it("no confunde carga o error de disponibilidad con falta de lugares y permite reintentar", async () => {
    let rechazar;
    const normal = get.getMockImplementation();
    get.mockImplementation((url, config) => url === "/api/v1/cocheras/disponibles"
      ? new Promise((_, reject) => { rechazar = reject; }) : normal(url, config));
    render(<ReservasContent />);
    await elegirVehiculo();
    expect(screen.getByText("Consultando disponibilidad…")).toBeInTheDocument();
    expect(screen.queryByLabelText("Acepto ingresar a la lista de espera")).not.toBeInTheDocument();
    await act(async () => rechazar(new Error("Sin conexión")));
    expect(screen.queryByLabelText("Acepto ingresar a la lista de espera")).not.toBeInTheDocument();
    get.mockImplementation(normal);
    await userEvent.click(screen.getByRole("button", { name: "Reintentar disponibilidad" }));
    expect(await screen.findByLabelText("Acepto ingresar a la lista de espera")).not.toBeChecked();
  });

  it("invalida el consentimiento cuando cambia la franja", async () => {
    render(<ReservasContent />);
    await elegirVehiculo();
    await userEvent.click(await screen.findByLabelText("Acepto ingresar a la lista de espera"));
    fireEvent.change(screen.getByLabelText("Hasta"), { target: { value: "2099-01-01T12:00" } });
    expect(await screen.findByLabelText("Acepto ingresar a la lista de espera")).not.toBeChecked();
    expect(screen.getByRole("button", { name: "Confirmar reserva" })).toBeDisabled();
  });

  it("una respuesta vacía de la franja anterior no habilita espera en la franja actual", async () => {
    let resolverAnterior;
    let consultas = 0;
    const normal = get.getMockImplementation();
    get.mockImplementation((url, config) => {
      if (url !== "/api/v1/cocheras/disponibles") return normal(url, config);
      consultas += 1;
      return consultas === 1 ? new Promise((resolve) => { resolverAnterior = resolve; }) : Promise.resolve({ data: [cochera] });
    });
    render(<ReservasContent />);
    await elegirVehiculo();
    fireEvent.change(screen.getByLabelText("Hasta"), { target: { value: "2099-01-01T12:00" } });
    await screen.findByRole("option", { name: /A-01/ });
    await act(async () => resolverAnterior({ data: [] }));
    expect(screen.queryByLabelText("Acepto ingresar a la lista de espera")).not.toBeInTheDocument();
    expect(screen.getByRole("option", { name: /A-01/ })).toBeInTheDocument();
  });

  it("si apareció un lugar al enviar, muestra confirmación y el código recibido", async () => {
    post.mockResolvedValue({ data: confirmada });
    render(<ReservasContent />);
    await elegirVehiculo();
    await userEvent.click(await screen.findByLabelText("Acepto ingresar a la lista de espera"));
    await userEvent.click(screen.getByRole("button", { name: "Ingresar a lista de espera" }));
    expect(await screen.findByText("Código de acceso confirmado")).toBeInTheDocument();
    expect(success).toHaveBeenCalledWith("Reserva creada correctamente");
  });

  it("un conflicto al confirmar recarga disponibilidad y ofrece espera sin aceptarla automáticamente", async () => {
    disponibles = [cochera];
    post.mockImplementation(async () => { disponibles = []; throw { response: { data: { message: "Cochera ocupada" } } }; });
    render(<ReservasContent />);
    await elegirVehiculo();
    await screen.findByRole("option", { name: /A-01/ });
    await userEvent.selectOptions(screen.getByLabelText("Cochera"), "c1");
    await userEvent.click(screen.getByRole("button", { name: "Confirmar reserva" }));
    expect(await screen.findByLabelText("Acepto ingresar a la lista de espera")).not.toBeChecked();
    expect(error).toHaveBeenCalledWith("Cochera ocupada");
    expect(post).toHaveBeenCalledTimes(1);
  });

  it("el alta operativa crea al visitante y su solicitud en una sola llamada", async () => {
    post.mockResolvedValue({ data: { reserva: { estado: "LISTA_ESPERA" } } });
    render(<VisitantesContent />);
    fireEvent.change(screen.getByLabelText("Nombre"), { target: { value: "Juan Pérez" } });
    fireEvent.change(screen.getByLabelText("Documento"), { target: { value: "30111222" } });
    fireEvent.change(screen.getByLabelText("Email"), { target: { value: "juan@test.com" } });
    fireEvent.change(screen.getByLabelText("Patente"), { target: { value: "ABC123" } });
    await userEvent.click(await screen.findByLabelText("Acepto ingresar a la lista de espera"));
    await userEvent.click(screen.getByRole("button", { name: "Dar de alta e ingresar a lista de espera" }));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/visitantes/alta", expect.objectContaining({
      nombre: "Juan Pérez", listaEspera: true, cocheraId: null, tipoCochera: "AUTO",
    })));
    expect(success).toHaveBeenCalledWith(expect.stringContaining("reserva en lista de espera"));
  });

  it("muestra espera separada de activas, permite cancelar y nunca ofrece QR", async () => {
    const cancelar = vi.fn();
    render(<ReservasListado reservas={[{ ...confirmada, estado: "LISTA_ESPERA", cochera: null }]}
      onCancelar={cancelar} onVerCodigo={vi.fn()} />);
    await userEvent.click(screen.getByRole("button", { name: "En espera 1" }));
    expect(screen.getByText("Pendiente de asignación")).toBeInTheDocument();
    expect(screen.getByText("EN LISTA DE ESPERA")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Ver QR" })).not.toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    expect(cancelar).toHaveBeenCalledWith(expect.objectContaining({ estado: "LISTA_ESPERA" }));
  });
});

describe("Reservas recurrentes", () => {
  it.each(["user", "admin"])("las nuevas reservas de %s no incluyen controles de recurrencia", async (modo) => {
    render(<ReservasContent modo={modo} />);
    await elegirVehiculo(modo);
    expect(screen.queryByLabelText("Repetir semanalmente")).not.toBeInTheDocument();
    expect(screen.queryByRole("group", { name: "Días de la semana" })).not.toBeInTheDocument();
  });

  it("el admin consulta sólo sus vehículos y series, y confirma una serie semanal a su nombre", async () => {
    disponibles = [cochera];
    post.mockResolvedValue({ data: [{ ...confirmada, recurrenciaId: "serie" }, { ...confirmada, id: "r2", recurrenciaId: "serie" }] });
    render(<ReservasContent modo="recurrentes" />);
    await elegirVehiculo("recurrentes");
    fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "2026-10-12T08:00" } });
    fireEvent.change(screen.getByLabelText("Hasta"), { target: { value: "2026-10-12T10:00" } });
    await screen.findByRole("option", { name: /A-01/ });
    await userEvent.selectOptions(screen.getByLabelText("Cochera"), "c1");
    await userEvent.click(screen.getByLabelText("Lunes"));
    expect(screen.getByLabelText("Lunes")).toBeChecked();
    expect(screen.getByLabelText("Cochera")).toHaveValue("c1");
    fireEvent.change(screen.getByLabelText("Repetir hasta (inclusive)"), { target: { value: "2026-10-19" } });
    expect(screen.getByText(/2 reservas\. El importe/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Confirmar reservas recurrentes" }));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/reservas/recurrentes", expect.objectContaining({
      cocheraId: "c1", diasSemana: [1], repetirHasta: "2026-10-19", precioEsperado: 1000,
    })));
    expect(post).toHaveBeenCalledTimes(1);
    expect(post.mock.calls[0][1].visitanteId).toBeUndefined();
    expect(get).toHaveBeenCalledWith("/api/v1/vehiculos/mios");
    expect(get).toHaveBeenCalledWith("/api/v1/reservas/recurrentes");
    expect(get).toHaveBeenCalledWith("/api/v1/cocheras/disponibles", expect.objectContaining({
      params: expect.objectContaining({ propia: true }),
    }));
    expect(get).not.toHaveBeenCalledWith("/api/v1/visitantes");
    expect(get).not.toHaveBeenCalledWith("/api/v1/vehiculos");
    expect(get).not.toHaveBeenCalledWith("/api/v1/reservas");
    expect(success).toHaveBeenCalledWith("2 reservas recurrentes creadas correctamente");
  });

  it("rechaza períodos vacíos, más de 90 ocurrencias y días que omiten la fecha inicial", async () => {
    disponibles = [cochera];
    render(<ReservasContent modo="recurrentes" />);
    await elegirVehiculo("recurrentes");
    fireEvent.change(screen.getByLabelText("Desde"), { target: { value: "2026-10-12T08:00" } });
    fireEvent.change(screen.getByLabelText("Hasta"), { target: { value: "2026-10-12T10:00" } });
    await userEvent.click(screen.getByLabelText("Lunes"));
    await screen.findByRole("option", { name: /A-01/ });
    await userEvent.selectOptions(screen.getByLabelText("Cochera"), "c1");
    const enviar = screen.getByRole("button", { name: "Confirmar reservas recurrentes" });
    expect(enviar).toBeDisabled();
    fireEvent.change(screen.getByLabelText("Repetir hasta (inclusive)"), { target: { value: "2027-10-12" } });
    await userEvent.click(screen.getByLabelText("Martes"));
    expect(screen.getByText(/reducí los días/)).toBeInTheDocument();
    expect(enviar).toBeDisabled();
    await userEvent.click(screen.getByLabelText("Lunes"));
    expect(screen.getByText(/deben incluir el día/)).toBeInTheDocument();
    expect(enviar).toBeDisabled();
    expect(post).not.toHaveBeenCalled();
  });

  it("no ofrece lista de espera para series sin disponibilidad", async () => {
    render(<ReservasContent modo="recurrentes" />);
    await elegirVehiculo("recurrentes");
    await screen.findByText("No hay cocheras libres durante toda esa franja.");
    expect(screen.queryByLabelText("Acepto ingresar a la lista de espera")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Confirmar reservas recurrentes" })).toBeDisabled();
  });

  it("permite ver el QR y cancelar una reserva recurrente propia", async () => {
    const normal = get.getMockImplementation();
    get.mockImplementation((url, config) => url === "/api/v1/reservas/recurrentes"
      ? Promise.resolve({ data: [{ ...confirmada, recurrenciaId: "serie" }] }) : normal(url, config));
    const confirmar = vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<ReservasContent modo="recurrentes" />);
    await userEvent.click(await screen.findByRole("button", { name: "Ver QR" }));
    expect(screen.getByText("Código de acceso confirmado")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Cancelar" }));
    await waitFor(() => expect(post).toHaveBeenCalledWith("/api/v1/reservas/r1/cancelar"));
    confirmar.mockRestore();
  });

  it("cuenta fechas inclusivas y conserva el límite anual en años bisiestos", () => {
    expect(contarRepeticiones("2026-10-12T08:00", "2026-10-19", [1])).toBe(2);
    expect(contarRepeticiones("2026-10-12T08:00", "2026-10-12", [2])).toBe(0);
    expect(limiteRepeticion("2028-02-29T08:00")).toBe("2029-02-28");
  });
});

describe("Notificaciones persistentes", () => {
  it("una promoción histórica conserva el aviso con el estado actual y sin QR si fue cancelada", async () => {
    get.mockResolvedValue({ data: [{ ...confirmada, estado: "CANCELADA", motivoCancelacion: "ADMINISTRACION" }] });
    render(<NotificacionesReservas onVerCodigo={vi.fn()} />);
    expect(await screen.findByText("CANCELADA POR ADMINISTRACIÓN")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Ver código de acceso" })).not.toBeInTheDocument();
  });

  it("carga promociones pendientes al entrar y las descarta sólo después de persistir la lectura", async () => {
    get.mockResolvedValue({ data: [confirmada] });
    post.mockRejectedValueOnce(new Error("Sin conexión")).mockResolvedValueOnce({ data: confirmada });
    const actualizar = vi.fn();
    render(<NotificacionesReservas onActualizadas={actualizar} onVerCodigo={vi.fn()} />);
    const notificaciones = await screen.findByRole("region", { name: "Notificaciones de reservas" });
    expect(within(notificaciones).getByText(/Se confirmó/)).toBeInTheDocument();
    expect(actualizar).toHaveBeenCalledTimes(1);
    await userEvent.click(screen.getByRole("button", { name: "Marcar como leída" }));
    expect(error).toHaveBeenCalledWith(expect.stringContaining("No se pudo marcar"));
    expect(notificaciones).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Marcar como leída" }));
    await waitFor(() => expect(screen.queryByRole("region", { name: "Notificaciones de reservas" })).not.toBeInTheDocument());
    expect(post).toHaveBeenCalledWith("/api/v1/reservas/r1/notificacion/leida");
    fireEvent(window, new Event("focus"));
    await waitFor(() => expect(get).toHaveBeenCalledTimes(2));
    expect(screen.queryByRole("region", { name: "Notificaciones de reservas" })).not.toBeInTheDocument();
  });
});
