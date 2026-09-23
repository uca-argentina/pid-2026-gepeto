"use client";

import { useRouter } from "next/navigation";

import { useAuthStore } from "@/store/authStore";
import DashboardIcon from "@/components/DashboardIcon";

export default function LogoutButton() {
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
      className="logout-button"
      title="Cerrar sesión"
    >
      <DashboardIcon name="logout" />
      <span className="logout-label">Cerrar sesión</span>
    </button>
  );
}
