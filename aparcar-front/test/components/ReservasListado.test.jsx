import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import ReservasListado from "@/components/ReservasListado";

const reserva = (id, extra = {}) => ({
  id, estado: "CONFIRMADA", desde: "2026-10-10T10:00", hasta: "2026-10-10T12:00",
  fechaCreacion: "2026-10-09T13:00:00Z", precioTotal: 2000, modalidad: "FRANJA",
  visitante: { nombre: `Visitante ${id}` }, vehiculo: { patente: "ABC123" },
  cochera: { numero: "A-01", sector: "Planta baja" }, historial: [], ...extra,
});
const props = { modo: "admin", cargando: false, error: false, onReintentar: vi.fn(), onCancelar: vi.fn() };
const filtro = (nombre) => screen.getByRole("button", { name: new RegExp(`^${nombre} \\d+$`) });
const filas = () => within(screen.getByRole("list", { name: "Reservas del administrador" })).getAllByRole("listitem");

describe("ReservasListado en modo admin", () => {
  it("abre en Activas por inicio y muestra ocupante, rango con año, cochera, importe y alta", () => {
    render(<ReservasListado {...props} reservas={[
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
    render(<ReservasListado {...props} reservas={[
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
    const { rerender } = render(<ReservasListado {...props} reservas={datos} />);
    await user.selectOptions(screen.getByLabelText("Ordenar por"), "ALTA_DESC");
    expect(filas()[0]).toHaveTextContent("Visitante nueva");
    await user.click(filtro("Todas"));
    rerender(<ReservasListado {...props} reservas={[...datos, reserva("ultima", { fechaCreacion: "2026-10-09T15:00:00Z" })]} />);
    expect(filtro("Todas")).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText("Ordenar por")).toHaveValue("ALTA_DESC");
    expect(filas()[0]).toHaveTextContent("Visitante ultima");
  });

  it("pagina de a diez, permite volver y reinicia la página al cambiar orden o filtro", async () => {
    const user = userEvent.setup();
    render(<ReservasListado {...props} reservas={Array.from({ length: 21 }, (_, i) => reserva(String(i).padStart(2, "0")))} />);
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
    const { rerender } = render(<ReservasListado {...props} reservas={datos} />);
    await userEvent.setup().click(screen.getByRole("button", { name: "Siguiente" }));
    rerender(<ReservasListado {...props} reservas={datos.slice(0, 10)} />);
    expect(filas()).toHaveLength(10);
    expect(screen.getByRole("status")).toHaveTextContent("1–10 de 10 reservas");
    expect(screen.queryByRole("navigation", { name: "Páginas de reservas" })).not.toBeInTheDocument();
    rerender(<ReservasListado {...props} reservas={datos} />);
    expect(screen.getByRole("status")).toHaveTextContent("1–10 de 11 reservas");
    expect(screen.getByText("Página 1 de 2")).toBeInTheDocument();
  });

  it("diferencia un filtro vacío de un sistema sin reservas y permite ver todas", async () => {
    const { rerender } = render(<ReservasListado {...props} reservas={[reserva("1", { estado: "CANCELADA" })]} />);
    expect(screen.getByText("No hay reservas activas.")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByRole("button", { name: "Ver todas las reservas" }));
    expect(filas()).toHaveLength(1);
    rerender(<ReservasListado {...props} reservas={[]} />);
    expect(screen.getByText("Todavía no hay reservas cargadas.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Ver todas las reservas" })).not.toBeInTheDocument();
  });

  it("distingue carga y error de una lista vacía, con reintento explícito", async () => {
    const reintentar = vi.fn();
    const { rerender } = render(<ReservasListado {...props} reservas={[]} cargando />);
    expect(screen.getByRole("status")).toHaveTextContent("Cargando reservas…");
    expect(screen.queryByText("Todavía no hay reservas cargadas.")).not.toBeInTheDocument();
    rerender(<ReservasListado {...props} reservas={[]} error onReintentar={reintentar} />);
    expect(screen.getByRole("alert")).toHaveTextContent("No se pudieron cargar las reservas.");
    await userEvent.setup().click(screen.getByRole("button", { name: "Reintentar" }));
    expect(reintentar).toHaveBeenCalledOnce();
  });

  it("bloquea cancelaciones duplicadas mientras se procesa la acción existente", async () => {
    let terminar;
    const cancelar = vi.fn(() => new Promise((resolve) => { terminar = resolve; }));
    const datos = [reserva("1"), reserva("2")];
    const { rerender } = render(<ReservasListado {...props} reservas={datos} onCancelar={cancelar} />);
    const user = userEvent.setup();
    await user.click(screen.getAllByRole("button", { name: "Cancelar" })[0]);
    expect(screen.getByRole("button", { name: "Cancelando…" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "Cancelar" })).toBeDisabled();
    expect(cancelar).toHaveBeenCalledExactlyOnceWith(datos[0]);
    await act(async () => terminar());
    rerender(<ReservasListado {...props} reservas={[{ ...datos[0], estado: "CANCELADA" }, datos[1]]} onCancelar={cancelar} />);
    expect(filas()).toHaveLength(1);
    expect(filtro("Canceladas")).toHaveTextContent("1");
    expect(screen.getByRole("button", { name: "Cancelar" })).toBeEnabled();
  });

  it("conserva el historial desplegable y los datos ausentes de reservas antiguas", async () => {
    render(<ReservasListado {...props} reservas={[reserva("1", { fechaCreacion: null, precioTotal: null })]} />);
    expect(screen.getByText("Alta: Sin fecha registrada")).toBeInTheDocument();
    expect(screen.getByText("Sin importe registrado")).toBeInTheDocument();
    await userEvent.setup().click(screen.getByText("Historial de la reserva"));
    expect(screen.getByText("Alta sin autor registrado.")).toBeVisible();
    expect(screen.getByText("Sin movimientos registrados.")).toBeVisible();
  });
});

describe("ReservasListado en modo usuario", () => {
  const propsUsuario = { ...props, modo: "user" };
  const filasUsuario = () => within(screen.getByRole("list", { name: "Mis reservas" })).getAllByRole("listitem");

  it("por defecto muestra las propias activas por inicio, con patente gráfica y sin datos administrativos", () => {
    render(<ReservasListado reservas={[
      reserva("tardia", { desde: "2026-10-12T10:00" }),
      reserva("proxima", { vehiculo: { patente: "A123BCD", tipo: "MOTO" }, historial: [{
        id: "m1", accion: "ALTA", actorNombre: "Ana Admin", actorEmail: "admin@test.com",
        actorRol: "ADMIN", fecha: "2026-10-09T13:00:00Z",
      }] }),
      reserva("baja", { estado: "CANCELADA" }),
    ]} />);
    expect(screen.getByRole("heading", { name: "Mis reservas" })).toBeInTheDocument();
    expect(filtro("Activas")).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText("Ordenar por")).toHaveValue("INICIO_ASC");
    expect(filasUsuario()).toHaveLength(2);
    const primera = within(filasUsuario()[0]);
    expect(primera.getByRole("img", { name: "Patente A123BCD" })).toHaveAttribute("data-formato", "moto-mercosur");
    expect(primera.getByText("10/10/2026 de 10:00 a 12:00")).toBeInTheDocument();
    expect(filasUsuario()[0]).toHaveTextContent("A-01 · Planta baja");
    expect(filasUsuario()[0]).toHaveTextContent(/Total:.*2\.000,00 ARS/);
    expect(filasUsuario()[0]).toHaveTextContent(/Fecha de alta:.*10:00/);
    expect(screen.queryByText(/Visitante proxima|Ana Admin|admin@test.com/)).not.toBeInTheDocument();
    expect(screen.queryByText("Historial de la reserva")).not.toBeInTheDocument();
  });

  it.each([
    ["USUARIO", "CANCELASTE ESTA RESERVA"],
    ["ADMINISTRACION", "CANCELADA POR ADMINISTRACIÓN"],
    ["DESHABILITACION", "DESHABILITADA POR ADMINISTRACIÓN"],
    [null, "CANCELADA"],
  ])("conserva el mensaje de cancelación %s al filtrar", async (motivoCancelacion, mensaje) => {
    render(<ReservasListado {...propsUsuario} reservas={[
      reserva("1", { estado: "CANCELADA", motivoCancelacion }),
    ]} />);
    expect(screen.getByText("No hay reservas activas.")).toBeInTheDocument();
    await userEvent.setup().click(filtro("Canceladas"));
    expect(screen.getByText(mensaje)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancelar" })).not.toBeInTheDocument();
    expect(screen.queryByText("Historial de la reserva")).not.toBeInTheDocument();
  });

  it("conserva filtro y orden al recargar y ajusta la página después de cancelar", async () => {
    const datos = Array.from({ length: 11 }, (_, i) => reserva(String(i), {
      fechaCreacion: `2026-10-${String(i + 1).padStart(2, "0")}T13:00:00Z`,
      cochera: { numero: `A-${i + 1}`, sector: "Planta baja" },
    }));
    const { rerender } = render(<ReservasListado {...propsUsuario} reservas={datos} />);
    const user = userEvent.setup();
    await user.selectOptions(screen.getByLabelText("Ordenar por"), "ALTA_ASC");
    expect(filasUsuario()[0]).toHaveTextContent("A-1 · Planta baja");
    await user.click(screen.getByRole("button", { name: "Siguiente" }));
    expect(filasUsuario()).toHaveLength(1);
    expect(filasUsuario()[0]).toHaveTextContent("A-11 · Planta baja");
    rerender(<ReservasListado {...propsUsuario} reservas={datos.map((r, i) => i === 10 ? { ...r, estado: "CANCELADA" } : r)} />);
    expect(filasUsuario()).toHaveLength(10);
    expect(screen.getByRole("status")).toHaveTextContent("1–10 de 10 reservas");
    expect(screen.getByLabelText("Ordenar por")).toHaveValue("ALTA_ASC");
    expect(filtro("Activas")).toHaveAttribute("aria-pressed", "true");
    expect(filtro("Canceladas")).toHaveTextContent("1");
    await user.click(filtro("Todas"));
    expect(screen.getByText("Página 1 de 2")).toBeInTheDocument();
  });
});
