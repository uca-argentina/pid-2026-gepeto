const paths = {
  profile: <><circle cx="12" cy="8" r="4" /><path d="M4 21v-2a8 8 0 0 1 16 0v2" /></>,
  chevron: <path d="m7 10 5 5 5-5" />,
  shield: <><path d="m12 3 8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3Z" /><path d="m8 12 3 3 5-5" /></>,
  tarifas: <><rect x="3" y="5" width="18" height="14" rx="3" /><path d="M3 10h18m-6 5h3" /></>,
  parking: <><rect x="4" y="3" width="16" height="18" rx="4" /><path d="M10 17V7h3a3 3 0 0 1 0 6h-3" /></>,
  calendar: <><rect x="3" y="5" width="18" height="16" rx="3" /><path d="M7 3v4m10-4v4M3 11h18m-13 5h2m4 0h2" /></>,
  users: <><circle cx="9" cy="8" r="3" /><path d="M3 21v-2a6 6 0 0 1 12 0v2m1-16a3 3 0 0 1 0 6m2 4a5 5 0 0 1 3 4v2" /></>,
  "user-plus": <><circle cx="9" cy="8" r="3" /><path d="M3 21v-2a6 6 0 0 1 12 0v2M18 8v6m3-3h-6" /></>,
  logout: <><path d="M9 4H5a2 2 0 0 0-2 2v12a2 2 0 0 0 2 2h4m6-4 4-4-4-4m-7 4h11" /></>,
  car: <><path d="M5 17H3v-5l2-5h14l2 5v5h-2" /><path d="M3 12h18M9 17h6" /><circle cx="7" cy="17" r="2" /><circle cx="17" cy="17" r="2" /></>,
};

export default function DashboardIcon({ name }) {
  return (
    <svg className="dashboard-icon" aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      {paths[name]}
    </svg>
  );
}