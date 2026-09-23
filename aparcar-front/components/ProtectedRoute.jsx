"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { useAuthStore } from "../store/authStore";

export default function ProtectedRoute({ children, allowedRoles = [] }) {
  const { isAuthenticated, user, checkAuth, isHydrated } = useAuthStore();
  const router = useRouter();

  useEffect(() => {
    checkAuth();
  }, [checkAuth]);

  const hasRequiredRole =
    allowedRoles.length === 0 ||
    (user && allowedRoles.some((r) => user.roles?.includes(r)));

  useEffect(() => {
    if (!isHydrated) return;

    if (!isAuthenticated) {
      router.push("/login");
      return;
    }

    if (!hasRequiredRole) {
      router.push("/unauthorized");
    }
  }, [isHydrated, isAuthenticated, hasRequiredRole, router]);

  const canRender = isHydrated && isAuthenticated && hasRequiredRole;

  if (!canRender) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-bg">
        <div className="h-8 w-8 animate-spin rounded-full border-4 border-accent border-t-transparent"></div>
      </div>
    );
  }

  return <>{children}</>;
}