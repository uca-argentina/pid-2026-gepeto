"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";
import api from "@/app/api";
import DashboardIcon from "@/components/DashboardIcon";
import PatenteVisual from "@/components/PatenteVisual";
import {
  ejemplosPatente,
  formatoPatenteValido,
  mensajeFormatoInvalido,
  normalizarPatente,
} from "@/utils/patenteValidation";

// La patente se normaliza (sin espacios ni guiones, en mayúsculas) antes de
// validar y de enviar: la leyenda la muestra separada en bloques, como en la
// chapa, y el backend espera el formato compacto.
const vehiculoSchema = z
  .object({
    patente: z.string().transform(normalizarPatente).pipe(z.string().min(1, "La patente es obligatoria")),
    tipo: z.enum(["AUTO", "MOTO", "CARGA"], {
      message: "Selecciona un tipo de vehículo",
    }),
  })
  .superRefine((data, ctx) => {
    if (!formatoPatenteValido(data.patente, data.tipo)) {
      ctx.addIssue({
        code: "custom",
        path: ["patente"],
        message: mensajeFormatoInvalido(data.tipo),
      });
    }
  });

const TIPO_ETIQUETA = { AUTO: "Auto", MOTO: "Moto", CARGA: "Carga" };

/**
 * Campos de patente y tipo, compartidos por el alta y la edición. La leyenda
 * de formato sigue al tipo elegido: moto y auto/carga usan esquemas distintos.
 */
function CamposVehiculo({ form, idPrefijo }) {
  const tipo = form.watch("tipo");
  const { actual, anterior } = ejemplosPatente(tipo);
  const error = form.formState.errors.patente;
  const idPatente = `${idPrefijo}-patente`;
  const idAyuda = `${idPrefijo}-patente-ayuda`;
  const idError = `${idPrefijo}-patente-error`;

  return (
    <>
      <div className="vehicle-form-patente">
        <label className="ui-label" htmlFor={idPatente}>Patente</label>
        <input
          id={idPatente}
          {...form.register("patente")}
          className="ui-input uppercase"
          placeholder={`${actual} / ${anterior}`}
          autoComplete="off"
          aria-invalid={Boolean(error)}
          aria-describedby={error ? `${idError} ${idAyuda}` : idAyuda}
        />
        <p id={idAyuda} className="vehicle-form-ayuda">
          Formato actual: <strong>{actual}</strong> · anterior: <strong>{anterior}</strong>
        </p>
        {error && (
          <p id={idError} role="alert" className="mt-1 text-sm text-red-500">{error.message}</p>
        )}
      </div>
      <div className="vehicle-form-tipo">
        <label className="ui-label" htmlFor={`${idPrefijo}-tipo`}>Tipo de vehículo</label>
        <select id={`${idPrefijo}-tipo`} {...form.register("tipo")} className="ui-input">
          <option value="AUTO">Auto</option>
          <option value="MOTO">Moto</option>
          <option value="CARGA">Carga</option>
        </select>
      </div>
    </>
  );
}

/**
 * Gestión de los vehículos del visitante (alta, edición y baja). Vive dentro
 * de "Mis datos" (/dashboard-user/perfil). El formulario de reserva vuelve a
 * pedir los vehículos al montarse y al enfocar la patente, así que al volver
 * al panel ya ve los cambios sin un aviso explícito; `onVehiculosCambiaron`
 * queda para quien lo renderice junto a otra cosa que dependa de la lista.
 */
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

  let contenido;
  if (loading) {
    contenido = <p role="status" className="text-sm text-ink/60">Cargando tus vehículos...</p>;
  } else if (!vehiculos) {
    contenido = <p className="text-sm text-ink/60">No se pudieron cargar tus vehículos.</p>;
  } else {
    contenido = (
      <>
        {vehiculos.length === 0 ? (
          <p className="mb-6 text-sm text-ink/60">Todavía no cargaste ningún vehículo.</p>
        ) : (
          <ul className="vehicle-list" aria-label="Tus vehículos">
            {vehiculos.map((v) =>
              editandoVehiculoId === v.id ? (
                <li key={v.id} className="vehicle-item is-editando">
                  <form
                    className="vehicle-form"
                    onSubmit={editVehiculoForm.handleSubmit(onEditarVehiculo)}
                    noValidate
                  >
                    <CamposVehiculo form={editVehiculoForm} idPrefijo={`vehiculo-${v.id}`} />
                    <div className="vehicle-form-acciones">
                      <button type="submit" className="ui-primary px-4 text-sm font-semibold">
                        Guardar
                      </button>
                      <button
                        type="button"
                        onClick={() => setEditandoVehiculoId(null)}
                        className="profile-secondary"
                      >
                        Cancelar
                      </button>
                    </div>
                  </form>
                </li>
              ) : (
                <li key={v.id} className="vehicle-item">
                  <PatenteVisual patente={v.patente} tipo={v.tipo} />
                  <span className="vehicle-tipo">{TIPO_ETIQUETA[v.tipo] ?? v.tipo}</span>
                  <div className="vehicle-item-acciones">
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
          className="vehicle-form vehicle-form-alta"
          onSubmit={vehiculoForm.handleSubmit(onAgregarVehiculo)}
          noValidate
        >
          <CamposVehiculo form={vehiculoForm} idPrefijo="vehiculo-nuevo" />
          <div className="vehicle-form-acciones">
            <button
              type="submit"
              disabled={vehiculoForm.formState.isSubmitting}
              className="ui-primary px-4 text-sm font-semibold transition-all disabled:opacity-50 disabled:cursor-not-allowed"
            >
              Agregar
            </button>
          </div>
        </form>
      </>
    );
  }

  return (
    <section id="mis-vehiculos" className="ui-card profile-form" aria-labelledby="mis-vehiculos-titulo">
      <div className="profile-section-heading">
        <span className="profile-section-icon"><DashboardIcon name="car" /></span>
        <div>
          <h2 id="mis-vehiculos-titulo">Mis vehículos</h2>
          <p>Cargá, editá o eliminá los vehículos con los que vas a reservar.</p>
        </div>
      </div>
      {contenido}
    </section>
  );
}