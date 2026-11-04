import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import DashboardHeader from "@/components/DashboardHeader";
import VerificacionesContent from "./VerificacionesContent";

export default async function VerificacionesPage() {
  // Solo ADMIN: los certificados de discapacidad son datos de salud.
  await requireAuth(["ADMIN"]);
  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader>
          <Link href="/dashboard-admin" className="dashboard-back-link">← Volver al panel</Link>
        </DashboardHeader>
        <VerificacionesContent />
      </div>
    </div>
  );
}
