"use client";

import { useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";
import api from "../api";
import AuthLayout from "@/components/AuthLayout";

const recoverSchema = z.object({
  email: z.string().email("Ingresa un correo válido"),
});

export default function RecoverPasswordPage() {
  const [success, setSuccess] = useState(false);
  const router = useRouter();

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(recoverSchema),
  });

  const onSubmit = async (data) => {
    try {
      await api.post("/forgot-password", data);
      setSuccess(true);
      toast.success("Código enviado, revisá tu correo");
      // El backend manda un código OTP por email (no un link), así que
      // llevamos al usuario a la pantalla donde lo ingresa junto con la
      // nueva contraseña.
      setTimeout(() => {
        router.push(`/reset-password?email=${encodeURIComponent(data.email)}`);
      }, 1200);
    } catch (err) {
      toast.error(err.response?.data?.message || "Ocurrió un error al procesar la solicitud.");
    }
  };

  return (
    <AuthLayout>
      <div className="auth-card ui-card space-y-8">
        <div>
          <p className="eyebrow">VOLVÉ A TU CUENTA</p>
          <h2 className="mt-3 text-3xl tracking-tight text-ink">
            Recuperar contraseña
          </h2>
          <p className="mt-3 text-sm text-ink/60">
            Ingresa tu correo para recibir las instrucciones
          </p>
        </div>

        {success ? (
          <div className="rounded-lg bg-accent/10 p-4 border border-accent/50">
            <p className="text-sm text-link text-center">
              Si el correo existe, vas a recibir un código para restablecer tu contraseña. Te redirigimos...
            </p>
          </div>
        ) : (
          <form className="mt-8 space-y-6" onSubmit={handleSubmit(onSubmit)}>
            <div className="space-y-4">
              <div>
                <label htmlFor="email-address" className="ui-label">
                  Correo electrónico
                </label>
                <input
                  id="email-address"
                  type="email"
                  autoComplete="email"
                  {...register("email")}
                  className="ui-input"
                  placeholder="Correo electrónico"
                />
                {errors.email && <p className="mt-1 text-sm text-red-500">{errors.email.message}</p>}
              </div>
            </div>

            <div>
              <button
                type="submit"
                disabled={isSubmitting}
                className="ui-primary group relative flex w-full justify-center px-3 py-3 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent transition-all disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isSubmitting ? "Enviando..." : "Enviar enlace"}
              </button>
            </div>
          </form>
        )}

        <div className="text-center mt-4 text-sm">
          <Link
            href="/login"
            className="font-medium text-link hover:text-ink transition-colors"
          >
            Volver al inicio de sesión
          </Link>
        </div>
      </div>
    </AuthLayout>
  );
}
