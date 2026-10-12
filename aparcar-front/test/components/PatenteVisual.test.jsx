import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import PatenteVisual from "@/components/PatenteVisual";

const renglones = (chapa) => [...chapa.querySelectorAll(".patente-renglon")].map((r) => r.textContent);

describe("PatenteVisual", () => {
  it.each([
    // patente, tipo, diseño, renglones dibujados
    ["AB123CD", "AUTO", "auto-mercosur", ["AB 123 CD"]],
    ["ABC123", "AUTO", "auto-anterior", ["ABC 123"]],
    ["A123BCD", "MOTO", "moto-mercosur", ["A12", "3BCD"]],
    ["123ABC", "MOTO", "moto-anterior", ["123", "ABC"]],
  ])("%s (%s) se dibuja con el diseño %s", (patente, tipo, diseno, esperados) => {
    render(<PatenteVisual patente={patente} tipo={tipo} />);

    const chapa = screen.getByRole("img", { name: `Patente ${patente}` });
    expect(chapa).toHaveAttribute("data-formato", diseno);
    expect(chapa).toHaveClass(`patente--${diseno}`);
    expect(renglones(chapa)).toEqual(esperados);
  });

  it("las Mercosur llevan la banda con el pais", () => {
    render(<PatenteVisual patente="AB123CD" tipo="AUTO" />);

    expect(screen.getByText("REPÚBLICA ARGENTINA")).toBeInTheDocument();
    expect(screen.queryByText("ARGENTINA")).not.toBeInTheDocument();
  });

  it("las anteriores llevan el rotulo ARGENTINA y no la banda Mercosur", () => {
    render(<PatenteVisual patente="123ABC" tipo="MOTO" />);

    expect(screen.getByText("ARGENTINA")).toBeInTheDocument();
    expect(screen.queryByText("REPÚBLICA ARGENTINA")).not.toBeInTheDocument();
  });

  it("el nombre accesible usa la patente compacta y en mayusculas", () => {
    render(<PatenteVisual patente="ab 123 cd" tipo="CARGA" />);

    expect(screen.getByRole("img", { name: "Patente AB123CD" })).toHaveAttribute("data-formato", "auto-mercosur");
  });

  it("una CARGA usa los mismos diseños que un auto", () => {
    render(<PatenteVisual patente="ABC123" tipo="CARGA" />);

    expect(screen.getByRole("img", { name: "Patente ABC123" })).toHaveAttribute("data-formato", "auto-anterior");
  });

  it("si la patente no coincide con ningun formato, la muestra igual en una chapa neutra", () => {
    render(<PatenteVisual patente="XYZ9" tipo="AUTO" />);

    const chapa = screen.getByRole("img", { name: "Patente XYZ9" });
    expect(chapa).toHaveAttribute("data-formato", "generica");
    expect(chapa).not.toHaveClass("patente--moto");
    expect(renglones(chapa)).toEqual(["XYZ9"]);
  });

  it("la chapa neutra de una moto toma la forma de moto", () => {
    render(<PatenteVisual patente="XY1" tipo="MOTO" />);

    expect(screen.getByRole("img", { name: "Patente XY1" })).toHaveClass("patente--generica", "patente--moto");
  });

  it("sin patente no rompe", () => {
    render(<PatenteVisual patente={undefined} />);

    expect(screen.getByRole("img", { name: "Patente" })).toHaveAttribute("data-formato", "generica");
  });

  it("acepta un tamaño chico", () => {
    render(<PatenteVisual patente="ABC123" tamano="sm" />);

    expect(screen.getByRole("img", { name: "Patente ABC123" })).toHaveClass("patente--sm");
  });
});