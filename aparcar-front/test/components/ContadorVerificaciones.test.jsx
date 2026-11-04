import { describe, it, expect, beforeEach, afterEach, vi } from "vitest";
import { act, render, screen } from "@testing-library/react";

const { getMock } = vi.hoisted(() => ({ getMock: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get: getMock } }));
vi.mock("next/link", () => ({
  default: ({ href, children, ...resto }) => <a href={href} {...resto}>{children}</a>,
}));

const { default: ContadorVerificaciones, EVENTO_VERIFICACIONES } = await import("@/components/ContadorVerificaciones");

describe("ContadorVerificaciones", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.useFakeTimers({ shouldAdvanceTime: true });
  });

  afterEach(() => vi.useRealTimers());

  it("muestra la cantidad de pendientes al entrar", async () => {
    getMock.mockResolvedValue({ data: { cantidad: 3 } });
    render(<ContadorVerificaciones />);

    const enlace = await screen.findByRole("link", { name: "Verificaciones, 3 pendientes" });
    expect(enlace).toHaveAttribute("href", "/dashboard-admin/verificaciones");
    expect(enlace).toHaveTextContent("3");
    expect(getMock).toHaveBeenCalledWith("/api/v1/verificaciones-discapacidad/pendientes/cantidad");
  });

  it("sin pendientes no muestra el contador", async () => {
    getMock.mockResolvedValue({ data: { cantidad: 0 } });
    render(<ContadorVerificaciones />);
    await act(async () => {});

    expect(screen.getByRole("link", { name: "Verificaciones" })).toBeInTheDocument();
  });

  it("se actualiza cada minuto y cuando se resuelve una solicitud", async () => {
    getMock.mockResolvedValue({ data: { cantidad: 2 } });
    render(<ContadorVerificaciones />);
    await screen.findByRole("link", { name: "Verificaciones, 2 pendientes" });

    getMock.mockResolvedValue({ data: { cantidad: 5 } });
    await act(async () => { vi.advanceTimersByTime(60000); });
    expect(await screen.findByRole("link", { name: "Verificaciones, 5 pendientes" })).toBeInTheDocument();

    getMock.mockResolvedValue({ data: { cantidad: 1 } });
    await act(async () => { window.dispatchEvent(new Event(EVENTO_VERIFICACIONES)); });
    expect(await screen.findByRole("link", { name: "Verificaciones, 1 pendientes" })).toBeInTheDocument();
  });

  it("si la consulta falla, sigue mostrando el acceso", async () => {
    getMock.mockRejectedValue(new Error("red"));
    render(<ContadorVerificaciones />);
    await act(async () => {});

    expect(screen.getByRole("link", { name: "Verificaciones" })).toBeInTheDocument();
  });
});
