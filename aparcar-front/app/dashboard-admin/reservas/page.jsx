import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import LogoutButton from "@/components/LogoutButton";
import PanelReservas from "./PanelReservas";
import DashboardHeader from "@/components/DashboardHeader";

export default async function ReservasAdminPage() {
  await requireAuth(["ADMIN"]);

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader actions={<LogoutButton />}>
          <Link
            href="/dashboard-admin"
            className="dashboard-back-link"
          >
            ← Volver al panel
          </Link>
        </DashboardHeader>

        <PanelReservas />
      </div>
    </div>
  );
}
