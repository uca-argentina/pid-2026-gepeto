"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";
import api from "@/app/api";
import AuthLayout from "@/components/AuthLayout";
import Brand from "@/components/Brand";

const registrationSchema = z.object({
  nombre: z.string().trim().min(1, "Ingresá tu nombre completo").max(100, "El nombre no puede superar los 100 caracteres"),
  documento: z.string().trim().min(1, "Ingresá tu DNI o documento").max(255, "El documento es demasiado largo"),
  email: z.string().trim().toLowerCase().email("Ingresá un correo válido").max(255, "El email es demasiado largo"),
  telefono: z.string().trim().max(255, "El teléfono es demasiado largo"),
  password: z.string().min(8, "La contraseña debe tener al menos 8 caracteres")
    .refine((value) => value.trim().length > 0, "La contraseña no puede contener solo espacios")
    .refine((value) => new TextEncoder().encode(value).length <= 72, "La contraseña es demasiado larga (máximo 72 bytes UTF-8)"),
  confirmPassword: z.string().min(1, "Repetí tu contraseña"),
}).refine((data) => data.password === data.confirmPassword, {
  message: "Las contraseñas no coinciden",
  path: ["confirmPassword"],
});

const inputClasses = "ui-input";
const fields = [
  { name: "nombre", label: "Nombre completo", autoComplete: "name", placeholder: "Tu nombre y apellido", maxLength: 100 },
  { name: "documento", label: "DNI / Documento", autoComplete: "off", placeholder: "Tu número de documento", maxLength: 255 },
  { name: "email", label: "Correo electrónico", type: "email", autoComplete: "email", placeholder: "nombre@ejemplo.com", maxLength: 255 },
  { name: "telefono", label: "Teléfono (opcional)", type: "tel", autoComplete: "tel", placeholder: "Tu teléfono de contacto", maxLength: 255 },
];

export default function RegisterPage() {
  const router = useRouter();
  const [showPasswords, setShowPasswords] = useState(false);
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm({
    resolver: zodResolver(registrationSchema),
    defaultValues: { nombre: "", documento: "", email: "", telefono: "", password: "", confirmPassword: "" },
  });

  const onSubmit = async ({ confirmPassword: _confirmation, ...data }) => {
    try {
      await api.post("/register", { ...data, telefono: data.telefono || undefined });
      toast.success("Tu cuenta fue creada. Ya podés iniciar sesión.");
      router.replace("/login");
    } catch (error) {
      const message = error.response?.status === 429
        ? "Demasiados intentos. Esperá un minuto antes de volver a intentar."
        : error.response?.data?.message || "No pudimos crear tu cuenta. Volvé a intentar.";
      setError("root", { message });
      toast.error(message);
    }
  };

  return (
    <AuthLayout wide>
      <div className="auth-card ui-card">
        <div>
          <Link href="/" aria-label="AparcAR, ir al inicio" className="mb-2 inline-block rounded-lg">
            <Brand />
          </Link>
          <h1 className="mt-5 text-3xl font-extrabold tracking-tight text-ink">Creá tu cuenta</h1>
          <p className="mt-3 max-w-md text-sm leading-6 text-ink/60">
            Registrate en AparcAR. Después vas a poder agregar tus vehículos y gestionar tus reservas.
          </p>
        </div>

        <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)} noValidate>
          <fieldset disabled={isSubmitting} className="space-y-6">
            <legend className="mb-4 text-xs font-semibold uppercase tracking-widest text-link">Tus datos</legend>
            <div className="grid gap-4 sm:grid-cols-2">
              {fields.map(({ name, label, ...inputProps }) => (
                <div key={name}>
                  <label htmlFor={name} className="mb-1.5 block text-sm font-medium text-ink/80">{label}</label>
                  <input id={name} {...inputProps} {...register(name)} className={inputClasses}
                    required={name !== "telefono"} aria-invalid={!!errors[name]}
                    aria-describedby={errors[name] ? `${name}-error` : undefined} />
                  {errors[name] && <p id={`${name}-error`} className="mt-1 text-sm text-red-500">{errors[name].message}</p>}
                </div>
              ))}
            </div>

            <div className="border-t border-ink/10 pt-6">
              <div className="mb-4 flex flex-wrap items-center justify-between gap-4">
                <h2 className="text-xs font-semibold uppercase tracking-widest text-link">Tu contraseña</h2>
                <button type="button" onClick={() => setShowPasswords((value) => !value)} aria-pressed={showPasswords}
                  className="text-sm font-medium text-ink/70 hover:text-link">
                  {showPasswords ? "Ocultar contraseñas" : "Mostrar contraseñas"}
                </button>
              </div>
              <div className="grid gap-4 sm:grid-cols-2">
                {[{ name: "password", label: "Contraseña" }, { name: "confirmPassword", label: "Confirmar contraseña" }].map(({ name, label }) => (
                  <div key={name}>
                    <label htmlFor={name} className="mb-1.5 block text-sm font-medium text-ink/80">{label}</label>
                    <input id={name} type={showPasswords ? "text" : "password"} autoComplete="new-password"
                      {...register(name)} className={inputClasses} required aria-invalid={!!errors[name]}
                      aria-describedby={errors[name] ? `${name}-error` : "password-help"} />
                    {errors[name] && <p id={`${name}-error`} className="mt-1 text-sm text-red-500">{errors[name].message}</p>}
                  </div>
                ))}
              </div>
              <p id="password-help" className="mt-3 text-xs leading-5 text-ink/60">Usá al menos 8 caracteres. Elegí una contraseña que no uses en otros sitios.</p>
            </div>
          </fieldset>

          {errors.root && <p role="alert" className="rounded-xl border border-red-500/20 bg-red-500/5 p-3 text-sm text-red-500">{errors.root.message}</p>}
          <button type="submit" disabled={isSubmitting}
            className="ui-primary flex w-full justify-center px-4 py-3 text-sm font-semibold transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent disabled:cursor-not-allowed disabled:opacity-50">
            {isSubmitting ? "Creando tu cuenta..." : "Crear cuenta"}
          </button>
        </form>

        <p className="mt-6 text-center text-sm text-ink/60">
          ¿Ya tenés cuenta?{" "}
          <Link href="/login" className="font-semibold text-link transition-colors hover:text-ink">Iniciá sesión</Link>
        </p>
      </div>
    </AuthLayout>
  );
}
