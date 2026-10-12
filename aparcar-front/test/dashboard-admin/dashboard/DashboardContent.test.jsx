import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get: getMock } }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...props }) => <a href={href} {...props}>{children}</a>,
}));

const { default: DashboardContent } = await import("@/app/dashboard-admin/dashboard/DashboardContent");

const datos = (extra = {}) => ({
  fecha: "2026-10-11", actualizadoAt: "2026-10-11T14:35:00", zonaHoraria: "America/Argentina/Buenos_Aires",
  ocupacion: { total: 10, habilitadas: 8, fueraServicio: 2, ocupadas: 6, ocupadasHabilitadas: 6, ocupadasFueraServicio: 0, libres: 2, porcentaje: 75 },
  reservasHoy: { total: 17, pendientes: 4, enCurso: 6, finalizadas: 3, canceladas: 1, noPresentados: 3 },
  operacion: { ingresosHoy: 9, egresosHoy: 3, permanenciasExcedidas: 2, proximosIngresos: 2 },
  actividadPorHora: Array.from({ length: 24 }, (_, hora) => ({ hora, ingresos: hora === 10 ? 9 : 0, egresos: hora === 13 ? 3 : 0 })),
  sectores: [
    { sector: "Planta baja", total: 7, habilitadas: 6, ocupadas: 5, ocupadasHabilitadas: 5, ocupadasFueraServicio: 0, libres: 1, ingresosHoy: 7, porcentajeOcupacion: 83.3 },
    { sector: "Subsuelo", total: 3, habilitadas: 2, ocupadas: 1, ocupadasHabilitadas: 1, ocupadasFueraServicio: 0, libres: 1, ingresosHoy: 2, porcentajeOcupacion: 50 },
  ],
  proximasReservas: [{ id: "r-proxima", desde: "2026-10-11T15:00:00", hasta: "2026-10-11T17:00:00", cocheraNumero: "A-07", sector: "Planta baja" }],
  ...extra,
});

const tarjeta = (nombre) => screen.getByRole("article", { name: nombre });
const cargaCompleta = () => screen.findByRole("article", { name: "Ocupación actual" });

beforeEach(() => {
  vi.clearAllMocks();
  getMock.mockResolvedValue({ data: datos() });
});

afterEach(() => {
  vi.restoreAllMocks();
  vi.useRealTimers();
});

