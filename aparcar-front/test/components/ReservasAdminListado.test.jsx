import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import ReservasAdminListado from "@/components/ReservasAdminListado";

const reserva = (id, extra = {}) => ({
  id, estado: "CONFIRMADA", desde: "2026-10-10T10:00", hasta: "2026-10-10T12:00",
  fechaCreacion: "2026-10-09T13:00:00Z", precioTotal: 2000, modalidad: "FRANJA",
  visitante: { nombre: `Visitante ${id}` }, vehiculo: { patente: "ABC123" },
  cochera: { numero: "A-01", sector: "Planta baja" }, historial: [], ...extra,
});
const props = { cargando: false, error: false, onReintentar: vi.fn(), onCancelar: vi.fn() };
const filtro = (nombre) => screen.getByRole("button", { name: new RegExp(`^${nombre} \\d+$`) });
const filas = () => within(screen.getByRole("list", { name: "Reservas del administrador" })).getAllByRole("listitem");

describe("ReservasAdminListado", () => {
  it("abre en Activas por inicio y muestra ocupante, rango con año, cochera, importe y alta", () => {
    render(<ReservasAdminListado {...props} reservas={[
      reserva("tardia", { desde: "2026-10-12T10:00" }), reserva("proxima"),
      reserva("baja", { estado: "CANCELADA" }),
    ]} />);
    expect(filtro("Activas")).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText("Ordenar por")).toHaveValue("INICIO_ASC");
    expect(filas()).toHaveLength(2);
    expect(filas()[0]).toHaveTextContent("Visitante proxima — ABC123");
    expect(filas()[0]).toHaveTextContent("10/10/2026 de 10:00 a 12:00");
    expect(filas()[0]).toHaveTextContent("A-01 · Planta baja");
    expect(filas()[0]).toHaveTextContent(/Total:.*2\.000,00 ARS/);
    expect(filas()[0]).toHaveTextContent(/Alta:.*10:00/);
    expect(screen.queryByText(/Visitante baja/)).not.toBeInTheDocument();
  });

  it("filtra finalizadas, canceladas y deshabilitadas manteniendo todos los contadores", async () => {
    const user = userEvent.setup();
    render(<ReservasAdminListado {...props} reservas={[
      reserva("1"), reserva("2", { estado: "FINALIZADA" }),
      reserva("3", { estado: "CANCELADA", motivoCancelacion: "USUARIO" }),
      reserva("4", { estado: "CANCELADA", motivoCancelacion: "DESHABILITACION" }),
    ]} />);
    expect(filtro("Activas")).toHaveTextContent("1");
    await user.click(filtro("Finalizadas"));
    expect(filas()).toHaveLength(1);
    expect(filas()[0]).toHaveTextContent("FINALIZADA");
    expect(screen.queryByRole("button", { name: "Cancelar" })).not.toBeInTheDocument();
    await user.click(filtro("Canceladas"));
    expect(filas()).toHaveLength(2);
    expect(screen.getByText("CANCELADA POR USUARIO")).toBeInTheDocument();
    expect(screen.getByText("DESHABILITADA POR ADMINISTRACIÓN")).toBeInTheDocument();
    expect(filtro("Todas")).toHaveTextContent("4");
    await user.click(filtro("Todas"));
    expect(filas()).toHaveLength(4);
  });

  it("permite ordenar por alta y conserva ese orden al filtrar y recibir datos nuevos", async () => {
    const user = userEvent.setup();
    const datos = [reserva("antigua"), reserva("nueva", { fechaCreacion: "2026-10-09T14:00:00Z" })];
    const { rerender } = render(<ReservasAdminListado {...props} reservas={datos} />);
    await user.selectOptions(screen.getByLabelText("Ordenar por"), "ALTA_DESC");
    expect(filas()[0]).toHaveTextContent("Visitante nueva");
    await user.click(filtro("Todas"));
    rerender(<ReservasAdminListado {...props} reservas={[...datos, reserva("ultima", { fechaCreacion: "2026-10-09T15:00:00Z" })]} />);
    expect(filtro("Todas")).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText("Ordenar por")).toHaveValue("ALTA_DESC");
    expect(filas()[0]).toHaveTextContent("Visitante ultima");
  });

  it("pagina de a diez, permite volver y reinicia la página al cambiar orden o filtro", async () => {
    const user = userEvent.setup();
    render(<ReservasAdminListado {...props} reservas={Array.from({ length: 21 }, (_, i) => reserva(String(i).padStart(2, "0")))} />);
    expect(filas()).toHaveLength(10);
    expect(screen.getByRole("status")).toHaveTextContent("1–10 de 21 reservas");
    expect(screen.getByRole("button", { name: "Anterior" })).toBeDisabled();
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    expect(screen.getByRole("status")).toHaveTextContent("11–20 de 21 reservas");
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    expect(filas()).toHaveLength(1);
    expect(screen.getByRole("button", { name: "Siguiente" })).toBeDisabled();
    expect(screen.getByText("Página 3 de 3")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Anterior" }));
    expect(screen.getByText("Página 2 de 3")).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Ordenar por"), "ALTA_ASC");
    expect(screen.getByText("Página 1 de 3")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    await user.click(filtro("Todas"));
    expect(screen.getByText("Página 1 de 3")).toBeInTheDocument();
  });

  it("vuelve a una página válida si una recarga elimina el último resultado", async () => {
    const datos = Array.from({ length: 11 }, (_, i) => reserva(String(i).padStart(2, "0")));
    const { rerender } = render(<ReservasAdminListado {...props} reservas={datos} />);
    await userEvent.setup().click(screen.getByRole("button", { name: "Siguiente" }));
    rerender(<ReservasAdminListado {...props} reservas={datos.slice(0, 10)} />);
    expect(filas()).toHaveLength(10);
    expect(screen.getByRole("status")).toHaveTextContent("1–10 de 10 reservas");
    expect(screen.queryByRole("navigation", { name: "Páginas de reservas" })).not.toBeInTheDocument();
    rerender(<ReservasAdminListado {...props} reservas={datos} />);
    expect(screen.getByRole("status")).toHaveTextContent("1–10 de 11 reservas");
    expect(screen.getByText("Página 1 de 2")).toBeInTheDocument();
  });

  it("diferencia un filtro vacío de un sistema sin reservas y permite ver todas", async () => {
    const { rerender } = render(<ReservasAdminListado {...props} reservas={[reserva("1", { estado: "CANCELADA" })]} />);
    expect(screen.getByText("No hay reservas activas.")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Ver todas las reservas" }));
    expect(filas()).toHaveLength(1);
    rerender(<ReservasAdminListado {...props} reservas={[]} />);
    expect(screen.getByText("Todavía no hay reservas cargadas.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Ver todas las reservas" })).not.toBeInTheDocument();
  });

  it("distingue carga y error de una lista vacía, con reintento explícito", async () => {
    const reintentar = vi.fn();
    const { rerender } = render(<ReservasAdminListado {...props} reservas={[]} cargando />);
    expect(screen.getByRole("status")).toHaveTextContent("Cargando reservas…");
    expect(screen.queryByText("Todavía no hay reservas cargadas.")).not.toBeInTheDocument();
    rerender(<ReservasAdminListado {...props} reservas={[]} error onReintentar={reintentar} />);
    expect(screen.getByRole("alert")).toHaveTextContent("No se pudieron cargar las reservas.");
    await userEvent.setup().click(screen.getByRole("button", { name: "Reintentar" }));
    expect(reintentar).toHaveBeenCalledOnce();
  });

  it("bloquea cancelaciones duplicadas mientras se procesa la acción existente", async () => {
    let terminar;
    const cancelar = vi.fn(() => new Promise((resolve) => { terminar = resolve; }));
    const datos = [reserva("1"), reserva("2")];
    const { rerender } = render(<ReservasAdminListado {...props} reservas={datos} onCancelar={cancelar} />);
    const user = userEvent.setup();
    await user.click(screen.getAllByRole("button", { name: "Cancelar" })[0]);
    expect(screen.getByRole("button", { name: "Cancelando…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Cancelar" })).toBeDisabled();
    expect(cancelar).toHaveBeenCalledExactlyOnceWith(datos[0]);
    await act(async () => terminar());
    rerender(<ReservasAdminListado {...props} reservas={[{ ...datos[0], estado: "CANCELADA" }, datos[1]]} onCancelar={cancelar} />);
    expect(filas()).toHaveLength(1);
    expect(filtro("Canceladas")).toHaveTextContent("1");
    expect(screen.getByRole("button", { name: "Cancelar" })).toBeEnabled();
  });

  it("conserva el historial desplegable y los datos ausentes de reservas antiguas", async () => {
    render(<ReservasAdminListado {...props} reservas={[reserva("1", { fechaCreacion: null, precioTotal: null })]} />);
    expect(screen.getByText("Alta: Sin fecha registrada")).toBeInTheDocument();
    expect(screen.getByText("Sin importe registrado")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByText("Historial de la reserva"));
    expect(screen.getByText("Alta sin autor registrado.")).toBeVisible();
    expect(screen.getByText("Sin movimientos registrados.")).toBeVisible();
  });
});
