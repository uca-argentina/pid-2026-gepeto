import { describe, it, expect, vi } from "vitest";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

import AtajosJornada from "@/components/AtajosJornada";
import {
  MINUTOS_JORNADA_COMPLETA,
  MINUTOS_MEDIA_JORNADA,
} from "@/utils/franjaHoraria";

/**
 * Los atajos de jornada.
 *
 * Lo que importa acá no es que sumen 12 o 24 horas cualesquiera, sino que caigan
 * **justo** en los umbrales con los que el backend clasifica la modalidad: un
 * atajo que dejara la reserva en 11 h 45 la etiquetaría como franja horaria y
 * el botón estaría mintiendo.
 */
const DESDE = "2026-10-01T08:00";

describe("AtajosJornada", () => {
  it("ofrece media jornada y jornada completa con su duracion", () => {
    render(<AtajosJornada desde={DESDE} hasta="2026-10-01T09:00" onChange={vi.fn()} />);

    expect(screen.getByRole("button", { name: /media jornada/i })).toHaveTextContent("12 h");
    expect(screen.getByRole("button", { name: /jornada completa/i })).toHaveTextContent("24 h");
  });

  it("media jornada deja el fin doce horas despues del inicio", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<AtajosJornada desde={DESDE} hasta="2026-10-01T09:00" onChange={onChange} />);

    await user.click(screen.getByRole("button", { name: /media jornada/i }));

    expect(onChange).toHaveBeenCalledWith("2026-10-01T20:00");
  });

  // Jornada completa cruza al día siguiente, que es justamente el caso de
  // "dejo el auto y lo busco mañana".
  it("jornada completa deja el fin al dia siguiente a la misma hora", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<AtajosJornada desde={DESDE} hasta="2026-10-01T09:00" onChange={onChange} />);

    await user.click(screen.getByRole("button", { name: /jornada completa/i }));

    expect(onChange).toHaveBeenCalledWith("2026-10-02T08:00");
  });

  // El atajo tiene que caer en el umbral exacto, no cerca: si no, el backend
  // clasificaría la reserva con otra modalidad que la que dice el botón.
  it("los atajos caen justo en los umbrales que usa el backend", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<AtajosJornada desde={DESDE} hasta="2026-10-01T09:00" onChange={onChange} />);

    await user.click(screen.getByRole("button", { name: /media jornada/i }));
    const media = (new Date(onChange.mock.lastCall[0]) - new Date(DESDE)) / 60000;
    expect(media).toBe(MINUTOS_MEDIA_JORNADA);

    await user.click(screen.getByRole("button", { name: /jornada completa/i }));
    const completa = (new Date(onChange.mock.lastCall[0]) - new Date(DESDE)) / 60000;
    expect(completa).toBe(MINUTOS_JORNADA_COMPLETA);
  });

  it("marca como activo el atajo que coincide con la franja actual", () => {
    render(<AtajosJornada desde={DESDE} hasta="2026-10-01T20:00" onChange={vi.fn()} />);

    expect(screen.getByRole("button", { name: /media jornada/i })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: /jornada completa/i })).toHaveAttribute("aria-pressed", "false");
  });

  it("ninguno queda activo con una franja que no es una jornada", () => {
    render(<AtajosJornada desde={DESDE} hasta="2026-10-01T11:00" onChange={vi.fn()} />);

    screen.getAllByRole("button").forEach((b) => {
      expect(b).toHaveAttribute("aria-pressed", "false");
    });
  });

  // Sin inicio no hay a qué sumarle las horas.
  it("quedan deshabilitados si todavia no hay inicio", () => {
    render(<AtajosJornada desde="" hasta="" onChange={vi.fn()} />);

    screen.getAllByRole("button").forEach((b) => expect(b).toBeDisabled());
  });

  // Los atajos parten de un inicio que ya cae en bloque y suman múltiplos de
  // 15, así que el resultado también cae en bloque.
  it("el resultado sigue cayendo en un bloque de 15 minutos", async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    render(<AtajosJornada desde="2026-10-01T08:45" hasta="2026-10-01T09:00" onChange={onChange} />);

    await user.click(screen.getByRole("button", { name: /jornada completa/i }));

    expect(onChange).toHaveBeenCalledWith("2026-10-02T08:45");
  });
});
