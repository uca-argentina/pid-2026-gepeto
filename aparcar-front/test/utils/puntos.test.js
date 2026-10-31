import { describe, expect, it } from "vitest";
import {
  formatearPorcentaje, formatearPuntos, textosPuntosReserva, validarPuntos,
} from "@/utils/puntos";

describe("validarPuntos", () => {
  const limites = { saldo: 300, maximo: 300 };

  it("acepta un entero positivo dentro del saldo y del tope", () => {
    expect(validarPuntos("100", limites)).toBeNull();
    expect(validarPuntos(" 300 ", limites)).toBeNull();
  });

  it.each([
    ["", "Indicá cuántos puntos querés usar."],
    ["abc", "Escribí un número entero de puntos."],
    ["1e3", "Escribí un número entero de puntos."],
    ["1.5", "Los puntos se usan enteros, sin decimales."],
    ["2,5", "Los puntos se usan enteros, sin decimales."],
    ["0", "Indicá una cantidad de puntos mayor a cero."],
    ["-10", "Indicá una cantidad de puntos mayor a cero."],
    ["301", "No tenés puntos suficientes: tu saldo es de 300."],
  ])("rechaza %j", (texto, mensaje) => {
    expect(validarPuntos(texto, limites)).toBe(mensaje);
  });

  it("con saldo de sobra, el límite es el tope de descuento", () => {
    expect(validarPuntos("600", { saldo: 800, maximo: 500 })).toBe("Podés usar hasta 500 puntos en esta reserva.");
  });
});

describe("formatos", () => {
  it("porcentaje y puntos con signo", () => {
    expect(formatearPorcentaje(10)).toBe("10 %");
    expect(formatearPorcentaje("12.5")).toBe("12,5 %");
    expect(formatearPuntos(20)).toBe("+20");
    expect(formatearPuntos(-100)).toBe("−100");
    expect(formatearPuntos(0)).toBe("0");
  });
});

describe("textosPuntosReserva", () => {
  const base = { puntosUsados: 0, descuentoPct: null, reintegrados: false, puntosAcreditados: null, puntosAGanar: 15, motivoSinPuntos: null };

  it("una reserva pendiente dice lo que va a sumar", () => {
    expect(textosPuntosReserva({ estado: "CONFIRMADA", puntos: base })).toEqual(["Suma 15 puntos al ingresar."]);
  });

  it("con descuento dice lo que usó, y lo que sumó al ingresar", () => {
    expect(textosPuntosReserva({ estado: "FINALIZADA", puntos: {
      ...base, puntosUsados: 100, descuentoPct: 10, puntosAcreditados: 15, puntosAGanar: 0,
    } })).toEqual(["Usaste 100 puntos: 10 % de descuento.", "Sumaste 15 puntos al ingresar."]);
  });

  it("cancelada: avisa el reintegro; vencida: que no se devuelven", () => {
    const usados = { ...base, puntosUsados: 100, descuentoPct: 10, puntosAGanar: 0, motivoSinPuntos: "La reserva no se usó: no suma puntos." };
    expect(textosPuntosReserva({ estado: "CANCELADA", puntos: { ...usados, reintegrados: true } }))
      .toEqual(["Usaste 100 puntos: 10 % de descuento.", "Te devolvimos los 100 puntos al cancelarla."]);
    expect(textosPuntosReserva({ estado: "VENCIDA", puntos: usados }))
      .toEqual(["Usaste 100 puntos: 10 % de descuento.", "Venció sin ingreso: los puntos usados no se devuelven."]);
  });

  it("si no sumó al ingresar, muestra el motivo", () => {
    expect(textosPuntosReserva({ estado: "EN_CURSO", puntos: {
      ...base, puntosAcreditados: 0, puntosAGanar: 0, motivoSinPuntos: "Ya sumaste puntos por 2 reservas ese día: esta no suma.",
    } })).toEqual(["Ya sumaste puntos por 2 reservas ese día: esta no suma."]);
  });

  it("sin datos de puntos no dice nada", () => {
    expect(textosPuntosReserva({ estado: "CONFIRMADA" })).toEqual([]);
  });
});
