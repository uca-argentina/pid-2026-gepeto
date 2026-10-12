import { describe, it, expect, beforeEach, vi } from "vitest";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

const { getMock, postMock, putMock, deleteMock, toastSuccessMock, toastErrorMock } = vi.hoisted(() => ({
  getMock: vi.fn(),
  postMock: vi.fn(),
  putMock: vi.fn(),
  deleteMock: vi.fn(),
  toastSuccessMock: vi.fn(),
  toastErrorMock: vi.fn(),
}));

vi.mock("@/app/api", () => ({
  default: { get: getMock, post: postMock, put: putMock, delete: deleteMock },
}));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace: vi.fn() }),
}));
vi.mock("sonner", () => ({ toast: { success: toastSuccessMock, error: toastErrorMock } }));

const { default: CocherasManagement } = await import("@/app/dashboard-admin/cocheras/CocherasManagement");

const cochera = (overrides = {}) => ({
  id: "1",
  numero: "A-01",
  sector: "Planta Baja",
  tipo: "AUTO",
  estado: "HABILITADA",
  disponibleEnFecha: null,
  ...overrides,
});

// El componente pide dos cosas distintas por GET /api/v1/cocheras: una sin
// params (para el dropdown de sectores, siempre la lista completa) y otra con
// params (la tabla, filtrada). Este helper simula ambas a la vez.
function mockCocheras(todasLasCocheras, filtradas = todasLasCocheras, sectores) {
  const sectoresCalculados =
    sectores ?? Array.from(new Set(todasLasCocheras.map((c) => c.sector))).sort();

  getMock.mockImplementation((url, config) => {
    if (url === "/api/v1/cocheras/sectores") return Promise.resolve({ data: sectoresCalculados });
    if (url !== "/api/v1/cocheras") return Promise.reject(new Error(`URL no mockeada: ${url}`));
    const sinParams = !config?.params || Object.keys(config.params).length === 0;
    return Promise.resolve({ data: sinParams ? todasLasCocheras : filtradas });
  });
}

