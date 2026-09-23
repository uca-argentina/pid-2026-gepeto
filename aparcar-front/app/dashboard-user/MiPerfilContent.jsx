"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";
import api from "@/app/api";

import { formatoPatenteValido, MENSAJE_FORMATO_INVALIDO } from "@/utils/patenteValidation";

const editPerfilSchema = z.object({
  telefono: z.string().optional(),
  email: z.string().min(1, "El email es obligatorio").email("Ingresa un correo válido"),
});

const passwordSchema = z
  .object({
    passwordActual: z.string().min(1, "Ingresá tu contraseña actual"),
    passwordNueva: z
      .string()
      .min(8, "La contraseña nueva debe tener al menos 8 caracteres")
      .max(100, "La contraseña nueva no puede superar los 100 caracteres"),
    passwordRepetida: z.string().min(1, "Repetí la contraseña nueva"),
  })
  .refine((d) => d.passwordNueva === d.passwordRepetida, {
    message: "Las contraseñas no coinciden",
    path: ["passwordRepetida"],
  });

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

const inputClasses =
  "ui-input";
const labelClasses = "ui-label";

// Ya no existe el paso de "cargá tus datos": la cuenta y el visitante son la
// misma entidad, así que nombre y documento vienen dados desde el alta y acá
// solo se muestran. Lo editable es lo de contacto y los vehículos propios.
//
// `onVehiculosCambiaron` avisa al dashboard para que el formulario de reserva
// vuelva a pedir la lista de patentes.
export default function MiPerfilContent({ onVehiculosCambiaron }) {
  const [visitante, setVisitante] = useState(null);
  const [vehiculos, setVehiculos] = useState([]);
  const [loading, setLoading] = useState(true);
  const [editandoPerfil, setEditandoPerfil] = useState(false);
  const [cambiandoPassword, setCambiandoPassword] = useState(false);
  const [editandoVehiculoId, setEditandoVehiculoId] = useState(null);

  const editPerfilForm = useForm({ resolver: zodResolver(editPerfilSchema) });
  const passwordForm = useForm({ resolver: zodResolver(passwordSchema) });
  const vehiculoForm = useForm({
    resolver: zodResolver(vehiculoSchema),
    defaultValues: { patente: "", tipo: "AUTO" },
  });
  const editVehiculoForm = useForm({ resolver: zodResolver(vehiculoSchema) });

  // El backend devuelve solo los vehículos de la cuenta autenticada, así que no
  // hace falta pasarle a quién pertenecen.
  const cargarVehiculos = async () => {
    const res = await api.get("/api/v1/vehiculos");
    setVehiculos(res.data);
  };

  const cargar = async () => {
    try {
      setLoading(true);
      const res = await api.get("/api/v1/visitantes/me");
      setVisitante(res.data);
      await cargarVehiculos();
    } catch {
      toast.error("No se pudieron cargar tus datos.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    cargar();
  }, []);

  const startEditandoPerfil = () => {
    editPerfilForm.reset({ telefono: visitante.telefono || "", email: visitante.email || "" });
    setEditandoPerfil(true);
  };

  const onEditarPerfil = async (data) => {
    try {
      const res = await api.put("/api/v1/visitantes/me", {
        telefono: data.telefono || undefined,
        email: data.email,
      });
      toast.success("Tus datos se actualizaron correctamente");
      setVisitante(res.data);
      setEditandoPerfil(false);
    } catch (err) {
      toast.error(err.response?.data?.message || "No se pudieron actualizar tus datos.");
    }
  };

  const startCambiandoPassword = () => {
    passwordForm.reset({ passwordActual: "", passwordNueva: "", passwordRepetida: "" });
    setCambiandoPassword(true);
  };

  const onCambiarPassword = async (data) => {
    try {
      await api.put("/api/v1/visitantes/me/password", {
        passwordActual: data.passwordActual,
        passwordNueva: data.passwordNueva,
      });
      toast.success("Tu contraseña se cambió correctamente");
      passwordForm.reset({ passwordActual: "", passwordNueva: "", passwordRepetida: "" });
      setCambiandoPassword(false);
    } catch (err) {
      toast.error(err.response?.data?.message || "No se pudo cambiar la contraseña.");
    }
  };

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

  if (loading) {
    return <div className="text-center text-sm text-ink/60 p-8">Cargando tus datos...</div>;
  }

  if (!visitante) {
    return (
      <div className="text-center text-sm text-ink/60 p-8">
        No se pudieron cargar tus datos.
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl">
      <h1 className="text-3xl font-extrabold tracking-tight text-ink mb-2">Mis datos</h1>
      <p className="text-sm text-ink/60 mb-8">Tus datos y vehículos registrados.</p>

      <div className="space-y-8">
        <div className="ui-card p-6">
          <div className="profile-heading flex items-start justify-between gap-4">
            <div>
              <h2 className="text-lg font-bold text-ink">{visitante.nombre}</h2>
              <p className="text-sm text-ink/60">
                Documento {visitante.documento}
                {visitante.telefono ? ` · ${visitante.telefono}` : ""}
                {visitante.email ? ` · ${visitante.email}` : ""}
              </p>
            </div>

            <div className="profile-actions flex shrink-0 gap-2">
              {!editandoPerfil && (
                <button
                  type="button"
                  onClick={startEditandoPerfil}
                  className="rounded-lg border border-ink/20 px-3 py-2 text-xs font-semibold text-ink hover:bg-ink/5 transition-colors"
                >
                  Editar mis datos
                </button>
              )}
              {!cambiandoPassword && (
                <button
                  type="button"
                  onClick={startCambiandoPassword}
                  className="rounded-lg border border-ink/20 px-3 py-2 text-xs font-semibold text-ink hover:bg-ink/5 transition-colors"
                >
                  Cambiar contraseña
                </button>
              )}
            </div>
          </div>

          {editandoPerfil && (
            <form
              className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2"
              onSubmit={editPerfilForm.handleSubmit(onEditarPerfil)}
            >
              <div>
                <label className={labelClasses} htmlFor="edit-telefono">Teléfono</label>
                <input id="edit-telefono" {...editPerfilForm.register("telefono")} className={inputClasses} />
              </div>
              <div>
                <label className={labelClasses} htmlFor="edit-email">Email</label>
                <input id="edit-email" type="email" {...editPerfilForm.register("email")} className={inputClasses} />
                {editPerfilForm.formState.errors.email && (
                  <p className="mt-1 text-sm text-red-500">{editPerfilForm.formState.errors.email.message}</p>
                )}
                <p className="mt-1 text-xs text-ink/50">
                  Es con lo que iniciás sesión: si lo cambiás, entrás con el nuevo.
                </p>
              </div>
              <div className="flex gap-2 sm:col-span-2">
                <button
                  type="submit"
                  disabled={editPerfilForm.formState.isSubmitting}
                  className="ui-primary px-4 py-2.5 text-sm font-semibold transition-all disabled:opacity-50"
                >
                  Guardar cambios
                </button>
                <button
                  type="button"
                  onClick={() => setEditandoPerfil(false)}
                  className="rounded-xl px-4 py-2.5 text-sm font-medium text-ink/70 hover:bg-ink/5 transition-colors"
                >
                  Cancelar
                </button>
              </div>
            </form>
          )}

          {cambiandoPassword && (
            <form
              className="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2"
              onSubmit={passwordForm.handleSubmit(onCambiarPassword)}
            >
              <div className="sm:col-span-2">
                <label className={labelClasses} htmlFor="password-actual">Contraseña actual</label>
                <input
                  id="password-actual"
                  type="password"
                  autoComplete="current-password"
                  {...passwordForm.register("passwordActual")}
                  className={inputClasses}
                />
                {passwordForm.formState.errors.passwordActual && (
                  <p className="mt-1 text-sm text-red-500">
                    {passwordForm.formState.errors.passwordActual.message}
                  </p>
                )}
                <p className="mt-1 text-xs text-ink/50">
                  Si tu cuenta la creó un administrador, tu contraseña actual es tu documento.
                </p>
              </div>
              <div>
                <label className={labelClasses} htmlFor="password-nueva">Contraseña nueva</label>
                <input
                  id="password-nueva"
                  type="password"
                  autoComplete="new-password"
                  {...passwordForm.register("passwordNueva")}
                  className={inputClasses}
                  placeholder="Mínimo 8 caracteres"
                />
                {passwordForm.formState.errors.passwordNueva && (
                  <p className="mt-1 text-sm text-red-500">
                    {passwordForm.formState.errors.passwordNueva.message}
                  </p>
                )}
              </div>
              <div>
                <label className={labelClasses} htmlFor="password-repetida">Repetir contraseña nueva</label>
                <input
                  id="password-repetida"
                  type="password"
                  autoComplete="new-password"
                  {...passwordForm.register("passwordRepetida")}
                  className={inputClasses}
                />
                {passwordForm.formState.errors.passwordRepetida && (
                  <p className="mt-1 text-sm text-red-500">
                    {passwordForm.formState.errors.passwordRepetida.message}
                  </p>
                )}
              </div>
              <div className="flex gap-2 sm:col-span-2">
                <button
                  type="submit"
                  disabled={passwordForm.formState.isSubmitting}
                  className="ui-primary px-4 py-2.5 text-sm font-semibold transition-all disabled:opacity-50"
                >
                  Guardar contraseña
                </button>
                <button
                  type="button"
                  onClick={() => setCambiandoPassword(false)}
                  className="rounded-xl px-4 py-2.5 text-sm font-medium text-ink/70 hover:bg-ink/5 transition-colors"
                >
                  Cancelar
                </button>
              </div>
            </form>
          )}
        </div>

        <div className="ui-card p-6">
          <h2 className="text-lg font-bold text-ink mb-4">Mis vehículos</h2>

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
    </div>
  );
}