describe("DashboardContent", () => {
  it("carga una foto del servidor y distingue ocupación, reservas, no presentados y excesos", async () => {
    render(<DashboardContent />);
    await cargaCompleta();

    expect(getMock.mock.calls[0][0]).toBe("/api/v1/dashboard");
    expect(tarjeta("Ocupación actual")).toHaveTextContent("75%");
    expect(within(tarjeta("Reservas del día")).getByText("17", { exact: true })).toBeInTheDocument();
    expect(within(tarjeta("No presentados")).getByText("3", { exact: true })).toBeInTheDocument();
    expect(within(tarjeta("Estadías excedidas")).getByText("2", { exact: true })).toBeInTheDocument();
    for (const nombre of ["Actividad del día", "Sectores más utilizados", "Estado de las reservas", "Próximas llegadas"]) {
      expect(screen.getByRole("heading", { name: nombre })).toBeInTheDocument();
    }
    expect(screen.getByText("A-07", { exact: false })).toBeInTheDocument();
    expect(within(screen.getByRole("region", { name: "Próximas llegadas" })).getByText("15:00")).toBeInTheDocument();
  });

  it("ofrece los datos horarios del gráfico en una tabla accesible", async () => {
    const user = userEvent.setup();
    render(<DashboardContent />);
    await cargaCompleta();
    await user.click(screen.getByText("Ver detalle por hora"));

    const tabla = screen.getByRole("table");
    expect(within(tabla).getByRole("columnheader", { name: /ingresos/i })).toBeInTheDocument();
    expect(within(tabla).getByRole("columnheader", { name: /egresos/i })).toBeInTheDocument();
    expect(within(tabla).getAllByRole("row")).toHaveLength(25);
    const fila = within(tabla).getByRole("row", { name: /10:00/ });
    expect(within(fila).getByRole("cell", { name: "9", exact: true })).toBeInTheDocument();
  });

  it("no presenta ceros ficticios mientras espera la primera respuesta", async () => {
    getMock.mockReturnValue(new Promise(() => {}));
    render(<DashboardContent />);

    expect(screen.getByText(/Cargando dashboard/)).toBeInTheDocument();
    expect(screen.queryByRole("article", { name: "Ocupación actual" })).not.toBeInTheDocument();
  });

  it("permite reintentar la primera carga fallida", async () => {
    const user = userEvent.setup();
    getMock.mockRejectedValueOnce(new Error("Sin conexión"));
    render(<DashboardContent />);

    expect(await screen.findByRole("alert")).toHaveTextContent("No se pudo cargar el dashboard.");
    expect(screen.queryByRole("article", { name: "Ocupación actual" })).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Reintentar" }));
    await cargaCompleta();
    expect(getMock).toHaveBeenCalledTimes(2);
  });

  it("conserva los últimos valores y avisa cuando falla una actualización", async () => {
    const user = userEvent.setup();
    render(<DashboardContent />);
    await cargaCompleta();
    getMock.mockRejectedValueOnce(new Error("Sin conexión"));
    await user.click(screen.getByRole("button", { name: "Actualizar", exact: true }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Mostramos los últimos datos disponibles.");
    expect(within(tarjeta("Reservas del día")).getByText("17", { exact: true })).toBeInTheDocument();
    getMock.mockResolvedValueOnce({ data: datos({ reservasHoy: { total: 18, pendientes: 5, enCurso: 6, finalizadas: 3, canceladas: 1, noPresentados: 3 } }) });
    await user.click(screen.getByRole("button", { name: "Actualizar", exact: true }));
    await waitFor(() => expect(within(tarjeta("Reservas del día")).getByText("18", { exact: true })).toBeInTheDocument());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("impide solicitudes repetidas mientras actualiza", async () => {
    const user = userEvent.setup();
    render(<DashboardContent />);
    await cargaCompleta();
    let resolver;
    getMock.mockReturnValueOnce(new Promise((resolve) => { resolver = resolve; }));
    await user.click(screen.getByRole("button", { name: "Actualizar", exact: true }));

    const boton = screen.getByRole("button", { name: /Actualizando/ });
    expect(boton).toBeDisabled();
    fireEvent.click(boton);
    expect(getMock).toHaveBeenCalledTimes(2);
    await act(async () => resolver({ data: datos() }));
    expect(screen.getByRole("button", { name: "Actualizar", exact: true })).toBeEnabled();
  });

  it("maneja un predio vacío sin porcentajes inválidos ni llegadas inventadas", async () => {
    getMock.mockResolvedValue({ data: datos({
      ocupacion: { total: 0, habilitadas: 0, fueraServicio: 0, ocupadas: 0, ocupadasHabilitadas: 0, ocupadasFueraServicio: 0, libres: 0, porcentaje: 0 },
      reservasHoy: { total: 0, pendientes: 0, enCurso: 0, finalizadas: 0, canceladas: 0, noPresentados: 0 },
      operacion: { ingresosHoy: 0, egresosHoy: 0, permanenciasExcedidas: 0, proximosIngresos: 0 },
      sectores: [], proximasReservas: [],
      actividadPorHora: Array.from({ length: 24 }, (_, hora) => ({ hora, ingresos: 0, egresos: 0 })),
    }) });
    const { container } = render(<DashboardContent />);
    await cargaCompleta();

    expect(within(tarjeta("Reservas del día")).getByText("0", { exact: true, selector: "p" })).toBeInTheDocument();
    expect(container).not.toHaveTextContent(/NaN|Infinity|undefined/);
    expect(screen.queryByText("A-07", { exact: false })).not.toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(screen.getByText("Aún no hay sectores para mostrar.")).toBeInTheDocument();
    expect(screen.getByText("Sin llegadas previstas en las próximas 2 horas.")).toBeInTheDocument();
  });

  it("advierte sobre vehículos en cocheras fuera de servicio sin ocultarlos", async () => {
    getMock.mockResolvedValue({ data: datos({
      ocupacion: { total: 10, habilitadas: 8, fueraServicio: 2, ocupadas: 7, ocupadasHabilitadas: 6, ocupadasFueraServicio: 1, libres: 2, porcentaje: 75 },
    }) });
    render(<DashboardContent />);
    await cargaCompleta();

    expect(screen.getByText(/fuera de servicio.*(vehículo|ocupad)|(vehículo|ocupad).*fuera de servicio/i)).toBeInTheDocument();
    expect(tarjeta("Ocupación actual")).toHaveTextContent("75%");
  });

  it("actualiza cada minuto solamente cuando la página está visible y limpia el temporizador", async () => {
    vi.useFakeTimers();
    const visibilidad = vi.spyOn(document, "visibilityState", "get").mockReturnValue("visible");
    const { unmount } = render(<DashboardContent />);
    await act(async () => {});
    expect(getMock).toHaveBeenCalledTimes(1);

    await act(async () => vi.advanceTimersByTimeAsync(60_000));
    expect(getMock).toHaveBeenCalledTimes(2);
    visibilidad.mockReturnValue("hidden");
    await act(async () => vi.advanceTimersByTimeAsync(120_000));
    expect(getMock).toHaveBeenCalledTimes(2);
    visibilidad.mockReturnValue("visible");
    await act(async () => document.dispatchEvent(new Event("visibilitychange")));
    expect(getMock).toHaveBeenCalledTimes(3);

    unmount();
    await act(async () => vi.advanceTimersByTimeAsync(60_000));
    expect(getMock).toHaveBeenCalledTimes(3);
  });

  it("aborta la consulta pendiente al salir de la pantalla", () => {
    getMock.mockReturnValue(new Promise(() => {}));
    const { unmount } = render(<DashboardContent />);
    const { signal } = getMock.mock.calls[0][1];
    expect(signal.aborted).toBe(false);

    unmount();

    expect(signal.aborted).toBe(true);
  });
});
