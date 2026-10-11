import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import ReservasListado from "@/components/ReservasListado";

const reserva = (id, extra = {}) => ({
  id, estado: "CONFIRMADA", desde: "2026-10-10T10:00", hasta: "2026-10-10T12:00",
  fechaCreacion: "2026-10-09T13:00:00Z", precioTotal: 2000, modalidad: "FRANJA",
  visitante: { nombre: `Visitante ${id}` }, vehiculo: { patente: "ABC123", tipo: "AUTO" },
  cochera: { numero: "A-01", sector: "Planta baja" }, historial: [],
  acceso: { codigo: "ABCDE12345", ingresoDesde: "2026-10-10T09:45:00", ingresoHasta: "2026-10-10T10:30:00" },
  ...extra,
});
const props = { cargando: false, error: false, onReintentar: vi.fn(), onCancelar: vi.fn() };
const filtro = (nombre) => screen.getByRole("button", { name: new RegExp(`^${nombre} \\d+$`) });
const filas = (nombre) => within(screen.getByRole("list", { name: nombre })).getAllByRole("listitem");

const todas = [
  reserva("confirmada"),
  reserva("adentro", { estado: "EN_CURSO", acceso: { codigo: "FGHJK67890", ingresoAt: "2026-10-10T10:05:00" } }),
  reserva("salio", { estado: "FINALIZADA", acceso: {
    codigo: null, ingresoAt: "2026-10-10T10:05:00", egresoAt: "2026-10-10T12:40:00", minutosExceso: 40,
  } }),
  reserva("noshow", { estado: "VENCIDA", acceso: { codigo: null } }),
];

describe.each(["user", "admin"])("ReservasListado con los estados de acceso (%s)", (modo) => {
  const lista = modo === "admin" ? "Reservas del administrador" : "Mis reservas";

  it("Activas incluye las reservas con el vehículo adentro, y hay un filtro de Vencidas", async () => {
    const user = userEvent.setup();
    render(<ReservasListado {...props} modo={modo} reservas={todas} />);

    expect(filtro("Activas")).toHaveTextContent("2");
    expect(filas(lista)).toHaveLength(2);
    await user.click(filtro("Vencidas"));
    expect(filas(lista)).toHaveLength(1);
    expect(screen.getByText(/no se registró el ingreso a tiempo/)).toBeInTheDocument();
    await user.click(filtro("Finalizadas"));
    expect(filas(lista)).toHaveLength(1);
  });

  it("muestra una insignia para cada estado nuevo", async () => {
    const user = userEvent.setup();
    render(<ReservasListado {...props} modo={modo} reservas={todas} />);
    await user.click(filtro("Todas"));

    expect(screen.getByText("EN CURSO")).toBeInTheDocument();
    expect(screen.getByText("FINALIZADA")).toBeInTheDocument();
    expect(screen.getByText("VENCIDA")).toBeInTheDocument();
    expect(screen.getByText("CONFIRMADA")).toBeInTheDocument();
  });

  it("muestra la hora de ingreso, la de egreso y el exceso registrados en la puerta", async () => {
    const user = userEvent.setup();
    render(<ReservasListado {...props} modo={modo} reservas={todas} />);
    await user.click(filtro("Finalizadas"));

    expect(filas(lista)[0]).toHaveTextContent("Ingreso 10:05 · Egreso 12:40 · excedido 40 min");
  });

  it("una reserva con el vehículo adentro ya no se puede cancelar", () => {
    render(<ReservasListado {...props} modo={modo} reservas={[todas[1]]} />);

    expect(screen.queryByRole("button", { name: "Cancelar" })).not.toBeInTheDocument();
  });
});

describe("ReservasListado: botón Ver QR", () => {
  it("el visitante lo ve en las CONFIRMADA y EN_CURSO, y abre el código de esa reserva", async () => {
    const user = userEvent.setup();
    const onVerCodigo = vi.fn();
    render(<ReservasListado {...props} modo="user" reservas={todas} onVerCodigo={onVerCodigo} />);

    const fila = (estado) => filas("Mis reservas").find((li) => within(li).queryByText(estado));
    const confirmada = fila("CONFIRMADA");
    const adentro = fila("EN CURSO");
    expect(within(confirmada).getByRole("button", { name: "Ver QR" })).toBeInTheDocument();
    await user.click(within(adentro).getByRole("button", { name: "Ver QR" }));
    expect(onVerCodigo).toHaveBeenCalledWith(todas[1]);
  });

  it("no aparece en reservas sin código vigente", async () => {
    const user = userEvent.setup();
    render(<ReservasListado {...props} modo="user" reservas={todas} onVerCodigo={vi.fn()} />);
    await user.click(filtro("Todas"));

    expect(screen.getAllByRole("button", { name: "Ver QR" })).toHaveLength(2);
  });

  it("el admin no lo ve en su listado", () => {
    render(<ReservasListado {...props} modo="admin" reservas={todas} onVerCodigo={vi.fn()} />);

    expect(screen.queryByRole("button", { name: "Ver QR" })).not.toBeInTheDocument();
  });
});
