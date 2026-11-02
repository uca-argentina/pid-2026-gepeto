"use client";

import { useId } from "react";
import { formatearPorcentaje } from "@/utils/puntos";

export default function ConveniosCredenciales({ modelo, guardar = false }) {
  const id = useId();
  const { convenios, campos, habilitado, cargando, error, cambiar, verificar, borrar, recargar } = modelo;
  if (!habilitado) return null;
  if (cargando) return <p className="text-sm text-ink/60" role="status">Cargando convenios…</p>;
  if (error) return <div className="text-sm text-ink/60">
    <p>{error}</p>
    <button type="button" className="mt-1 font-semibold text-link underline" onClick={recargar}>Reintentar convenios</button>
  </div>;
  if (!convenios.length) return guardar ? <p className="text-sm text-ink/60">No hay convenios disponibles.</p> : null;

  return <fieldset className="min-w-0 space-y-4 rounded-xl border border-accent/25 p-4">
    <legend className="px-1 text-sm font-semibold text-ink">Descuentos por convenios</legend>
    <p className="text-xs text-ink/60">Se aplica el mayor descuento disponible entre convenios y los puntos que elijas usar. No se acumulan.</p>
    {convenios.map((convenio) => {
      const campo = campos[convenio.id] ?? {};
      const campoId = `${id}-${convenio.id}`;
      return <div key={convenio.id} className="min-w-0">
        <label htmlFor={campoId} className="ui-label">Credencial {convenio.nombre}</label>
        <p id={`${campoId}-ayuda`} className="mb-2 text-xs text-ink/60">
          Ingresá tu credencial para obtener un {formatearPorcentaje(convenio.descuentoPct)} de descuento.
          {" "}Formato: {convenio.formatoCredencial}; XXX son los últimos 3 dígitos del documento.
        </p>
        <div className="flex min-w-0 flex-col gap-2 sm:flex-row sm:items-start">
          <input id={campoId} className="ui-input min-w-0 flex-1 uppercase" autoComplete="off" maxLength={100}
            value={campo.texto ?? ""} readOnly={campo.guardada} disabled={campo.ocupada}
            placeholder={convenio.formatoCredencial} aria-invalid={Boolean(campo.error)}
            aria-describedby={`${campoId}-ayuda${campo.error ? ` ${campoId}-error` : ""}`}
            onChange={(event) => cambiar(convenio.id, event.target.value)} />
          {!campo.validada && <button type="button" className="profile-secondary shrink-0"
            disabled={!campo.texto?.trim() || campo.ocupada || !modelo.puedeVerificar}
            onClick={() => verificar(convenio.id)}>
            {campo.ocupada ? "Verificando…" : guardar ? "Verificar y guardar" : "Verificar"}
          </button>}
          {(campo.guardada ? guardar : campo.validada) && <button type="button" className="profile-secondary shrink-0"
            disabled={campo.ocupada} onClick={() => borrar(convenio.id)}>
            {campo.ocupada ? "Eliminando…" : campo.guardada ? "Eliminar" : "Quitar"}
          </button>}
        </div>
        {campo.validada && <p className="mt-1 text-xs font-medium text-link" role="status">
          Credencial validada{campo.guardada ? " y guardada en Mis datos" : " para esta reserva"}.
        </p>}
        {!modelo.puedeVerificar && <p className="mt-1 text-xs text-ink/60">Ingresá primero el documento del visitante.</p>}
        {campo.error && <p id={`${campoId}-error`} className="mt-1 text-sm text-red-600 dark:text-red-300" role="alert">{campo.error}</p>}
      </div>;
    })}
  </fieldset>;
}
