"use client";

import { Suspense } from "react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";
import api from "../api";
import AuthLayout from "@/components/AuthLayout";

const resetSchema = z
  .object({
    email: z.string().email("Ingresa un correo válido"),
    otp: z.string().min(1, "Ingresá el código que recibiste por correo"),
    newPassword: z.string().min(8, "La contraseña debe tener al menos 8 caracteres"),
    confirmPassword: z.string(),
  })
  .refine((data) => data.newPassword === data.confirmPassword, {
    message: "Las contraseñas no coinciden",
    path: ["confirmPassword"],
  });

function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const emailFromQuery = searchParams.get("email") || "";

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(resetSchema),
    defaultValues: { email: emailFromQuery },
  });

  const onSubmit = async (data) => {
    try {
      // El backend espera el campo "new_password" en snake_case.
      await api.post("/reset-password", {
        email: data.email,
        otp: data.otp,
        new_password: data.newPassword,
      });
      toast.success("Contraseña actualizada. Ya podés iniciar sesión.");
      router.push("/login");
    } catch (err) {
      toast.error(
        err.response?.data?.message || "El código es inválido o expiró. Solicitá uno nuevo."
      );
    }
  };

  return (
    <div className="auth-card ui-card space-y-8">
      <div>
        <p className="eyebrow">UN NUEVO ACCESO</p>
        <h2 className="mt-3 text-3xl tracking-tight text-ink">
          Restablecer contraseña
        </h2>
        <p className="mt-3 text-sm text-ink/60">
          Ingresá el código que recibiste por correo y tu nueva contraseña
        </p>
      </div>

      <form className="mt-8 space-y-4" onSubmit={handleSubmit(onSubmit)}>
        <div>
          <label htmlFor="email" className="ui-label">Correo electrónico</label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            {...register("email")}
            className="ui-input"
            placeholder="Correo electrónico"
          />
          {errors.email && <p className="mt-1 text-sm text-red-500">{errors.email.message}</p>}
        </div>

        <div>
          <label htmlFor="otp" className="ui-label">Código</label>
          <input
            id="otp"
            type="text"
            inputMode="numeric"
            {...register("otp")}
            className="ui-input"
            placeholder="Código recibido por correo"
          />
          {errors.otp && <p className="mt-1 text-sm text-red-500">{errors.otp.message}</p>}
        </div>

        <div>
          <label htmlFor="newPassword" className="ui-label">Nueva contraseña</label>
          <input
            id="newPassword"
            type="password"
            autoComplete="new-password"
            {...register("newPassword")}
            className="ui-input"
            placeholder="Nueva contraseña"
          />
          {errors.newPassword && <p className="mt-1 text-sm text-red-500">{errors.newPassword.message}</p>}
        </div>

        <div>
          <label htmlFor="confirmPassword" className="ui-label">Confirmar contraseña</label>
          <input
            id="confirmPassword"
            type="password"
            autoComplete="new-password"
            {...register("confirmPassword")}
            className="ui-input"
            placeholder="Confirmar nueva contraseña"
          />
          {errors.confirmPassword && <p className="mt-1 text-sm text-red-500">{errors.confirmPassword.message}</p>}
        </div>

        <div>
          <button
            type="submit"
            disabled={isSubmitting}
            className="ui-primary group relative flex w-full justify-center px-3 py-3 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent transition-all disabled:opacity-50 disabled:cursor-not-allowed"
          >
            {isSubmitting ? "Actualizando..." : "Restablecer contraseña"}
          </button>
        </div>
      </form>

      <div className="text-center text-sm">
        <Link href="/login" className="font-medium text-link hover:text-ink transition-colors">
          Volver al inicio de sesión
        </Link>
      </div>
    </div>
  );
}

export default function ResetPasswordPage() {
  return (
    <AuthLayout>
      <Suspense fallback={
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent" />
      }>
        <ResetPasswordForm />
      </Suspense>
    </AuthLayout>
  );
}
