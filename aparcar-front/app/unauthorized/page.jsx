"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuthStore } from "../../store/authStore";

export default function UnauthorizedPage() {
  const { user } = useAuthStore();
  const router = useRouter();

  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-bg px-4">
      <div className="flex flex-col items-center max-w-lg text-center space-y-6">
        <div className="flex h-20 w-20 items-center justify-center rounded-full bg-red-50">
          <svg className="h-10 w-10 text-red-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
          </svg>
        </div>

        <h1 className="text-4xl font-extrabold text-ink tracking-tight">Acceso Denegado</h1>

        <p className="text-ink/60 text-lg">
          No tienes los permisos necesarios para ver esta página.
          {user && ` Estás identificado como '${user.roles?.join(', ') || 'usuario sin rol'}'.`}
        </p>

        <div className="flex gap-4 pt-4">
          <button
            onClick={() => router.back()}
            className="rounded-xl bg-surface px-6 py-3 text-sm font-semibold text-ink hover:bg-ink/5 transition-all border border-ink/20"
          >
            Volver atrás
          </button>

          <Link
            href="/"
            className="ui-primary px-6 py-3 text-sm font-semibold transition-all"
          >
            Ir al inicio
          </Link>
        </div>
      </div>
    </div>
  );
}