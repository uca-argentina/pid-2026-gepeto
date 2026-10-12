import Brand from "@/components/Brand";
import AccountMenu from "@/components/AccountMenu";

export default function DashboardHeader({ children, actions, visitor = false }) {
  return (
    <header className="dashboard-header">
      <div className="dashboard-toolbar">
        <div className="dashboard-identity">
          <Brand />
          <span className="workspace-label">{visitor ? "Mi espacio" : "Administración"}</span>
        </div>
        <div className="dashboard-account">{actions ?? <AccountMenu visitor={visitor} />}</div>
      </div>
      {children && <nav className="dashboard-navigation" aria-label={visitor ? "Mi cuenta" : "Administración"}>{children}</nav>}
    </header>
  );
}
