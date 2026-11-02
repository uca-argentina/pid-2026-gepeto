"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";
import api from "@/app/api";

const vacio = { nombre: "", formatoCredencial: "", descuentoPct: "" };
const schema = z.object({
  nombre: z.string().trim().min(1, "Ingresá el nombre del convenio").max(100, "Usá hasta 100 caracteres"),
  formatoCredencial: z.string().trim().toUpperCase().max(100, "Usá hasta 100 caracteres")
    .refine((valor) => /^[A-Z0-9._/-]+XXX$/.test(valor) && valor.indexOf("XXX") === valor.length - 3,
      "Usá un prefijo de letras, números, guiones, puntos o barras y terminá con XXX. Ejemplo: UCA-XXX"),
  descuentoPct: z.string().trim()
    .regex(/^\d{1,3}([.,]\d{1,2})?$/, "Ingresá un porcentaje con hasta 2 decimales")
    .transform((valor) => Number(valor.replace(",", ".")))
    .refine((valor) => valor > 0 && valor <= 100, "El descuento debe ser mayor que 0 y hasta 100%"),
});

function Campo({ form, nombre, etiqueta, ayuda, ...props }) {
  const id = `convenio-${nombre}`;
  const error = form.formState.errors[nombre];
  return (
    <div className="min-w-0">
      <label className="ui-label" htmlFor={id}>{etiqueta}</label>
      <input id={id} className="ui-input" {...form.register(nombre)} {...props}
        aria-invalid={Boolean(error)} aria-describedby={`${id}-ayuda${error ? ` ${id}-error` : ""}`} />
      <p id={`${id}-ayuda`} className="mt-2 text-xs text-ink/60">{ayuda}</p>
      {error && <p id={`${id}-error`} role="alert" className="mt-2 text-sm text-red-600 dark:text-red-300">{error.message}</p>}
    </div>
  );
}

