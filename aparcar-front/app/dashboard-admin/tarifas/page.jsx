import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import DashboardHeader from "@/components/DashboardHeader";
import TarifasManagement from "./TarifasManagement";

export default async function TarifasPage() {
  await requireAuth(["ADMIN"]);
  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader>
          <Link href="/dashboard-admin" className="dashboard-back-link">← Volver al panel</Link>
        </DashboardHeader>
        <TarifasManagement />
      </div>
    </div>
  );
}
