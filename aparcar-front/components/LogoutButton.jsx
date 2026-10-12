"use client";

import { useRouter } from "next/navigation";

import { useAuthStore } from "@/store/authStore";
import DashboardIcon from "@/components/DashboardIcon";

export default function LogoutButton({ className = "logout-button" }) {
  const logout = useAuthStore((state) => state.logout);
  const router = useRouter();

  const handleLogout = () => {
    logout();
    router.replace("/login");
  };

  return (
    <button
      type="button"
      onClick={handleLogout}
      className={className}
      title="Cerrar sesión"
    >
      <DashboardIcon name="logout" />
      <span className="logout-label">Cerrar sesión</span>
    </button>
  );
}
