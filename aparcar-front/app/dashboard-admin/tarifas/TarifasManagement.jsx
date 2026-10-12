"use client";

import { useCallback, useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";
import api from "@/app/api";
import { PERIODOS_TARIFA, TIPOS_TARIFA } from "@/utils/tarifas";

const importe = z.string().trim()
  .regex(/^\d{1,8}([.,]\d{1,2})?$/, "Ingresá un importe entre 0 y 99.999.999,99, con hasta 2 decimales")
  .transform((valor) => valor.replace(",", "."));
const schema = z.object({ tarifas: z.array(z.object({
  tipo: z.string(), version: z.number().nullable(),
  ...Object.fromEntries(PERIODOS_TARIFA.map(({ campo }) => [campo, importe])),
})).length(4) });

function valoresFormulario(tarifas) {
  return { tarifas: TIPOS_TARIFA.map(({ tipo }) => {
    const fila = tarifas.find((tarifa) => tarifa.tipo === tipo);
    return { tipo, version: fila?.version ?? null,
      ...Object.fromEntries(PERIODOS_TARIFA.map(({ campo }) => [campo, fila?.[campo] == null ? "" : String(fila[campo])])),
    };
  }) };
}

export default function TarifasManagement() {
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState("");
  const [errorGuardado, setErrorGuardado] = useState("");
  const { register, handleSubmit, reset, formState: { errors, isDirty, isSubmitting } } = useForm({
    resolver: zodResolver(schema), defaultValues: valoresFormulario([]),
  });

  const cargar = useCallback(async () => {
    setCargando(true);
    setErrorCarga("");
    setErrorGuardado("");
    try {
      const { data } = await api.get("/api/v1/tarifas");
      reset(valoresFormulario(data));
    } catch {
      setErrorCarga("No se pudieron cargar las tarifas. Reintentá para poder editarlas.");
    } finally {
      setCargando(false);
    }
  }, [reset]);

  useEffect(() => { cargar(); }, [cargar]);

  const guardar = async (datos) => {
    setErrorGuardado("");
    try {
      const { data } = await api.put("/api/v1/tarifas", datos);
      reset(valoresFormulario(data));
      toast.success("Tarifas actualizadas. Se aplicarán a las nuevas reservas.");
    } catch (error) {
      setErrorGuardado(error.response?.data?.message || "No se pudieron guardar las tarifas. Tus cambios siguen en el formulario.");
    }
  };

  return (
    <section className="space-y-6">
      <div>
        <p className="eyebrow mb-3">PRECIOS DEL ESTACIONAMIENTO</p>
        <h1 className="mb-2 text-3xl font-extrabold tracking-tight text-ink">Gestionar tarifas</h1>
        <p className="max-w-3xl text-sm text-ink/60">Definí los cuatro precios de cada categoría en pesos argentinos (ARS). Los cambios se aplican a nuevas reservas; las reservas ya creadas conservan su importe.</p>
      </div>
      <div className="ui-card p-5 text-sm text-ink/70">
        <p>El total suma jornadas de 24 h, medias jornadas de 12 h, horas y fracciones de 15 min restantes.</p>
        <p className="mt-2">La tarifa Accesible corresponde a una cochera accesible. Completá los 16 precios antes de guardar; un valor de 0 indica que ese período es sin cargo.</p>
      </div>
      {cargando ? <p role="status" className="py-8 text-center text-ink/60">Cargando tarifas…</p> : errorCarga ? (
        <div className="ui-card p-6">
          <p role="alert" className="text-sm text-red-600 dark:text-red-300">{errorCarga}</p>
          <button onClick={cargar} className="ui-primary mt-4 px-5 py-3">Reintentar carga</button>
        </div>
      ) : (
        <form onSubmit={handleSubmit(guardar)} noValidate className="space-y-6">
          <fieldset disabled={isSubmitting} className="grid min-w-0 grid-cols-1 gap-5 md:grid-cols-2 xl:grid-cols-4">
            <legend className="sr-only">Precios por categoría y duración</legend>
            {TIPOS_TARIFA.map(({ tipo, nombre }, indice) => (
              <div key={tipo} className="ui-card p-5 sm:p-6">
                <h2 className="mb-5 border-b border-ink/10 pb-4 text-lg font-bold text-ink">{nombre}</h2>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 md:grid-cols-1">
                  {PERIODOS_TARIFA.map(({ campo, nombre: periodo, duracion }) => {
                    const id = `tarifa-${tipo}-${campo}`;
                    const error = errors.tarifas?.[indice]?.[campo];
                    return (
                      <div key={campo}>
                        <label htmlFor={id} className="ui-label">{periodo}<span className="sr-only"> · {nombre}</span></label>
                        <p id={`${id}-ayuda`} className="mb-2 text-xs text-ink/50">{duracion} · ARS</p>
                        <input id={id} inputMode="decimal" autoComplete="off" placeholder="0,00" className="ui-input"
                          aria-invalid={Boolean(error)} aria-describedby={`${id}-ayuda${error ? ` ${id}-error` : ""}`}
                          {...register(`tarifas.${indice}.${campo}`)} />
                        {error && <p id={`${id}-error`} role="alert" className="mt-1 text-xs text-red-600 dark:text-red-300">{error.message}</p>}
                      </div>
                    );
                  })}
                </div>
              </div>
            ))}
          </fieldset>
          {errorGuardado && <p role="alert" className="text-sm text-red-600 dark:text-red-300">{errorGuardado}</p>}
          <div className="ui-card flex flex-col gap-4 p-5 sm:flex-row sm:items-center sm:justify-between">
            <p className="text-sm text-ink/60" role="status">{isDirty ? "Tenés cambios sin guardar." : "Los precios del formulario están actualizados."}</p>
            <div className="flex flex-col gap-3 sm:flex-row">
              <button type="button" disabled={isSubmitting} onClick={() => {
                if (!isDirty || window.confirm("¿Descartar los cambios sin guardar y recargar las tarifas?")) cargar();
              }} className="rounded-xl border border-ink/15 px-5 py-3 text-sm font-semibold text-ink disabled:opacity-50">Recargar precios</button>
              <button type="submit" disabled={isSubmitting || !isDirty} className="ui-primary px-6 py-3 text-sm font-semibold disabled:cursor-not-allowed disabled:opacity-50">{isSubmitting ? "Guardando…" : "Guardar tarifas"}</button>
            </div>
          </div>
        </form>
      )}
    </section>
  );
}
