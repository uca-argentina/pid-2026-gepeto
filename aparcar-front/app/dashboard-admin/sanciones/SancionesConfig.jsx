"use client";

import { useEffect, useState } from "react";
import { toast } from "sonner";

import api from "@/app/api";

/** Los cuatro números, con el rango que acepta el backend y qué significan. */
const CAMPOS = [
  {
    nombre: "maxFaltas",
    etiqueta: "Faltas permitidas",
    min: 1,
    max: 50,
    unidad: "faltas",
    ayuda: "Cuántas se toleran en los últimos 3 meses. Al superar este número "
      + "la cuenta queda inhabilitada, así que con 3 bloquea la cuarta.",
  },
  {
    nombre: "diasBloqueo",
    etiqueta: "Duración del bloqueo",
    min: 1,
    max: 3650,
    unidad: "días",
    ayuda: "Cuánto dura la inhabilitación. Al cumplirse, la cuenta se restituye "
      + "sola; no hace falta hacer nada.",
  },
  {
    nombre: "mesesVentana",
    etiqueta: "Ventana de conteo",
    min: 1,
    max: 120,
    unidad: "meses",
    ayuda: "Hacia atrás cuánto se miran las faltas. Las más viejas dejan de "
      + "sumar para el bloqueo, pero siguen en el historial, que es permanente. "
      + "Ojo: alargarla puede bloquear cuentas en el acto, porque vuelve a "
      + "traer al conteo faltas que ya habían salido.",
  },
  {
    nombre: "toleranciaIngresoMin",
    etiqueta: "Tolerancia de ingreso",
    min: 0,
    max: 1440,
    unidad: "minutos",
    ayuda: "Cuánto se espera después del inicio de la reserva antes de aplicar "
      + "una falta automática por no haber registrado el ingreso.",
  },
  {
    nombre: "toleranciaEgresoMin",
    etiqueta: "Tolerancia de egreso",
    min: 0,
    max: 1440,
    unidad: "minutos",
    ayuda: "Lo mismo, contado desde el fin de la reserva, para el egreso.",
  },
];

/**
 * Los números con los que funciona el régimen de faltas.
 *
 * Son datos de configuración, no de operación: cambiarlos altera cómo se
 * comporta el sistema con todo el mundo. Por eso la pantalla es solo del
 * administrador y cada campo explica qué provoca, en vez de dejar cuatro
 * casilleros sueltos que haya que adivinar.
 *
 * Los cambios tienen efecto en la corrida siguiente de la tarea automática,
 * que es cada minuto. No hace falta reiniciar nada.
 */
export default function SancionesConfig() {
  const [valores, setValores] = useState(null);
  const [error, setError] = useState(false);
  const [guardando, setGuardando] = useState(false);

  useEffect(() => {
    let vigente = true;
    api
      .get("/api/v1/amonestaciones/configuracion")
      .then((res) => vigente && setValores(res.data))
      .catch(() => vigente && setError(true));
    return () => {
      vigente = false;
    };
  }, []);

  const guardar = async (e) => {
    e.preventDefault();
    if (guardando) return;
    setGuardando(true);
    try {
      const { data } = await api.put("/api/v1/amonestaciones/configuracion", valores);
      setValores(data);
      toast.success("Configuración guardada.");
    } catch (err) {
      toast.error(err.response?.data?.message ?? "No se pudo guardar la configuración.");
    } finally {
      setGuardando(false);
    }
  };

  if (error) {
    return (
      <main className="mx-auto max-w-3xl">
        <p className="text-sm text-ink/60">No se pudo cargar la configuración.</p>
      </main>
    );
  }

  if (!valores) return null;

  return (
    <main className="mx-auto max-w-3xl">
      <h1 className="text-3xl font-extrabold tracking-tight text-ink mb-2">
        Régimen de faltas
      </h1>
      <p className="text-sm text-ink/60 mb-6">
        Los valores con los que el sistema aplica y acumula las faltas. Aplican a
        todas las cuentas desde que se guardan.
      </p>

      <form className="ui-card p-8 space-y-6" onSubmit={guardar} noValidate>
        {CAMPOS.map((campo) => (
          <div key={campo.nombre}>
            <label
              className="block text-sm font-semibold text-ink mb-1"
              htmlFor={`sancion-${campo.nombre}`}
            >
              {campo.etiqueta}
            </label>

            <div className="flex items-center gap-2">
              <input
                id={`sancion-${campo.nombre}`}
                type="number"
                min={campo.min}
                max={campo.max}
                value={valores[campo.nombre] ?? ""}
                onChange={(e) =>
                  setValores({ ...valores, [campo.nombre]: Number(e.target.value) })
                }
                className="w-28 rounded-lg border border-ink/15 bg-bg px-3 py-2 text-sm text-ink"
              />
              <span className="text-sm text-ink/60">{campo.unidad}</span>
            </div>

            <p className="mt-1 text-xs text-ink/55">{campo.ayuda}</p>
          </div>
        ))}

        <div className="flex justify-end pt-2">
          <button type="submit" className="ui-primary px-5 py-2.5 text-sm" disabled={guardando}>
            {guardando ? "Guardando…" : "Guardar cambios"}
          </button>
        </div>
      </form>

      <p className="mt-4 text-xs text-ink/55">
        Los cambios aplican sobre el próximo conteo. El historial de incidencias
        no se toca nunca: es permanente, independientemente de la ventana.
      </p>
    </main>
  );
}