export default function ConveniosManagement() {
  const [convenios, setConvenios] = useState([]);
  const [cargando, setCargando] = useState(true);
  const [errorCarga, setErrorCarga] = useState("");
  const [errorGuardado, setErrorGuardado] = useState("");
  const [editando, setEditando] = useState(null);
  const [eliminando, setEliminando] = useState(null);
  const operacionEnCurso = useRef(false);
  const form = useForm({ resolver: zodResolver(schema), defaultValues: vacio });
  const { reset, setFocus } = form;

  const cargar = useCallback(async () => {
    setCargando(true);
    setErrorCarga("");
    try {
      const { data } = await api.get("/api/v1/convenios");
      setConvenios(data);
    } catch {
      setErrorCarga("No se pudieron cargar los convenios. Reintentá para gestionarlos.");
    } finally {
      setCargando(false);
    }
  }, []);

  useEffect(() => { cargar(); }, [cargar]);

  const limpiar = () => {
    setEditando(null);
    setErrorGuardado("");
    reset(vacio);
  };

  const guardar = async (datos) => {
    if (operacionEnCurso.current) return;
    operacionEnCurso.current = true;
    setErrorGuardado("");
    try {
      const { data } = editando
        ? await api.put(`/api/v1/convenios/${editando.id}`, datos)
        : await api.post("/api/v1/convenios", datos);
      setConvenios((actuales) => editando
        ? actuales.map((convenio) => convenio.id === data.id ? data : convenio)
        : [...actuales, data]);
      toast.success(editando ? "Convenio actualizado." : "Convenio agregado.");
      limpiar();
    } catch (error) {
      setErrorGuardado(error.response?.data?.message || "No se pudo guardar el convenio. Tus cambios siguen en el formulario.");
    } finally {
      operacionEnCurso.current = false;
    }
  };

  const editar = (convenio) => {
    setEditando(convenio);
    setErrorGuardado("");
    reset({ nombre: convenio.nombre, formatoCredencial: convenio.formatoCredencial, descuentoPct: String(convenio.descuentoPct) });
    setFocus("nombre");
  };

  const eliminar = async (convenio) => {
    if (operacionEnCurso.current || !window.confirm(`¿Eliminar el convenio ${convenio.nombre}? Las reservas ya creadas conservarán su descuento.`)) return;
    operacionEnCurso.current = true;
    setEliminando(convenio.id);
    try {
      await api.delete(`/api/v1/convenios/${convenio.id}`);
      setConvenios((actuales) => actuales.filter((actual) => actual.id !== convenio.id));
      if (editando?.id === convenio.id) limpiar();
      toast.success("Convenio eliminado. Las reservas existentes conservan su descuento.");
    } catch (error) {
      toast.error(error.response?.data?.message || "No se pudo eliminar el convenio.");
    } finally {
      operacionEnCurso.current = false;
      setEliminando(null);
    }
  };

  const ocupado = form.formState.isSubmitting || eliminando !== null;
  return (
    <section className="space-y-6">
      <div>
        <p className="eyebrow mb-3">DESCUENTOS DEL ESTACIONAMIENTO</p>
        <h1 className="mb-2 text-3xl font-extrabold tracking-tight text-ink">Gestionar convenios</h1>
        <p className="max-w-3xl text-sm text-ink/60">Configurá las instituciones y sus descuentos. Las reservas ya creadas conservan el convenio y el descuento aplicado, aunque después lo modifiques o elimines.</p>
      </div>
      <div className="ui-card p-5 text-sm text-ink/70">
        Las credenciales deben coincidir con los últimos 3 dígitos del documento del visitante. Los convenios no se acumulan entre sí ni con puntos: se aplica el beneficio de mayor descuento.
      </div>
      {cargando ? <p role="status" className="py-8 text-center text-ink/60">Cargando convenios…</p> : errorCarga ? (
        <div className="ui-card p-6">
          <p role="alert" className="text-sm text-red-600 dark:text-red-300">{errorCarga}</p>
          <button type="button" onClick={cargar} className="ui-primary mt-4 px-5 py-3">Reintentar carga</button>
        </div>
      ) : (
        <div className="grid min-w-0 grid-cols-1 items-start gap-6 lg:grid-cols-2">
          <form onSubmit={form.handleSubmit(guardar)} noValidate className="ui-card min-w-0 p-5 sm:p-6">
            <fieldset disabled={ocupado} className="min-w-0 space-y-5">
              <legend className="mb-5 text-lg font-bold text-ink">{editando ? `Editar convenio: ${editando.nombre}` : "Nuevo convenio"}</legend>
              <Campo form={form} nombre="nombre" etiqueta="Nombre del convenio" maxLength={100}
                placeholder="Ej. UCA" ayuda="El nombre que verán los visitantes al reservar." />
              <Campo form={form} nombre="formatoCredencial" etiqueta="Formato de credencial" maxLength={100} autoComplete="off"
                placeholder="Ej. UCA-XXX" ayuda="XXX representa los últimos 3 dígitos del documento. Usá XXX una sola vez, al final del formato." />
              <Campo form={form} nombre="descuentoPct" etiqueta="Descuento (%)" inputMode="decimal" autoComplete="off"
                placeholder="Ej. 15" ayuda="Mayor que 0 y hasta 100%, con hasta 2 decimales." />
              {errorGuardado && <p role="alert" className="text-sm text-red-600 dark:text-red-300">{errorGuardado}</p>}
              <div className="flex flex-col gap-3 border-t border-ink/10 pt-5 sm:flex-row sm:justify-end">
                {editando && <button type="button" onClick={limpiar} className="profile-secondary">Cancelar edición</button>}
                <button type="submit" className="ui-primary px-5 py-3 text-sm disabled:opacity-50">{form.formState.isSubmitting ? "Guardando…" : editando ? "Guardar convenio" : "Agregar convenio"}</button>
              </div>
            </fieldset>
          </form>
          <div className="min-w-0">
            <h2 className="mb-4 text-lg font-bold text-ink">Convenios disponibles</h2>
            {convenios.length === 0 ? <p className="ui-card p-6 text-sm text-ink/60">Todavía no hay convenios. Agregá el primero desde el formulario.</p> : (
              <ul className="space-y-4">
                {convenios.map((convenio) => (
                  <li key={convenio.id} className="ui-card min-w-0 p-5">
                    <div className="flex flex-wrap items-start justify-between gap-3">
                      <h3 className="min-w-0 break-words text-lg font-bold text-ink">{convenio.nombre}</h3>
                      <span className="shrink-0 rounded-lg bg-accent/10 px-3 py-1 text-sm font-semibold text-link">{Number(convenio.descuentoPct).toLocaleString("es-AR")}% de descuento</span>
                    </div>
                    <p className="mt-2 break-all text-sm text-ink/60">Credencial: <span className="font-semibold">{convenio.formatoCredencial}</span></p>
                    <div className="mt-5 flex flex-wrap gap-3">
                      <button type="button" aria-label={`Editar ${convenio.nombre}`} disabled={ocupado} onClick={() => editar(convenio)} className="profile-secondary">Editar</button>
                      <button type="button" aria-label={`Eliminar ${convenio.nombre}`} disabled={ocupado} onClick={() => eliminar(convenio)} className="rounded-xl border border-red-500/25 px-4 py-2 text-sm font-semibold text-red-600 disabled:opacity-50 dark:text-red-300">{eliminando === convenio.id ? "Eliminando…" : "Eliminar"}</button>
                    </div>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
