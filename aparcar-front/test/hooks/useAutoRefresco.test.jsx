import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useAutoRefresco } from "@/hooks/useAutoRefresco";

/** Fuerza el estado de visibilidad de la pestaña y avisa a quien lo escuche. */
const verPestana = (estado) => {
  Object.defineProperty(document, "visibilityState", {
    value: estado,
    configurable: true,
  });
  document.dispatchEvent(new Event("visibilitychange"));
};

beforeEach(() => {
  vi.useFakeTimers();
  verPestana("visible");
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe("useAutoRefresco", () => {
  it("no consulta al montar: la carga inicial es responsabilidad de la pantalla", () => {
    const refrescar = vi.fn();
    renderHook(() => useAutoRefresco(refrescar, { intervalo: 1000 }));

    expect(refrescar).not.toHaveBeenCalled();
  });

  it("consulta una vez por intervalo", async () => {
    const refrescar = vi.fn();
    renderHook(() => useAutoRefresco(refrescar, { intervalo: 1000 }));

    // Hay que dejar resolver la promesa entre turno y turno. Si se adelantara
    // el reloj 3000 de una sola vez, el guard contra consultas superpuestas
    // saltearia los dos turnos siguientes, porque la primera todavia figuraria
    // en vuelo: el `await` del hook no llego a completarse.
    for (let i = 0; i < 3; i += 1) {
      await act(async () => {
        vi.advanceTimersByTime(1000);
      });
    }

    expect(refrescar).toHaveBeenCalledTimes(3);
  });

  it("no consulta con la pestaña oculta", () => {
    const refrescar = vi.fn();
    renderHook(() => useAutoRefresco(refrescar, { intervalo: 1000 }));

    verPestana("hidden");
    act(() => vi.advanceTimersByTime(5000));

    expect(refrescar).not.toHaveBeenCalled();
  });

  it("consulta apenas se vuelve a la pestaña, sin esperar al próximo turno", () => {
    const refrescar = vi.fn();
    renderHook(() => useAutoRefresco(refrescar, { intervalo: 60000 }));

    verPestana("hidden");
    act(() => vi.advanceTimersByTime(5000));
    expect(refrescar).not.toHaveBeenCalled();

    act(() => verPestana("visible"));

    expect(refrescar).toHaveBeenCalledTimes(1);
  });

  it("no superpone consultas: con una lenta, el turno siguiente se saltea", async () => {
    let resolver;
    const refrescar = vi.fn(() => new Promise((r) => { resolver = r; }));
    renderHook(() => useAutoRefresco(refrescar, { intervalo: 1000 }));

    act(() => vi.advanceTimersByTime(1000));
    expect(refrescar).toHaveBeenCalledTimes(1);

    // Pasan dos turnos más mientras la primera sigue en vuelo.
    act(() => vi.advanceTimersByTime(2000));
    expect(refrescar).toHaveBeenCalledTimes(1);

    // Al terminar, el siguiente turno vuelve a consultar.
    await act(async () => {
      resolver();
    });
    act(() => vi.advanceTimersByTime(1000));
    expect(refrescar).toHaveBeenCalledTimes(2);
  });

  it("un error no corta el refresco: el turno siguiente reintenta", async () => {
    const refrescar = vi.fn()
      .mockRejectedValueOnce(new Error("se cayó la red"))
      .mockResolvedValue(undefined);
    renderHook(() => useAutoRefresco(refrescar, { intervalo: 1000 }));

    await act(async () => {
      vi.advanceTimersByTime(1000);
    });
    await act(async () => {
      vi.advanceTimersByTime(1000);
    });

    expect(refrescar).toHaveBeenCalledTimes(2);
  });

  it("con activo en false no consulta nada", () => {
    const refrescar = vi.fn();
    renderHook(() => useAutoRefresco(refrescar, { intervalo: 1000, activo: false }));

    act(() => vi.advanceTimersByTime(5000));
    act(() => verPestana("visible"));

    expect(refrescar).not.toHaveBeenCalled();
  });

  it("deja de consultar al desmontar", () => {
    const refrescar = vi.fn();
    const { unmount } = renderHook(() => useAutoRefresco(refrescar, { intervalo: 1000 }));

    act(() => vi.advanceTimersByTime(1000));
    expect(refrescar).toHaveBeenCalledTimes(1);

    unmount();
    act(() => vi.advanceTimersByTime(5000));

    expect(refrescar).toHaveBeenCalledTimes(1);
  });

  it("usa siempre la última función recibida, sin reiniciar el intervalo", () => {
    const primera = vi.fn();
    const segunda = vi.fn();
    const { rerender } = renderHook(({ fn }) => useAutoRefresco(fn, { intervalo: 1000 }), {
      initialProps: { fn: primera },
    });

    // Un render a mitad de camino no tiene que reiniciar la cuenta: si lo
    // hiciera, el intervalo nunca llegaría a cumplirse en una pantalla que
    // redibuja seguido.
    act(() => vi.advanceTimersByTime(600));
    rerender({ fn: segunda });
    act(() => vi.advanceTimersByTime(400));

    expect(primera).not.toHaveBeenCalled();
    expect(segunda).toHaveBeenCalledTimes(1);
  });
});
