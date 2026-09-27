"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";
import api from "@/app/api";
import { formatoPatenteValido, MENSAJE_FORMATO_INVALIDO } from "@/utils/patenteValidation";

const vehiculoSchema = z
  .object({
    patente: z.string().min(1, "La patente es obligatoria"),
    tipo: z.enum(["AUTO", "MOTO", "CARGA"], {
      message: "Selecciona un tipo de vehículo",
    }),
  })
  .superRefine((data, ctx) => {
    if (!formatoPatenteValido(data.patente, data.tipo)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["patente"],
        message: MENSAJE_FORMATO_INVALIDO[data.tipo],
      });
    }
  });


const inputClasses = "ui-input";

// Los datos personales se editan desde Mi cuenta. Los vehículos siguen junto
// a las reservas para conservar el refresco de patentes del panel.
export default function MiPerfilContent({ onVehiculosCambiaron }) {
  const [vehiculos, setVehiculos] = useState(null);
  const [loading, setLoading] = useState(true);
  const [editandoVehiculoId, setEditandoVehiculoId] = useState(null);
  const vehiculoForm = useForm({
    resolver: zodResolver(vehiculoSchema),
    defaultValues: { patente: "", tipo: "AUTO" },
  });
  const editVehiculoForm = useForm({ resolver: zodResolver(vehiculoSchema) });
  const cargarVehiculos = async () => {
    const res = await api.get("/api/v1/vehiculos");
    setVehiculos(res.data);
  };
  useEffect(() => {
    let activo = true;
    api.get("/api/v1/vehiculos")
      .then(({ data }) => {
        if (activo) setVehiculos(data);
      })
      .catch(() => { if (activo) toast.error("No se pudieron cargar tus vehículos."); })
      .finally(() => { if (activo) setLoading(false); });
    return () => { activo = false; };
  }, []);

  const onAgregarVehiculo = async (data) => {
    try {
      await api.post("/api/v1/vehiculos", data);
      toast.success("Vehículo agregado correctamente");
      vehiculoForm.reset({ patente: "", tipo: "AUTO" });
      await cargarVehiculos();
      onVehiculosCambiaron?.();
    } catch (err) {
      toast.error(err.response?.data?.message || "No se pudo agregar el vehículo.");
    }
  };

  const startEditandoVehiculo = (vehiculo) => {
    editVehiculoForm.reset({ patente: vehiculo.patente, tipo: vehiculo.tipo });
    setEditandoVehiculoId(vehiculo.id);
  };

  const onEditarVehiculo = async (data) => {
    try {
      await api.put(`/api/v1/vehiculos/${editandoVehiculoId}`, data);
      toast.success("Vehículo actualizado correctamente");
      setEditandoVehiculoId(null);
      await cargarVehiculos();
      onVehiculosCambiaron?.();
    } catch (err) {
      toast.error(err.response?.data?.message || "No se pudo actualizar el vehículo.");
    }
  };

  const eliminarVehiculo = async (vehiculo) => {
    const confirmado = window.confirm(`¿Seguro que querés eliminar el vehículo ${vehiculo.patente}?`);
    if (!confirmado) {
      return;
    }

    try {
      await api.delete(`/api/v1/vehiculos/${vehiculo.id}`);
      toast.success("Vehículo eliminado correctamente");
      await cargarVehiculos();
      onVehiculosCambiaron?.();
    } catch (err) {
      toast.error(err.response?.data?.message || "No se pudo eliminar el vehículo.");
    }
  };


  if (loading) return <p className="p-8 text-center text-sm text-ink/60">Cargando tus vehículos...</p>;
  if (!vehiculos) return <p className="p-8 text-center text-sm text-ink/60">No se pudieron cargar tus vehículos.</p>;

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <div>
        <h1 className="text-3xl font-extrabold tracking-tight text-ink mb-2">Mis vehículos</h1>
        <p className="text-sm text-ink/60">Tus vehículos, listos para tu próxima reserva.</p>
      </div>
      <div className="ui-card p-6">
        {vehiculos.length === 0 ? (
          <p className="text-sm text-ink/60 mb-4">Todavía no cargaste ningún vehículo.</p>
        ) : (
          <ul className="mb-4 divide-y divide-ink/10">
            {vehiculos.map((v) =>
              editandoVehiculoId === v.id ? (
                <li key={v.id} className="py-3">
                  <form
                    className="vehicle-form flex flex-wrap items-start gap-2"
                    onSubmit={editVehiculoForm.handleSubmit(onEditarVehiculo)}
                  >
                    <div>
                      <input
                        {...editVehiculoForm.register("patente")}
                        className={`${inputClasses} uppercase`}
                      />
                      {editVehiculoForm.formState.errors.patente && (
                        <p className="mt-1 text-sm text-red-500">
                          {editVehiculoForm.formState.errors.patente.message}
                        </p>
                      )}
                    </div>
                    <select {...editVehiculoForm.register("tipo")} className={inputClasses}>
                      <option value="AUTO">Auto</option>
                      <option value="MOTO">Moto</option>
                      <option value="CARGA">Carga</option>
                    </select>
                    <button
                      type="submit"
                      className="ui-primary px-4 py-3 text-sm font-semibold transition-all"
                    >
                      Guardar
                    </button>
                    <button
                      type="button"
                      onClick={() => setEditandoVehiculoId(null)}
                      className="rounded-xl px-4 py-3 text-sm font-medium text-ink/70 hover:bg-ink/5 transition-colors"
                    >
                      Cancelar
                    </button>
                  </form>
                </li>
              ) : (
                <li key={v.id} className="flex flex-wrap items-center justify-between gap-3 py-4 text-sm">
                  <span className="font-medium text-ink">{v.patente}</span>
                  <span className="text-ink/60">{v.tipo}</span>
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => startEditandoVehiculo(v)}
                      className="rounded-lg border border-ink/20 px-2.5 py-1 text-xs font-semibold text-ink hover:bg-ink/5 transition-colors"
                    >
                      Editar
                    </button>
                    <button
                      type="button"
                      onClick={() => eliminarVehiculo(v)}
                      className="rounded-lg border border-red-200 px-2.5 py-1 text-xs font-semibold text-red-600 hover:bg-red-50 transition-colors dark:bg-red-500/10 dark:text-red-300 dark:ring-red-400/20 dark:border-red-400/25 dark:hover:bg-red-500/20"
                    >
                      Eliminar
                    </button>
                  </div>
                </li>
              )
            )}
          </ul>
        )}

        <form
          className="vehicle-form flex flex-wrap items-start gap-3"
          onSubmit={vehiculoForm.handleSubmit(onAgregarVehiculo)}
        >
          <div>
            <input
              {...vehiculoForm.register("patente")}
              className={`${inputClasses} uppercase`}
              placeholder="ABC123 / AB123CD"
            />
            {vehiculoForm.formState.errors.patente && (
              <p className="mt-1 text-sm text-red-500">{vehiculoForm.formState.errors.patente.message}</p>
            )}
          </div>
          <select {...vehiculoForm.register("tipo")} className={inputClasses}>
            <option value="AUTO">Auto</option>
            <option value="MOTO">Moto</option>
            <option value="CARGA">Carga</option>
          </select>
          <button
            type="submit"
            disabled={vehiculoForm.formState.isSubmitting}
            className="ui-primary px-4 py-3 text-sm font-semibold transition-all disabled:opacity-50 disabled:cursor-not-allowed"
          >
            Agregar
          </button>
        </form>
      </div>
    </div>
  );
}
