import { beforeEach, expect, it, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get: getMock } }));

const { default: ResumenPuntos } = await import("@/app/dashboard-admin/dashboard/ResumenPuntos");

beforeEach(() => {
  vi.resetAllMocks();
  getMock.mockResolvedValue({ data: { emitidos: 12500, canjeados: 3000, enCirculacion: 9500, usuariosConRacha: 14, mejorRacha: 21 } });
});

it("muestra lo emitido, canjeado, en circulación y las rachas", async () => {
  render(<ResumenPuntos />);
  const seccion = screen.getByRole("region", { name: "Programa de puntos" });
  const datos = await within(seccion).findByText("Emitidos");
  expect(getMock).toHaveBeenCalledWith("/api/v1/puntos/resumen");
  expect(datos.nextSibling).toHaveTextContent("12.500");
  expect(seccion).toHaveTextContent("Canjeados3.000");
  expect(seccion).toHaveTextContent("En circulación9.500");
  expect(seccion).toHaveTextContent("Usuarios con racha activa14");
  expect(seccion).toHaveTextContent("Mejor racha21 días");
});

it("si falla, avisa y permite reintentar", async () => {
  getMock.mockRejectedValueOnce(new Error("403"));
  render(<ResumenPuntos />);
  expect(await screen.findByRole("alert")).toHaveTextContent("No se pudo cargar el resumen de puntos.");
  await userEvent.setup().click(screen.getByRole("button", { name: "Reintentar" }));
  expect(await screen.findByText("12.500")).toBeInTheDocument();
});
