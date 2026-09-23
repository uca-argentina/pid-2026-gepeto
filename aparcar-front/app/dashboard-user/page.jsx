import { requireAuth } from "@/utils/serverAuth";
import LogoutButton from "@/components/LogoutButton";
import PanelVisitante from "./PanelVisitante";
import DashboardHeader from "@/components/DashboardHeader";

export default async function DashboardUserPage() {
  await requireAuth(["USER"]);

  return (
    <div className="dashboard-shell">
      <div className="dashboard-container">
        <DashboardHeader visitor actions={<LogoutButton />} />
        <PanelVisitante />
      </div>
    </div>
  );
}
