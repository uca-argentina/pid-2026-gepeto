import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen } from "@testing-library/react";

const { toStringMock } = vi.hoisted(() => ({ toStringMock: vi.fn() }));

vi.mock("qrcode", () => ({ default: { toString: toStringMock } }));

const { default: CodigoAccesoQR } = await import("@/components/CodigoAccesoQR");

describe("CodigoAccesoQR", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    toStringMock.mockResolvedValue("<svg xmlns='http://www.w3.org/2000/svg'></svg>");
  });

  it("dibuja el QR con el prefijo APARCAR: y colores fijos, sin depender del tema", async () => {
    render(<CodigoAccesoQR codigo="ABCDE12345" />);

    const img = await screen.findByRole("img", { name: "Código QR de acceso ABCDE-12345" });
    expect(img.getAttribute("src")).toMatch(/^data:image\/svg\+xml/);
    expect(toStringMock).toHaveBeenCalledWith("APARCAR:ABCDE12345", expect.objectContaining({
      type: "svg",
      color: { dark: "#000000", light: "#ffffff" },
    }));
  });

  it("mientras genera muestra un indicador de carga", () => {
    toStringMock.mockReturnValue(new Promise(() => {}));
    render(<CodigoAccesoQR codigo="ABCDE12345" />);

    expect(screen.getByRole("status", { name: "Generando el código QR" })).toBeInTheDocument();
  });

  it("si no puede dibujarlo, remite al código escrito", async () => {
    toStringMock.mockRejectedValue(new Error("boom"));
    render(<CodigoAccesoQR codigo="ABCDE12345" />);

    expect(await screen.findByRole("alert")).toHaveTextContent(/código escrito/);
  });

  it("al cambiar de reserva no deja el QR de la anterior", async () => {
    const { rerender } = render(<CodigoAccesoQR codigo="ABCDE12345" />);
    await screen.findByRole("img", { name: /ABCDE-12345/ });

    rerender(<CodigoAccesoQR codigo="FGHJK67890" />);

    expect(await screen.findByRole("img", { name: /FGHJK-67890/ })).toBeInTheDocument();
    expect(screen.queryByRole("img", { name: /ABCDE-12345/ })).not.toBeInTheDocument();
  });
});
