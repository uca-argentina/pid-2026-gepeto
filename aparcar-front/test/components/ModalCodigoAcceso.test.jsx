import { describe, it, expect, beforeEach, vi } from "vitest";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("sonner", () => ({ toast: { success: toastSuccessMock, error: toastErrorMock } }));
vi.mock("qrcode", () => ({ default: { toString: vi.fn().mockResolvedValue("<svg></svg>") } }));

const { default: ModalCodigoAcceso } = await import("@/components/ModalCodigoAcceso");

const reserva = (extra = {}) => ({
  id: "r1", estado: "CONFIRMADA", desde: "2026-10-10T15:00", hasta: "2026-10-10T17:00",
  vehiculo: { patente: "AB123CD", tipo: "AUTO" }, cochera: { numero: "A-01", sector: "Planta Baja" },
  acceso: { codigo: "ABCDE12345", ingresoDesde: "2026-10-10T14:45:00", ingresoHasta: "2026-10-10T15:30:00" },
  ...extra,
});

describe("ModalCodigoAcceso", () => {
  beforeEach(() => vi.clearAllMocks());

  it("es un diálogo modal con el QR, el código grande, los datos y la ventana de ingreso", async () => {
    render(<ModalCodigoAcceso reserva={reserva()} onClose={vi.fn()} />);

    const dialogo = screen.getByRole("dialog", { name: "Tu código de acceso" });
    expect(dialogo).toHaveAttribute("aria-modal", "true");
    expect(await within(dialogo).findByRole("img", { name: /Código QR de acceso ABCDE-12345/ })).toBeInTheDocument();
    expect(within(dialogo).getByLabelText("Código ABCDE-12345")).toHaveTextContent("ABCDE-12345");
    expect(within(dialogo).getByRole("img", { name: "Patente AB123CD" })).toBeInTheDocument();
    expect(dialogo).toHaveTextContent("A-01 · Planta Baja");
    expect(dialogo).toHaveTextContent("10/10 de 15:00 a 17:00");
    expect(dialogo).toHaveTextContent("Ingresá entre 14:45 y 15:30.");
  });

  it("con el vehículo adentro recuerda que el mismo código sirve para salir", () => {
    render(<ModalCodigoAcceso onClose={vi.fn()} reserva={reserva({
      estado: "EN_CURSO", acceso: { codigo: "ABCDE12345", ingresoAt: "2026-10-10T15:05:00" },
    })} />);

    expect(screen.getByRole("dialog")).toHaveTextContent("Ingresaste a las 15:05. Mostrá este mismo código para salir.");
  });

  it("copia el código con guion al portapapeles y lo confirma", async () => {
    const user = userEvent.setup();
    const writeText = vi.spyOn(navigator.clipboard, "writeText").mockResolvedValue();
    render(<ModalCodigoAcceso reserva={reserva()} onClose={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: "Copiar" }));

    expect(writeText).toHaveBeenCalledWith("ABCDE-12345");
    expect(toastSuccessMock).toHaveBeenCalledWith("Código copiado");
  });

  it("si el navegador no deja copiar, lo avisa", async () => {
    const user = userEvent.setup();
    vi.spyOn(navigator.clipboard, "writeText").mockRejectedValue(new Error("denegado"));
    render(<ModalCodigoAcceso reserva={reserva()} onClose={vi.fn()} />);

    await user.click(screen.getByRole("button", { name: "Copiar" }));

    expect(toastErrorMock).toHaveBeenCalled();
  });

  it("cierra con Escape", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<ModalCodigoAcceso reserva={reserva()} onClose={onClose} />);

    await user.keyboard("{Escape}");

    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("cierra con el botón Cerrar y con un clic en el fondo, no con un clic adentro", async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<ModalCodigoAcceso reserva={reserva()} onClose={onClose} />);

    await user.click(screen.getByRole("dialog"));
    expect(onClose).not.toHaveBeenCalled();

    await user.click(screen.getByRole("button", { name: "Cerrar" }));
    await user.click(screen.getByRole("dialog").parentElement);
    expect(onClose).toHaveBeenCalledTimes(2);
  });

  it("atrapa el foco: arranca en cerrar y Tab da la vuelta sin salir del diálogo", async () => {
    const user = userEvent.setup();
    render(
      <>
        <button type="button">Afuera</button>
        <ModalCodigoAcceso reserva={reserva()} onClose={vi.fn()} />
      </>
    );
    const dialogo = screen.getByRole("dialog");
    const cerrarX = within(dialogo).getByRole("button", { name: "Cerrar ventana" });

    expect(dialogo).toContainElement(document.activeElement);
    expect(document.activeElement).toBe(cerrarX);

    for (let i = 0; i < 6; i++) {
      await user.tab();
      expect(dialogo).toContainElement(document.activeElement);
    }
    await user.tab({ shift: true });
    expect(dialogo).toContainElement(document.activeElement);
  });

  it("al cerrarse devuelve el foco a quien lo abrió y libera el scroll", () => {
    const abridor = document.createElement("button");
    document.body.appendChild(abridor);
    abridor.focus();
    const { unmount } = render(<ModalCodigoAcceso reserva={reserva()} onClose={vi.fn()} />);
    expect(document.body.style.overflow).toBe("hidden");

    unmount();

    expect(document.activeElement).toBe(abridor);
    expect(document.body.style.overflow).toBe("");
    abridor.remove();
  });

  it("muestra el título que le pasen (por ejemplo, al confirmar la reserva)", () => {
    render(<ModalCodigoAcceso reserva={reserva()} onClose={vi.fn()} titulo="Reserva confirmada: tu código de acceso" />);

    expect(screen.getByRole("dialog", { name: "Reserva confirmada: tu código de acceso" })).toBeInTheDocument();
  });
});
