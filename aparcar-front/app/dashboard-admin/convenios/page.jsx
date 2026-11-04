import Link from "next/link";
import { requireAuth } from "@/utils/serverAuth";
import DashboardHeader from "@/components/DashboardHeader";
import ConveniosManagement from "./ConveniosManagement";

export default async function ConveniosPage() {
  await requireAuth(["ADMIN"]);
  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader>
          <Link href="/dashboard-admin" className="dashboard-back-link">← Volver al panel</Link>
        </DashboardHeader>
        <ConveniosManagement />
      </div>
    </div>
  );
}
