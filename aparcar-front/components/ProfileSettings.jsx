"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";
import api from "@/app/api";
import { useAuthStore } from "@/store/authStore";
import DashboardIcon from "@/components/DashboardIcon";

const perfilSchema = z.object({
  email: z.string().trim().min(1, "El email es obligatorio").email("Ingresá un correo válido").max(255),
  telefono: z.string().trim().max(255, "El teléfono es demasiado largo"),
  tieneDiscapacidad: z.boolean(),
});
const adminSchema = perfilSchema.extend({
  documento: z.string().trim().min(1, "El documento es obligatorio").max(255, "El documento es demasiado largo"),
  nombreEstacionamiento: z.string().trim().max(100, "Usá hasta 100 caracteres para el nombre"),
});
const passwordSchema = z.object({
  passwordActual: z.string().min(1, "Ingresá tu contraseña actual"),
  passwordNueva: z.string().min(8, "La contraseña nueva debe tener al menos 8 caracteres")
    .max(100, "La contraseña nueva no puede superar los 100 caracteres"),
  passwordRepetida: z.string().min(1, "Repetí la contraseña nueva"),
}).refine((data) => data.passwordNueva === data.passwordRepetida, {
  message: "Las contraseñas no coinciden", path: ["passwordRepetida"],
});
const emptyPassword = { passwordActual: "", passwordNueva: "", passwordRepetida: "" };
const valuesFrom = (perfil) => ({
  email: perfil.email || "", telefono: perfil.telefono || "",
  documento: perfil.documento || "", nombreEstacionamiento: perfil.nombreEstacionamiento || "",
  tieneDiscapacidad: Boolean(perfil.tieneDiscapacidad),
});

function Field({ form, name, label, hint, ...props }) {
  const error = form.formState.errors[name];
  return (
    <div>
      <label className="ui-label" htmlFor={`perfil-${name}`}>{label}</label>
      <input id={`perfil-${name}`} className="ui-input" {...form.register(name)} {...props}
        aria-invalid={Boolean(error)} aria-describedby={error ? `${name}-error` : hint ? `${name}-hint` : undefined} />
      {error && <p id={`${name}-error`} role="alert" className="mt-2 text-sm text-red-600 dark:text-red-300">{error.message}</p>}
      {hint && <p id={`${name}-hint`} className="mt-2 text-xs text-ink/60">{hint}</p>}
    </div>
  );
}

