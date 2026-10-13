import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import DashboardHeader from "@/components/DashboardHeader";
import ControlAccesoContent from "./ControlAccesoContent";

export default async function AccesosPage() {
  await requireAuth(["ADMIN", "EMPLEADO"]);
  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader>
          <Link href="/dashboard-admin" className="dashboard-back-link">← Volver al panel</Link>
        </DashboardHeader>
        <ControlAccesoContent />
      </div>
    </div>
  );
}
