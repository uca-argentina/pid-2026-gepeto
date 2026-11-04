"use client";

import { useId } from "react";

/**
 * "Usar puntos" en el alta de una reserva. Solo junta lo que quiere usar el
 * visitante: el descuento y el precio final los calcula el servidor y se ven
 * en la cotización.
 */
export default function UsarPuntos({ saldo, maximo, texto, todos, error, onCambiar }) {
  const id = useId();
  const activo = todos || texto !== "";

  if (!saldo) {
    return (
      <div className="puntos-canje" role="group" aria-labelledby={`${id}-titulo`}>
        <p id={`${id}-titulo`} className="puntos-canje-titulo">Usar puntos</p>
        <p className="puntos-canje-ayuda">
          Todavía no tenés puntos para usar. Los sumás cada vez que ingresás con una reserva.
        </p>
      </div>
    );
  }

  const ayuda = `${id}-ayuda`;
  const errorId = `${id}-error`;
  return (
    <fieldset className="puntos-canje">
      <legend className="puntos-canje-titulo">Usar puntos</legend>
      <p id={ayuda} className="puntos-canje-ayuda">
        Tenés <strong>{saldo}</strong> {saldo === 1 ? "punto" : "puntos"}. Cada 100 puntos son un 10 % de
        descuento; en esta reserva podés usar hasta {maximo}.
      </p>
      <div className="puntos-canje-fila">
        <div className="puntos-canje-campo">
          <label htmlFor={`${id}-cantidad`}>Puntos a usar</label>
          <input
            id={`${id}-cantidad`}
            type="text"
            inputMode="numeric"
            autoComplete="off"
            className="ui-input"
            placeholder="0"
            value={todos ? String(maximo) : texto}
            disabled={todos}
            aria-invalid={Boolean(error)}
            aria-describedby={`${ayuda}${error ? ` ${errorId}` : ""}`}
            onChange={(e) => onCambiar({ texto: e.target.value, todos: false })}
          />
        </div>
        <button
          type="button"
          className="profile-secondary"
          aria-pressed={todos}
          onClick={() => onCambiar({ texto: "", todos: !todos })}
        >
          Usar todos
        </button>
        {activo && (
          <button type="button" className="puntos-canje-quitar" onClick={() => onCambiar({ texto: "", todos: false })}>
            No usar puntos
          </button>
        )}
      </div>
      {error && <p id={errorId} role="alert" className="puntos-canje-error">{error}</p>}
    </fieldset>
  );
}
