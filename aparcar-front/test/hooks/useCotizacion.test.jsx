import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, expect, it, vi } from "vitest";
const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }));
vi.mock("@/app/api", () => ({ default: { get, post } }));
import useCotizacion from "@/hooks/useCotizacion";
const desde = "2099-01-01T08:00";
const hasta = "2099-01-01T09:00";
beforeEach(() => vi.resetAllMocks());

it("no cotiza sin tipo o con rango inválido", () => {
  const { result, rerender } = renderHook(({ tipo, fin }) => useCotizacion(tipo, desde, fin), { initialProps: { tipo: undefined, fin: hasta } });
  rerender({ tipo: "AUTO", fin: desde });
  expect(get).not.toHaveBeenCalled();
  expect(result.current.lista).toBe(false);
});

it("invalida inmediatamente el precio previo e ignora respuestas tardías", async () => {
  const pendientes = [];
  get.mockImplementation(() => new Promise((resolve) => pendientes.push(resolve)));
  const { result, rerender } = renderHook(({ tipo }) => useCotizacion(tipo, desde, hasta), { initialProps: { tipo: "AUTO" } });
  rerender({ tipo: "MOTO" });
  await act(async () => pendientes[1]({ data: { total: 500, tipo: "MOTO" } }));
  expect(result.current.datos.total).toBe(500);
  await act(async () => pendientes[0]({ data: { total: 1000, tipo: "AUTO" } }));
  expect(result.current.datos.tipo).toBe("MOTO");
  rerender({ tipo: "ACCESIBLE" });
  expect(result.current.lista).toBe(false);
  expect(result.current.cargando).toBe(true);
  rerender({ tipo: "MOTO" });
  expect(result.current.lista).toBe(false);
});

it("muestra el error del backend y permite reintentar incluso un precio cero", async () => {
  get.mockRejectedValueOnce({ response: { data: { message: "Tarifa sin configurar" } } }).mockResolvedValue({ data: { total: 0 } });
  const { result } = renderHook(() => useCotizacion("AUTO", desde, hasta));
  await waitFor(() => expect(result.current.error).toBe("Tarifa sin configurar"));
  expect(result.current.lista).toBe(false);
  act(() => result.current.recargar());
  await waitFor(() => expect(result.current.lista).toBe(true));
  expect(result.current.datos.total).toBe(0);
});

it("descarta una respuesta que no incluye un precio válido", async () => {
  get.mockResolvedValue({ data: {} });
  const { result } = renderHook(() => useCotizacion("AUTO", desde, hasta));
  await waitFor(() => expect(result.current.error).toMatch(/No se pudo calcular/));
  expect(result.current.lista).toBe(false);
});

it("cambiar la franja vuelve a consultar el importe", async () => {
  get.mockResolvedValue({ data: { total: 1000 } });
  const { result, rerender } = renderHook(({ fin }) => useCotizacion("AUTO", desde, fin), { initialProps: { fin: hasta } });
  await waitFor(() => expect(result.current.lista).toBe(true));
  rerender({ fin: "2099-01-02T08:00" });
  expect(result.current.lista).toBe(false);
  await waitFor(() => expect(get).toHaveBeenLastCalledWith("/api/v1/tarifas/cotizacion", {
    params: { tipo: "AUTO", desde, hasta: "2099-01-02T08:00" },
  }));
});

it("con un canje pide el precio con puntos, o con todos los disponibles", async () => {
  get.mockResolvedValue({ data: { total: 900 } });
  const { rerender } = renderHook(({ canje }) => useCotizacion("AUTO", desde, hasta, canje), { initialProps: { canje: { puntos: "100" } } });
  await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/tarifas/cotizacion",
    { params: { tipo: "AUTO", desde, hasta, puntos: "100" } }));
  rerender({ canje: { todos: true } });
  await waitFor(() => expect(get).toHaveBeenLastCalledWith("/api/v1/tarifas/cotizacion",
    { params: { tipo: "AUTO", desde, hasta, usarTodos: true } }));
});

it("sin canje no manda parámetros de puntos", async () => {
  get.mockResolvedValue({ data: { total: 1000 } });
  renderHook(() => useCotizacion("AUTO", desde, hasta));
  await waitFor(() => expect(get).toHaveBeenCalledWith("/api/v1/tarifas/cotizacion", { params: { tipo: "AUTO", desde, hasta } }));
});

it("cotiza credenciales en el cuerpo y descarta el precio al cambiar el visitante", async () => {
  const pendientes = [];
  post.mockImplementation(() => new Promise((resolve) => pendientes.push(resolve)));
  const credencialesConvenio = [{ convenioId: "uca", credencial: "UCA-123" }];
  const { result, rerender } = renderHook(({ visitanteId }) => useCotizacion("AUTO", desde, hasta,
    { puntos: "100" }, { visitanteId, credencialesConvenio }), { initialProps: { visitanteId: "v1" } });
  expect(post).toHaveBeenCalledWith("/api/v1/tarifas/cotizacion", {
    tipo: "AUTO", desde, hasta, puntos: "100", visitanteId: "v1", credencialesConvenio,
  });
  await act(async () => pendientes[0]({ data: { total: 850 } }));
  expect(result.current.lista).toBe(true);
  rerender({ visitanteId: "v2" });
  expect(result.current.lista).toBe(false);
  expect(get).not.toHaveBeenCalled();
});
