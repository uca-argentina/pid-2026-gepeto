import { describe, it, expect, beforeEach, vi } from "vitest";
import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock, postMock, lectorProps } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  lectorProps: { ultimo: null, montajes: 0 },
}));

vi.mock("@/app/api", () => ({ default: { get: getMock, post: postMock } }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
// La cámara se prueba en LectorQR.test.jsx; acá alcanza con simular una lectura.
vi.mock("@/components/LectorQR", async () => {
  const { useEffect } = await import("react");
  return {
    default: function LectorFalso(props) {
      lectorProps.ultimo = props;
      useEffect(() => {
        lectorProps.montajes += 1;
      }, []);
      return (
        <button type="button" onClick={() => props.onLectura("APARCAR:ABCDE12345")}>
          Simular lectura
        </button>
      );
    },
  };
});

const { default: ControlAccesoContent } = await import("@/app/dashboard-admin/accesos/ControlAccesoContent");

const reserva = (extra = {}) => ({
  id: "r1", estado: "CONFIRMADA", desde: "2026-10-10T15:00:00", hasta: "2026-10-10T17:00:00",
  visitanteNombre: "Juan Perez", patente: "AB123CD", tipoVehiculo: "AUTO",
  cocheraNumero: "A-01", cocheraSector: "Planta Baja", cocheraTipo: "AUTO",
  ingresoDesde: "2026-10-10T14:45:00", ingresoHasta: "2026-10-10T15:30:00",
  ingresoAt: null, egresoAt: null, minutosExceso: null, ...extra,
});

const consultaValida = (accion = "INGRESO", extra = {}) => ({
  data: {
    codigo: "ABCDE-12345", valido: true, accion, motivo: null, mensaje: "Puede ingresar.",
    reserva: reserva(accion === "EGRESO" ? { estado: "EN_CURSO", ingresoAt: "2026-10-10T15:05:00" } : {}),
    ...extra,
  },
});

const registroOk = (accion = "INGRESO", extra = {}) => ({
  data: {
    accion, yaRegistrado: false, fecha: "2026-10-10T15:06:00", excedido: false, minutosExceso: null,
    mensaje: accion === "INGRESO" ? "Ingreso registrado. Cochera A-01." : "Egreso registrado. La cochera A-01 quedó libre.",
    reserva: reserva({ estado: accion === "INGRESO" ? "EN_CURSO" : "FINALIZADA" }),
    ...extra,
  },
});

const errorHttp = (status, message, details) =>
  Object.assign(new Error(message), { response: { status, data: { code: status, message, details } } });

async function tipear(user, codigo) {
  await user.click(screen.getByRole("tab", { name: "Escribir código" }));
  const campo = screen.getByLabelText("Código de acceso");
  await user.clear(campo);
  await user.type(campo, codigo);
  await user.click(screen.getByRole("button", { name: "Validar" }));
}

beforeEach(() => {
  vi.clearAllMocks();
  lectorProps.ultimo = null;
  lectorProps.montajes = 0;
  getMock.mockResolvedValue({ data: [] });
});

describe("ControlAccesoContent: código escrito", () => {
  it("valida un código tipeado y muestra de quién es la reserva y qué corresponde", async () => {
    const user = userEvent.setup();
    postMock.mockResolvedValueOnce(consultaValida());
    render(<ControlAccesoContent />);

    await tipear(user, "ABCDE-12345");

    expect(postMock).toHaveBeenCalledWith("/api/v1/accesos/consulta", { codigo: "ABCDE12345" });
    const tarjeta = await screen.findByRole("article", { name: "Corresponde: INGRESO" });
    expect(tarjeta).toHaveTextContent("Juan Perez");
    expect(within(tarjeta).getByRole("img", { name: "Patente AB123CD" })).toBeInTheDocument();
    expect(tarjeta).toHaveTextContent("A-01 · Planta Baja");
    expect(tarjeta).toHaveTextContent("10/10 de 15:00 a 17:00");
    expect(tarjeta).toHaveTextContent("Entre 14:45 y 15:30");
    expect(within(tarjeta).getByRole("button", { name: "Registrar ingreso" })).toBeEnabled();
  });

  it.each([
    ["abcde-12345", "ABCDE12345"],
    ["ab cde 123 45", "ABCDE12345"],
    ["APARCAR:ABCDE12345", "ABCDE12345"],
    ["ABCDE-O2345", "ABCDE02345"],
  ])("normaliza %s antes de consultar", async (tipeado, enviado) => {
    const user = userEvent.setup();
    postMock.mockResolvedValueOnce(consultaValida());
    render(<ControlAccesoContent />);

    await tipear(user, tipeado);

    expect(postMock).toHaveBeenCalledWith("/api/v1/accesos/consulta", { codigo: enviado });
  });

  it("el campo muestra lo tipeado en mayúsculas", async () => {
    const user = userEvent.setup();
    render(<ControlAccesoContent />);
    await user.click(screen.getByRole("tab", { name: "Escribir código" }));

    await user.type(screen.getByLabelText("Código de acceso"), "abcde");

    expect(screen.getByLabelText("Código de acceso")).toHaveValue("ABCDE");
  });

  it("un código con formato inválido se rechaza sin consultar al backend", async () => {
    const user = userEvent.setup();
    render(<ControlAccesoContent />);

    await tipear(user, "ABC12");

    expect(postMock).not.toHaveBeenCalled();
    expect(screen.getByRole("alert")).toHaveTextContent("El código tiene 10 letras o números");
  });

  it("un código inexistente muestra el mensaje del backend", async () => {
    const user = userEvent.setup();
    postMock.mockRejectedValueOnce(errorHttp(404, "No existe ninguna reserva con el código ZZZZZ-99999."));
    render(<ControlAccesoContent />);

    await tipear(user, "ZZZZZ-99999");

    expect(await screen.findByRole("alert")).toHaveTextContent("No existe ninguna reserva con el código ZZZZZ-99999.");
  });

  it("un código que no se puede usar muestra el motivo destacado y no ofrece registrar", async () => {
    const user = userEvent.setup();
    postMock.mockResolvedValueOnce({
      data: {
        codigo: "ABCDE-12345", valido: false, accion: null, motivo: "VENCIDA",
        mensaje: "La reserva venció: no se registró el ingreso a tiempo (la ventana cerró a las 15:30).",
        reserva: reserva({ estado: "VENCIDA" }),
      },
    });
    render(<ControlAccesoContent />);

    await tipear(user, "ABCDE12345");

    const tarjeta = await screen.findByRole("article", { name: "Reserva vencida" });
    expect(within(tarjeta).getByRole("alert")).toHaveTextContent("La reserva venció");
    expect(screen.queryByRole("button", { name: /Registrar/ })).not.toBeInTheDocument();
    await user.click(within(tarjeta).getByRole("button", { name: "Validar otro código" }));
    expect(screen.queryByRole("article")).not.toBeInTheDocument();
  });
});

describe("ControlAccesoContent: confirmación", () => {
  it("registra el ingreso, muestra el resultado y deja el campo listo para el siguiente", async () => {
    const user = userEvent.setup();
    postMock.mockResolvedValueOnce(consultaValida()).mockResolvedValueOnce(registroOk("INGRESO"));
    getMock.mockResolvedValueOnce({ data: [] }).mockResolvedValueOnce({
      data: [{
        id: "m1", accion: "INGRESO", fecha: "2026-10-10T18:06:00Z", reservaId: "r1", visitanteNombre: "Juan Perez",
        patente: "AB123CD", tipoVehiculo: "AUTO", cocheraNumero: "A-01", cocheraSector: "Planta Baja",
        operador: "Ana Operadora", minutosExceso: null,
      }],
    });
    render(<ControlAccesoContent />);
    await tipear(user, "ABCDE12345");

    await user.click(await screen.findByRole("button", { name: "Registrar ingreso" }));

    expect(postMock).toHaveBeenLastCalledWith("/api/v1/accesos/registro", { codigo: "ABCDE12345", accion: "INGRESO" });
    expect(await screen.findByText("Ingreso registrado")).toBeInTheDocument();
    expect(screen.getByText("Ingreso registrado. Cochera A-01.")).toBeInTheDocument();
    expect(screen.queryByRole("article")).not.toBeInTheDocument();
    expect(screen.getByLabelText("Código de acceso")).toHaveValue("");
    expect(screen.getByLabelText("Código de acceso")).toHaveFocus();
    const movimientos = await screen.findByRole("list", { name: "Ingresos y egresos de hoy" });
    expect(movimientos).toHaveTextContent("Ingreso");
    expect(movimientos).toHaveTextContent("Cochera A-01 · Juan Perez");
    expect(movimientos).toHaveTextContent("Ana Operadora");
  });

  it("registra el egreso y avisa si el vehículo se pasó de hora", async () => {
    const user = userEvent.setup();
    postMock
      .mockResolvedValueOnce(consultaValida("EGRESO", { mensaje: "Puede salir. Se pasó 40 minutos de su horario." }))
      .mockResolvedValueOnce(registroOk("EGRESO", {
        excedido: true, minutosExceso: 40, mensaje: "Egreso registrado con exceso: se pasó 40 minutos de su horario.",
      }));
    render(<ControlAccesoContent />);
    await tipear(user, "ABCDE12345");

    const tarjeta = await screen.findByRole("article", { name: "Corresponde: EGRESO" });
    expect(tarjeta).toHaveTextContent("Ingresó15:05");
    await user.click(within(tarjeta).getByRole("button", { name: "Registrar egreso" }));

    expect(postMock).toHaveBeenLastCalledWith("/api/v1/accesos/registro", { codigo: "ABCDE12345", accion: "EGRESO" });
    expect(await screen.findByText("Egreso registrado")).toBeInTheDocument();
    expect(screen.getByText("Egreso registrado con exceso: se pasó 40 minutos de su horario.")).toBeInTheDocument();
  });

  it("un doble clic en confirmar manda un solo registro", async () => {
    const user = userEvent.setup();
    let resolver;
    postMock
      .mockResolvedValueOnce(consultaValida())
      .mockReturnValueOnce(new Promise((resolve) => { resolver = resolve; }));
    render(<ControlAccesoContent />);
    await tipear(user, "ABCDE12345");
    const confirmar = await screen.findByRole("button", { name: "Registrar ingreso" });

    await user.dblClick(confirmar);
    await user.click(screen.getByRole("button", { name: "Registrando…" }));

    expect(postMock.mock.calls.filter(([url]) => url === "/api/v1/accesos/registro")).toHaveLength(1);
    resolver(registroOk());
    expect(await screen.findByText("Ingreso registrado")).toBeInTheDocument();
  });

  it("si el backend ya lo tenía registrado, lo informa sin duplicarlo", async () => {
    const user = userEvent.setup();
    postMock.mockResolvedValueOnce(consultaValida()).mockResolvedValueOnce(registroOk("INGRESO", {
      yaRegistrado: true, mensaje: "El ingreso ya estaba registrado a las 15:06; no se registró de nuevo.",
    }));
    render(<ControlAccesoContent />);
    await tipear(user, "ABCDE12345");

    await user.click(await screen.findByRole("button", { name: "Registrar ingreso" }));

    expect(await screen.findByText("Ingreso ya registrado")).toBeInTheDocument();
  });

  it("un rechazo del backend al registrar se muestra con su mensaje", async () => {
    const user = userEvent.setup();
    postMock.mockResolvedValueOnce(consultaValida()).mockRejectedValueOnce(
      errorHttp(400, "La reserva venció: no se registró el ingreso a tiempo (la ventana cerró a las 15:30).", ["VENCIDA"])
    );
    render(<ControlAccesoContent />);
    await tipear(user, "ABCDE12345");

    await user.click(await screen.findByRole("button", { name: "Registrar ingreso" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("La reserva venció");
    expect(screen.queryByRole("button", { name: "Registrar ingreso" })).not.toBeInTheDocument();
  });

  it("un conflicto de concurrencia (409) pide volver a validar", async () => {
    const user = userEvent.setup();
    postMock.mockResolvedValueOnce(consultaValida()).mockRejectedValueOnce(errorHttp(409, "El registro cambio"));
    render(<ControlAccesoContent />);
    await tipear(user, "ABCDE12345");

    await user.click(await screen.findByRole("button", { name: "Registrar ingreso" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Otro operador registró este código al mismo tiempo");
  });

  it("sin conexión con el backend lo dice en vez de quedarse cargando", async () => {
    const user = userEvent.setup();
    postMock.mockRejectedValueOnce(new Error("Network Error"));
    render(<ControlAccesoContent />);

    await tipear(user, "ABCDE12345");

    expect(await screen.findByRole("alert")).toHaveTextContent("No se pudo validar el código");
  });
});

describe("ControlAccesoContent: escaneo", () => {
  it("lo leído con la cámara hace la misma consulta que el código escrito", async () => {
    const user = userEvent.setup();
    postMock.mockResolvedValueOnce(consultaValida());
    render(<ControlAccesoContent />);

    expect(screen.getByRole("tab", { name: "Escanear QR" })).toHaveAttribute("aria-selected", "true");
    await user.click(screen.getByRole("button", { name: "Simular lectura" }));

    expect(postMock).toHaveBeenCalledWith("/api/v1/accesos/consulta", { codigo: "ABCDE12345" });
    expect(await screen.findByRole("article", { name: "Corresponde: INGRESO" })).toBeInTheDocument();
  });

  it("después de registrar, el lector vuelve a prenderse solo para el siguiente vehículo", async () => {
    const user = userEvent.setup();
    postMock.mockResolvedValueOnce(consultaValida()).mockResolvedValueOnce(registroOk());
    render(<ControlAccesoContent />);
    expect(lectorProps.ultimo.autoIniciar).toBe(false);

    await user.click(screen.getByRole("button", { name: "Simular lectura" }));
    await user.click(await screen.findByRole("button", { name: "Registrar ingreso" }));

    await screen.findByText("Ingreso registrado");
    expect(lectorProps.montajes).toBe(2);
    expect(lectorProps.ultimo.autoIniciar).toBe(true);
  });

  it("un QR que no es de AparcAR se rechaza sin consultar", async () => {
    render(<ControlAccesoContent />);

    await act(() => lectorProps.ultimo.onLectura("https://otra-cosa.com"));

    expect(screen.getByRole("alert")).toHaveTextContent("Ese QR no es un código de AparcAR.");
    expect(postMock).not.toHaveBeenCalled();
  });

  it("si la cámara no se puede usar, el lector puede pasar al código escrito", async () => {
    render(<ControlAccesoContent />);

    await act(() => lectorProps.ultimo.onUsarCodigo());

    expect(await screen.findByLabelText("Código de acceso")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: "Escribir código" })).toHaveAttribute("aria-selected", "true");
  });
});

describe("ControlAccesoContent: movimientos", () => {
  it("lista los movimientos del día y avisa si no hay", async () => {
    render(<ControlAccesoContent />);

    expect(getMock).toHaveBeenCalledWith("/api/v1/accesos/movimientos");
    expect(await screen.findByText("Todavía no hubo ingresos ni egresos hoy.")).toBeInTheDocument();
  });

  it("marca los egresos excedidos", async () => {
    getMock.mockResolvedValue({
      data: [{
        id: "m2", accion: "EGRESO", fecha: "2026-10-10T20:40:00Z", reservaId: "r1", visitanteNombre: "Juan Perez",
        patente: "AB123CD", tipoVehiculo: "AUTO", cocheraNumero: "A-01", cocheraSector: "PB",
        operador: "Ana Operadora", minutosExceso: 40,
      }],
    });
    render(<ControlAccesoContent />);

    const lista = await screen.findByRole("list", { name: "Ingresos y egresos de hoy" });
    expect(lista).toHaveTextContent("Egreso");
    expect(lista).toHaveTextContent("excedido 40 min");
  });

  it("si no se pueden cargar, lo dice", async () => {
    getMock.mockRejectedValue(new Error("boom"));
    render(<ControlAccesoContent />);

    expect(await screen.findByText("No se pudieron cargar los movimientos.")).toBeInTheDocument();
  });
});