export default function ProfileSettings({ admin = false }) {
  const [perfil, setPerfil] = useState(null);
  const [loading, setLoading] = useState(true);
  const [attempt, setAttempt] = useState(0);
  const [editingPassword, setEditingPassword] = useState(false);
  const router = useRouter();
  const logout = useAuthStore((state) => state.logout);
  const form = useForm({ resolver: zodResolver(admin ? adminSchema : perfilSchema) });
  const passwordForm = useForm({ resolver: zodResolver(passwordSchema), defaultValues: emptyPassword });
  const { reset } = form;

  useEffect(() => {
    let activo = true;
    api.get("/api/v1/visitantes/me")
      .then(({ data }) => {
        if (activo) { setPerfil(data); reset(valuesFrom(data)); }
      })
      .catch(() => { if (activo) toast.error("No se pudieron cargar tus datos."); })
      .finally(() => { if (activo) setLoading(false); });
    return () => { activo = false; };
  }, [attempt, reset]);

  const onSave = async (data) => {
    try {
      const { data: actualizado } = await api.put("/api/v1/visitantes/me", data);
      if (actualizado.email.trim().toLowerCase() !== perfil.email.trim().toLowerCase()) {
        logout();
        toast.success("Tus datos se guardaron. Iniciá sesión con tu nuevo email.");
        router.replace("/login");
        return;
      }
      setPerfil(actualizado);
      reset(valuesFrom(actualizado));
      toast.success("Tus datos se actualizaron correctamente");
    } catch (err) {
      toast.error(err.response?.data?.message || "No se pudieron actualizar tus datos.");
    }
  };

  const onPassword = async (data) => {
    try {
      await api.put("/api/v1/visitantes/me/password", {
        passwordActual: data.passwordActual, passwordNueva: data.passwordNueva,
      });
      passwordForm.reset(emptyPassword);
      setEditingPassword(false);
      toast.success("Tu contraseña se cambió correctamente");
    } catch (err) {
      toast.error(err.response?.data?.message || "No se pudo cambiar la contraseña.");
    }
  };

  if (loading) return <p role="status" className="p-8 text-center text-sm text-ink/60">Cargando tus datos...</p>;
  if (!perfil) return (
    <div className="ui-card mx-auto max-w-lg p-8 text-center">
      <p className="mb-4 text-sm text-ink/60">No se pudieron cargar tus datos.</p>
      <button type="button" className="profile-secondary" onClick={() => { setLoading(true); setAttempt(attempt + 1); }}>Reintentar</button>
    </div>
  );

  const busy = form.formState.isSubmitting || passwordForm.formState.isSubmitting;
  return (
    <main className="profile-page">
      <div className="profile-page-heading">
        <p className="eyebrow">MI CUENTA</p>
        <h1>Mis datos</h1>
        <p>Un espacio para mantener tu información al día.</p>
      </div>
      <div className="profile-layout">
        <aside className="ui-card profile-identity">
          <span className="profile-avatar" aria-hidden="true">{perfil.nombre?.trim().split(/\s+/).slice(0, 2).map((part) => part[0]).join("").toUpperCase() || "A"}</span>
          <span className="profile-role">{admin ? "Administrador" : "Visitante"}</span>
          <h2>{perfil.nombre}</h2>
          <p>{perfil.email}</p>
          <div className="profile-identity-detail"><DashboardIcon name="profile" /><span>Documento {perfil.documento}</span></div>
          {admin && perfil.nombreEstacionamiento && <div className="profile-identity-detail"><DashboardIcon name="parking" /><span>{perfil.nombreEstacionamiento}</span></div>}
          {!admin && perfil.tieneDiscapacidad && <p className="profile-accessibility-badge">Persona con discapacidad · puede usar cocheras accesibles</p>}
        </aside>

        <div className="profile-sections">
          <form className="ui-card profile-form" onSubmit={form.handleSubmit(onSave)} noValidate>
            <fieldset disabled={busy}>
              <div className="profile-section-heading">
                <span className="profile-section-icon"><DashboardIcon name="profile" /></span>
                <div><h2>Datos personales</h2><p>Tu información de contacto y acceso.</p></div>
              </div>
              <div className="profile-fields">
                {admin && <Field form={form} name="documento" label="Documento" maxLength={255} />}
                <Field form={form} name="telefono" label="Teléfono" type="tel" autoComplete="tel" maxLength={255} placeholder="Ej. 11 1234 5678" hint="Opcional. Podés dejarlo vacío." />
                <div className="profile-field-wide">
                  <Field form={form} name="email" label="Email" type="email" autoComplete="email" maxLength={255}
                    hint="Si cambiás tu email, te pediremos iniciar sesión con el nuevo." />
                </div>
              </div>
              {!admin && (
                <div className="profile-accessibility">
                  <label htmlFor="perfil-discapacidad">
                    <input id="perfil-discapacidad" type="checkbox" {...form.register("tieneDiscapacidad")} />
                    <span>Soy una persona con discapacidad</span>
                  </label>
                  <p>Te habilita a reservar cocheras accesibles. Podés cambiar esta declaración cuando quieras.</p>
                </div>
              )}
              {admin && (
                <section className="profile-parking">
                  <div className="profile-section-heading">
                    <span className="profile-section-icon"><DashboardIcon name="parking" /></span>
                    <div><h2>Tu estacionamiento</h2><p>Dale identidad a tu panel de administración.</p></div>
                  </div>
                  <Field form={form} name="nombreEstacionamiento" label="Nombre del estacionamiento" maxLength={100}
                    placeholder="Ej. Parking Larrea" hint="Aparecerá como título en tu dashboard. Si lo dejás vacío, verás «Mi estacionamiento»." />
                </section>
              )}
              <div className="profile-form-footer">
                <button type="button" className="profile-secondary" disabled={!form.formState.isDirty} onClick={() => reset(valuesFrom(perfil))}>Descartar cambios</button>
                <button type="submit" className="ui-primary px-5 py-2.5 text-sm disabled:opacity-50">{form.formState.isSubmitting ? "Guardando..." : "Guardar cambios"}</button>
              </div>
            </fieldset>
          </form>

          <section className="ui-card profile-form">
            <div className="profile-security-heading">
              <div className="profile-section-heading">
                <span className="profile-section-icon"><DashboardIcon name="shield" /></span>
                <div><h2>Seguridad</h2><p>Protegé el acceso a tu cuenta.</p></div>
              </div>
              {!editingPassword && <button type="button" disabled={busy} className="profile-secondary" onClick={() => { passwordForm.reset(emptyPassword); setEditingPassword(true); }}>Cambiar contraseña</button>}
            </div>
            {editingPassword && (
              <form onSubmit={passwordForm.handleSubmit(onPassword)} noValidate>
                <fieldset disabled={busy}>
                  <div className="profile-fields">
                    <div className="profile-field-wide">
                      <Field form={passwordForm} name="passwordActual" label="Contraseña actual" type="password" autoComplete="current-password"
                        hint={!admin ? "Si tu cuenta la creó un administrador, tu contraseña inicial es tu documento." : undefined} />
                    </div>
                    <Field form={passwordForm} name="passwordNueva" label="Contraseña nueva" type="password" autoComplete="new-password" placeholder="Mínimo 8 caracteres" />
                    <Field form={passwordForm} name="passwordRepetida" label="Repetir contraseña nueva" type="password" autoComplete="new-password" />
                  </div>
                  <div className="profile-form-footer">
                    <button type="button" className="profile-secondary" onClick={() => { passwordForm.reset(emptyPassword); setEditingPassword(false); }}>Cancelar</button>
                    <button type="submit" className="ui-primary px-5 py-2.5 text-sm disabled:opacity-50">{passwordForm.formState.isSubmitting ? "Guardando..." : "Guardar contraseña"}</button>
                  </div>
                </fieldset>
              </form>
            )}
          </section>
        </div>
      </div>
    </main>
  );
}
