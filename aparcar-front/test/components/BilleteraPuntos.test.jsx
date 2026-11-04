import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, expect, it, vi } from "vitest";
const { get } = vi.hoisted(() => ({ get: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get } }));
import BilleteraPuntos from "@/components/BilleteraPuntos";

const FRANJAS = [
  { clave: "NOCTURNA", nombre: "Noche (valle de demanda)", inicio: "22:00", fin: "06:00", puntos: 20 },
  { clave: "MANANA", nombre: "Mañana (pico de demanda)", inicio: "06:00", fin: "10:00", puntos: 10 },
  { clave: "MEDIODIA", nombre: "Mediodía (demanda media)", inicio: "10:00", fin: "14:00", puntos: 15 },
  { clave: "TARDE", nombre: "Tarde (demanda alta)", inicio: "14:00", fin: "22:00", puntos: 10 },
];

const billetera = (extra = {}) => ({
  saldo: 230, rachaActual: 5, mejorRacha: 9, ultimoDiaRacha: "2026-10-31", diasParaBonus: 2, rachaDias: 7,
  rachaBonus: 10, descuentoMaxPct: 50, puntosPorPorcentaje: 10, maxPuntosCanjeables: 500,
  duracionMinimaMin: 60, maxAcreditacionesDia: 2, franjas: FRANJAS, ...extra,
});

let movimientos;
beforeEach(() => {
  vi.resetAllMocks();
  movimientos = [
    { id: "m2", tipo: "CANJE", puntos: -100, descripcion: "Descuento del 10 % en la reserva del 30/10 10:00", fecha: "2026-10-30T12:00:00Z" },
    { id: "m1", tipo: "ACREDITACION_FRANJA", puntos: 20, descripcion: "Reserva del 29/10 23:00 · Noche", fecha: "2026-10-29T23:05:00Z" },
  ];
  get.mockImplementation((url) => url.endsWith("/billetera")
    ? Promise.resolve({ data: billetera() })
    : Promise.resolve({ data: movimientos }));
});

it("muestra el saldo, lo que alcanza de descuento y la racha con su avance", async () => {
  render(<BilleteraPuntos />);
  const saldo = await screen.findByRole("region", { name: "Tu saldo" });
  expect(saldo).toHaveTextContent("230 puntos");
  expect(saldo).toHaveTextContent("hasta un 23 % de descuento");

  const racha = screen.getByRole("region", { name: "Racha" });
  expect(racha).toHaveTextContent("5 días seguidos");
  expect(racha).toHaveTextContent("Te faltan 2 días seguidos para el bonus de +10");
  expect(racha).toHaveTextContent("Mejor racha: 9 días");
  const progreso = within(racha).getByRole("progressbar", { name: "Avance hacia el próximo bonus de racha" });
  expect(progreso).toHaveAttribute("aria-valuenow", "5");
  expect(progreso).toHaveAttribute("aria-valuemax", "7");
});

it("explica las reglas con la tabla de franjas que manda el servidor", async () => {
  render(<BilleteraPuntos />);
  const tabla = await screen.findByRole("table", { name: "Puntos por franja horaria de inicio" });
  const filas = within(tabla).getAllByRole("row");
  expect(filas).toHaveLength(5);
  expect(filas[1]).toHaveTextContent("Noche (valle de demanda)22:00 a 06:00+20");
  expect(screen.getByText(/menos de 60 minutos no suman/)).toBeInTheDocument();
  expect(screen.getByText(/hasta un 50 %/)).toBeInTheDocument();
});

it("lista el historial con signo y tipo", async () => {
  render(<BilleteraPuntos />);
  const historial = await screen.findByRole("region", { name: "Historial" });
  const items = within(historial).getAllByRole("listitem");
  expect(items[0]).toHaveTextContent("Descuento");
  expect(items[0]).toHaveTextContent("−100 puntos");
  expect(items[1]).toHaveTextContent("Puntos por reserva");
  expect(items[1]).toHaveTextContent("+20 puntos");
});

it("sin actividad: saldo en cero, racha apagada y sin movimientos", async () => {
  movimientos = [];
  get.mockImplementation((url) => url.endsWith("/billetera")
    ? Promise.resolve({ data: billetera({ saldo: 0, rachaActual: 0, mejorRacha: 0, diasParaBonus: 7 }) })
    : Promise.resolve({ data: [] }));
  render(<BilleteraPuntos />);
  expect(await screen.findByText(/Con 100 puntos ya tenés un 10 % de descuento/)).toBeInTheDocument();
  expect(screen.getByText("0 días seguidos")).toBeInTheDocument();
  expect(screen.getByText(/Todavía no tenés movimientos/)).toBeInTheDocument();
});

it("a un día del bonus lo dice así", async () => {
  get.mockImplementation((url) => url.endsWith("/billetera")
    ? Promise.resolve({ data: billetera({ rachaActual: 6, diasParaBonus: 1 }) })
    : Promise.resolve({ data: [] }));
  render(<BilleteraPuntos />);
  expect(await screen.findByText(/Mañana, si ingresás, sumás el bonus de \+10/)).toBeInTheDocument();
});

it("si falla la carga, avisa y permite reintentar", async () => {
  get.mockRejectedValueOnce(new Error("caída"));
  render(<BilleteraPuntos />);
  expect(await screen.findByRole("alert")).toHaveTextContent("No se pudieron cargar tus puntos.");
  await userEvent.setup().click(screen.getByRole("button", { name: "Reintentar" }));
  expect(await screen.findByRole("region", { name: "Tu saldo" })).toHaveTextContent("230 puntos");
});