describe("CocherasManagement", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockCocheras([]);
  });

  it("muestra la navegación de regreso al panel", () => {
    render(<CocherasManagement />);

    expect(screen.getByRole("link", { name: "← Volver al panel" }))
      .toHaveAttribute("href", "/dashboard-admin");
  });

  it("carga y lista las cocheras existentes", async () => {
    mockCocheras([cochera(), cochera({ id: "2", numero: "M-01", tipo: "MOTO" })]);
    render(<CocherasManagement />);

    expect(await screen.findByText("A-01")).toBeInTheDocument();
    expect(screen.getByText("M-01")).toBeInTheDocument();
  });

  it("combina tipo y estado sin sector y recupera todas las cocheras al limpiar filtros", async () => {
    const todas = [cochera(), cochera({ id: "2", numero: "M-01", tipo: "MOTO", estado: "DESHABILITADA" })];
    mockCocheras(todas, [todas[1]]);
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");
    const tipo = screen.getByDisplayValue("Todos los tipos");
    const estado = screen.getByDisplayValue("Todos los estados");
    await user.selectOptions(estado, "DESHABILITADA");
    await waitFor(() => expect(getMock).toHaveBeenCalledWith(
      "/api/v1/cocheras", { params: { estado: "DESHABILITADA" } }
    ));
    await user.selectOptions(tipo, "MOTO");
    await waitFor(() => expect(getMock).toHaveBeenCalledWith(
      "/api/v1/cocheras", { params: { tipo: "MOTO", estado: "DESHABILITADA" } }
    ));
    expect(screen.queryByText("A-01")).not.toBeInTheDocument();
    await user.selectOptions(tipo, "TODOS");
    await user.selectOptions(estado, "TODOS");
    expect(await screen.findByText("A-01")).toBeInTheDocument();
    expect(screen.getByText("M-01")).toBeInTheDocument();
  });

  it("una respuesta anterior no reemplaza los resultados del filtro actual", async () => {
    let resolverAnterior;
    getMock.mockImplementation((url, config) => {
      if (url.endsWith("/sectores")) return Promise.resolve({ data: ["Planta Baja"] });
      if (!config.params.tipo) return new Promise((resolve) => { resolverAnterior = resolve; });
      return Promise.resolve({ data: [cochera({ id: "2", numero: "M-01", tipo: "MOTO" })] });
    });
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await user.selectOptions(screen.getByDisplayValue("Todos los tipos"), "MOTO");
    await screen.findByText("M-01");
    await act(async () => resolverAnterior({ data: [cochera()] }));
    expect(screen.getByText("M-01")).toBeInTheDocument();
    expect(screen.queryByText("A-01")).not.toBeInTheDocument();
  });

  it("el dropdown de sector se arma con los sectores reales, sin repetidos", async () => {
    mockCocheras([
      cochera({ sector: "Planta Baja" }),
      cochera({ id: "2", numero: "A-02", sector: "Planta Baja" }),
      cochera({ id: "3", numero: "S-01", sector: "Subsuelo" }),
    ]);
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    const selectSector = screen.getByDisplayValue("Todos los sectores");
    const opciones = Array.from(selectSector.querySelectorAll("option")).map((o) => o.textContent);
    expect(opciones).toEqual(["Todos los sectores", "Planta Baja", "Subsuelo"]);
  });

  it("filtra por sector pidiendole al backend, no en el cliente", async () => {
    mockCocheras(
      [cochera({ sector: "Planta Baja" }), cochera({ id: "2", numero: "A-02", sector: "Subsuelo" })],
      [cochera({ id: "2", numero: "A-02", sector: "Subsuelo" })]
    );
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    const selectSector = screen.getByDisplayValue("Todos los sectores");
    await user.selectOptions(selectSector, "Subsuelo");

    await waitFor(() =>
      expect(getMock).toHaveBeenCalledWith(
        "/api/v1/cocheras",
        expect.objectContaining({ params: expect.objectContaining({ sector: "Subsuelo" }) })
      )
    );
    expect(await screen.findByText("A-02")).toBeInTheDocument();
    expect(screen.queryByText("A-01")).not.toBeInTheDocument();
  });

  it("filtra por tipo pidiendole al backend", async () => {
    mockCocheras(
      [cochera({ tipo: "AUTO" }), cochera({ id: "2", numero: "M-01", tipo: "MOTO" })],
      [cochera({ id: "2", numero: "M-01", tipo: "MOTO" })]
    );
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    const [filtroTipoSelect] = screen.getAllByDisplayValue("Todos los tipos");
    await user.selectOptions(filtroTipoSelect, "MOTO");

    await waitFor(() =>
      expect(getMock).toHaveBeenCalledWith(
        "/api/v1/cocheras",
        expect.objectContaining({ params: expect.objectContaining({ tipo: "MOTO" }) })
      )
    );
    expect(await screen.findByText("M-01")).toBeInTheDocument();
    expect(screen.queryByText("A-01")).not.toBeInTheDocument();
  });

  it("sin fecha seleccionada, la columna de disponibilidad muestra un guion", async () => {
    mockCocheras([cochera({ disponibleEnFecha: null })]);
    render(<CocherasManagement />);

    await screen.findByText("A-01");
    expect(screen.getByText("—")).toBeInTheDocument();
  });

  // El filtro sigue siendo por día, pero el backend razona por rango: se
  // traduce al día completo para que "disponible el 15" siga significando
  // "sin ninguna reserva ese día".
  it("al elegir una fecha, pide al backend el dia completo como rango y muestra Libre/Ocupada", async () => {
    mockCocheras(
      [cochera({ disponibleEnFecha: null })],
      [cochera({ disponibleEnFecha: false })]
    );
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await user.type(screen.getByLabelText("Filtrar por fecha"), "2026-06-15");

    await waitFor(() =>
      expect(getMock).toHaveBeenCalledWith(
        "/api/v1/cocheras",
        expect.objectContaining({
          params: expect.objectContaining({
            desde: "2026-06-15T00:00",
            hasta: "2026-06-15T23:59",
          }),
        })
      )
    );
    expect(await screen.findByText("Ocupada")).toBeInTheDocument();
  });

    it("crea una cochera nueva y refresca la lista y los sectores", async () => {
    postMock.mockResolvedValue({ data: cochera() });
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await waitFor(() =>
      expect(getMock.mock.calls.some((c) => c[0] === "/api/v1/cocheras/sectores")).toBe(true)
    );

    await user.type(document.getElementById("numero"), "A-01");
    await user.type(document.getElementById("sector"), "Planta Baja");
    await user.click(screen.getByRole("button", { name: /crear cochera/i }));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith(
        "/api/v1/cocheras",
        expect.objectContaining({ numero: "A-01", sector: "Planta Baja", tipo: "AUTO", estado: "HABILITADA" })
      )
    );
    expect(toastSuccessMock).toHaveBeenCalledWith("Cochera creada correctamente");
    // Refresca tanto la lista filtrada como el dropdown de sectores.
    await waitFor(() => expect(getMock.mock.calls.length).toBeGreaterThanOrEqual(4));
  });

  it("muestra errores de validacion si falta numero o sector", async () => {
    const user = userEvent.setup();
    render(<CocherasManagement />);

    await user.click(screen.getByRole("button", { name: /crear cochera/i }));

    expect(await screen.findByText("El número es obligatorio")).toBeInTheDocument();
    expect(screen.getByText("El sector es obligatorio")).toBeInTheDocument();
    expect(postMock).not.toHaveBeenCalled();
  });

  it("al editar, precarga el formulario con los datos de la fila elegida", async () => {
    mockCocheras([cochera({ sector: "Planta Baja" })]);
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await user.click(screen.getByRole("button", { name: /^editar$/i }));

    expect(screen.getByDisplayValue("A-01")).toBeInTheDocument();
    expect(screen.getByDisplayValue("Planta Baja")).toBeInTheDocument();
  });

  it("al deshabilitar una cochera antes HABILITADA, pide confirmacion; si se cancela, no llama a PUT", async () => {
    mockCocheras([cochera()]);
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await user.click(screen.getByRole("button", { name: /^editar$/i }));
    await user.selectOptions(document.getElementById("edit-estado"), "DESHABILITADA");
    await user.click(screen.getByRole("button", { name: /guardar cambios/i }));

    expect(window.confirm).toHaveBeenCalled();
    expect(putMock).not.toHaveBeenCalled();
  });

  it("al deshabilitar y confirmar, llama a PUT con el nuevo estado", async () => {
    mockCocheras([cochera()]);
    putMock.mockResolvedValue({ data: cochera({ estado: "DESHABILITADA" }) });
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await user.click(screen.getByRole("button", { name: /^editar$/i }));
    await user.selectOptions(document.getElementById("edit-estado"), "DESHABILITADA");
    await user.click(screen.getByRole("button", { name: /guardar cambios/i }));

    await waitFor(() =>
      expect(putMock).toHaveBeenCalledWith(
        "/api/v1/cocheras/1",
        expect.objectContaining({ estado: "DESHABILITADA" })
      )
    );
  });

  it("eliminar pide confirmacion, y si se cancela no llama a DELETE", async () => {
    mockCocheras([cochera()]);
    vi.spyOn(window, "confirm").mockReturnValue(false);
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await user.click(screen.getByRole("button", { name: /eliminar/i }));

    expect(window.confirm).toHaveBeenCalledWith("¿Seguro que querés eliminar la cochera A-01?");
    expect(deleteMock).not.toHaveBeenCalled();
  });

  it("eliminar, si se confirma, llama a DELETE y refresca la lista", async () => {
    mockCocheras([cochera()]);
    deleteMock.mockResolvedValue({});
    vi.spyOn(window, "confirm").mockReturnValue(true);
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await user.click(screen.getByRole("button", { name: /eliminar/i }));

    await waitFor(() => expect(deleteMock).toHaveBeenCalledWith("/api/v1/cocheras/1"));
    expect(toastSuccessMock).toHaveBeenCalledWith("Cochera eliminada correctamente");
  });

    // ---- Dropdown de sector en el alta ----

  it("el alta usa un dropdown de sector con las opciones reales del backend", async () => {
    mockCocheras([cochera({ sector: "Planta Baja" }), cochera({ id: "2", numero: "A-02", sector: "Subsuelo" })]);
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    const selectSector = screen.getByLabelText("Sector");
    expect(selectSector.tagName).toBe("SELECT");
    const opciones = Array.from(selectSector.querySelectorAll("option")).map((o) => o.textContent);
    expect(opciones).toEqual(["Seleccioná un sector", "Planta Baja", "Subsuelo", "+ Otro (sector nuevo)"]);
  });

  it("elegir '+ Otro' en el alta muestra un input de texto libre para el sector nuevo", async () => {
    mockCocheras([cochera({ sector: "Planta Baja" })]);
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await user.selectOptions(screen.getByLabelText("Sector"), "+ Otro (sector nuevo)");

    const inputSectorNuevo = screen.getByPlaceholderText("Nombre del sector nuevo");
    expect(inputSectorNuevo).toBeInTheDocument();

    await user.type(inputSectorNuevo, "Playa Externa");
await user.type(screen.getByLabelText(/^número$/i), "PE-01");
    await user.click(screen.getByRole("button", { name: /crear cochera/i }));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith(
        "/api/v1/cocheras",
        expect.objectContaining({ sector: "Playa Externa" })
      )
    );
  });

  it("sin ningun sector cargado todavia, el alta (y el alta por planta) arrancan directo en modo texto libre", async () => {
    mockCocheras([], [], []);
    render(<CocherasManagement />);

    await waitFor(() =>
      expect(screen.getAllByPlaceholderText("Nombre del sector nuevo")).toHaveLength(2)
    );
    expect(screen.getByLabelText("Sector").tagName).toBe("INPUT");
    expect(screen.getByLabelText("Sector de la planta").tagName).toBe("INPUT");
  });

  // ---- Alta por planta ----

  const cantidad = (tipo) => screen.getByLabelText(tipo, { selector: "input" });

  async function cargarCantidad(user, tipo, valor) {
    await user.clear(cantidad(tipo));
    await user.type(cantidad(tipo), String(valor));
  }

  it("las cuatro cantidades arrancan en 0", async () => {
    mockCocheras([cochera()]);
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    for (const tipo of ["Auto", "Moto", "Accesible", "Carga"]) {
      expect(cantidad(tipo)).toHaveValue(0);
      expect(cantidad(tipo)).toHaveAttribute("min", "0");
    }
  });

  it("con todas las cantidades en 0, el boton de crear queda deshabilitado", async () => {
    mockCocheras([cochera()]);
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    expect(screen.getByRole("button", { name: /indicá cuántas crear/i })).toBeDisabled();
  });

  it("al cargar alguna cantidad se habilita y muestra el total a crear", async () => {
    mockCocheras([cochera()]);
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await cargarCantidad(user, "Auto", 3);
    await cargarCantidad(user, "Moto", 2);

    expect(screen.getByRole("button", { name: "Crear 5 cocheras" })).toBeEnabled();
  });

  it("el sector del alta por planta usa el mismo dropdown de sectores reales, con opcion de uno nuevo", async () => {
    mockCocheras([cochera({ sector: "Planta Baja" }), cochera({ id: "2", numero: "A-02", sector: "Subsuelo" })]);
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    const select = screen.getByLabelText("Sector de la planta");
    expect(select.tagName).toBe("SELECT");
    const opciones = Array.from(select.querySelectorAll("option")).map((o) => o.textContent);
    expect(opciones).toEqual(["Seleccioná un sector", "Planta Baja", "Subsuelo", "+ Otro (sector nuevo)"]);
  });

  it("envia sector y cantidades por tipo (sin los que quedaron en 0) a POST alta-por-planta", async () => {
    mockCocheras([cochera({ sector: "Planta Baja" })]);
    postMock.mockResolvedValue({ data: [] });
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await user.selectOptions(screen.getByLabelText("Sector de la planta"), "Planta Baja");
    await cargarCantidad(user, "Auto", 3);
    await cargarCantidad(user, "Accesible", 1);
    await user.click(screen.getByRole("button", { name: "Crear 4 cocheras" }));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith("/api/v1/cocheras/alta-por-planta", {
        sector: "Planta Baja",
        cantidades: { AUTO: 3, ACCESIBLE: 1 },
      })
    );
  });

  it("permite crear la planta en un sector nuevo con '+ Otro'", async () => {
    mockCocheras([cochera({ sector: "Planta Baja" })]);
    postMock.mockResolvedValue({ data: [] });
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await user.selectOptions(screen.getByLabelText("Sector de la planta"), "+ Otro (sector nuevo)");
    await user.type(screen.getByLabelText("Sector de la planta"), "Primer Piso");
    await cargarCantidad(user, "Moto", 2);
    await user.click(screen.getByRole("button", { name: "Crear 2 cocheras" }));

    await waitFor(() =>
      expect(postMock).toHaveBeenCalledWith("/api/v1/cocheras/alta-por-planta", {
        sector: "Primer Piso",
        cantidades: { MOTO: 2 },
      })
    );
  });

  it("muestra los numeros que asigno el backend, agrupados por tipo", async () => {
    mockCocheras([cochera({ sector: "Planta Baja" })]);
    postMock.mockResolvedValue({
      data: [
        cochera({ id: "n1", numero: "A-05", tipo: "AUTO" }),
        cochera({ id: "n2", numero: "A-06", tipo: "AUTO" }),
        cochera({ id: "n3", numero: "M-03", tipo: "MOTO" }),
      ],
    });
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await user.selectOptions(screen.getByLabelText("Sector de la planta"), "Planta Baja");
    await cargarCantidad(user, "Auto", 2);
    await cargarCantidad(user, "Moto", 1);
    await user.click(screen.getByRole("button", { name: "Crear 3 cocheras" }));

    const resultado = await screen.findByRole("status");
    expect(resultado).toHaveTextContent("Se crearon 3 cocheras en Planta Baja");
    expect(resultado).toHaveTextContent("Auto: A-05, A-06");
    expect(resultado).toHaveTextContent("Moto: M-03");
    expect(toastSuccessMock).toHaveBeenCalledWith("Se crearon 3 cocheras");
  });

  it("despues de crear, vuelve las cantidades a 0 y refresca la lista", async () => {
    mockCocheras([cochera({ sector: "Planta Baja" })]);
    postMock.mockResolvedValue({ data: [cochera({ id: "n1", numero: "A-02" })] });
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");
    const pedidosAntes = getMock.mock.calls.filter((c) => c[0] === "/api/v1/cocheras").length;

    await user.selectOptions(screen.getByLabelText("Sector de la planta"), "Planta Baja");
    await cargarCantidad(user, "Auto", 1);
    await user.click(screen.getByRole("button", { name: "Crear 1 cochera" }));

    await screen.findByRole("status");
    expect(cantidad("Auto")).toHaveValue(0);
    const pedidosDespues = getMock.mock.calls.filter((c) => c[0] === "/api/v1/cocheras").length;
    expect(pedidosDespues).toBeGreaterThan(pedidosAntes);
  });

  it("si el backend rechaza el pedido, muestra su mensaje y no borra lo cargado", async () => {
    mockCocheras([cochera({ sector: "Planta Baja" })]);
    postMock.mockRejectedValue({
      response: { data: { message: "Indica al menos un tipo de cochera con cantidad mayor a 0." } },
    });
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await user.selectOptions(screen.getByLabelText("Sector de la planta"), "Planta Baja");
    await cargarCantidad(user, "Carga", 2);
    await user.click(screen.getByRole("button", { name: "Crear 2 cocheras" }));

    await waitFor(() =>
      expect(toastErrorMock).toHaveBeenCalledWith(
        "Indica al menos un tipo de cochera con cantidad mayor a 0."
      )
    );
    expect(cantidad("Carga")).toHaveValue(2);
    expect(screen.queryByRole("status")).not.toBeInTheDocument();
  });

  it("exige elegir un sector antes de enviar", async () => {
    mockCocheras([cochera({ sector: "Planta Baja" })]);
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await cargarCantidad(user, "Auto", 2);
    await user.click(screen.getByRole("button", { name: "Crear 2 cocheras" }));

    expect(await screen.findAllByText("El sector es obligatorio")).not.toHaveLength(0);
    expect(postMock).not.toHaveBeenCalled();
  });

  it("no envia una cantidad negativa", async () => {
    mockCocheras([cochera({ sector: "Planta Baja" })]);
    const user = userEvent.setup();
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    await user.selectOptions(screen.getByLabelText("Sector de la planta"), "Planta Baja");
    await cargarCantidad(user, "Auto", 2);
    // jsdom no escribe bien el "-" tecleado en un input numérico: se setea
    // el valor directo, que es lo mismo que le llega al formulario.
    fireEvent.change(cantidad("Moto"), { target: { value: "-1" } });
    await user.click(screen.getByRole("button", { name: "Crear 2 cocheras" }));

    // El input tiene min=0: el navegador frena el envío con su propio aviso
    // antes de que llegue al schema. Lo que importa es que no viaja.
    expect(cantidad("Moto")).toBeInvalid();
    await new Promise((r) => setTimeout(r, 50));
    expect(postMock).not.toHaveBeenCalled();
  });

  it("ya no ofrece la carga fila por fila ni llama al endpoint /bulk", async () => {
    mockCocheras([cochera()]);
    render(<CocherasManagement />);
    await screen.findByText("A-01");

    expect(screen.queryByRole("button", { name: /agregar fila/i })).not.toBeInTheDocument();
    expect(screen.queryAllByLabelText(/^Número fila/)).toHaveLength(0);
  });
});