import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import DashboardHeader from "@/components/DashboardHeader";
import DashboardContent from "./DashboardContent";
import ResumenPuntos from "./ResumenPuntos";

export default async function DashboardPage() {
  await requireAuth(["ADMIN"]);

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader>
          <Link href="/dashboard-admin" className="dashboard-back-link">← Volver al panel</Link>
        </DashboardHeader>
        <DashboardContent />
        <ResumenPuntos />
      </div>
    </div>
  );
}
