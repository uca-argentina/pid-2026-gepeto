"use client";

import { useId, useRef, useState, useEffect } from "react";
import Link from "next/link";
import DashboardIcon from "@/components/DashboardIcon";
import LogoutButton from "@/components/LogoutButton";

export default function AccountMenu({ visitor = false }) {
  const [open, setOpen] = useState(false);
  const root = useRef(null);
  const trigger = useRef(null);
  const id = useId();

  useEffect(() => {
    if (!open) return;
    const dismiss = (event) => {
      if (!root.current?.contains(event.target)) setOpen(false);
    };
    const escape = (event) => {
      if (event.key === "Escape") {
        setOpen(false);
        trigger.current?.focus();
      }
    };
    document.addEventListener("pointerdown", dismiss);
    document.addEventListener("keydown", escape);
    return () => {
      document.removeEventListener("pointerdown", dismiss);
      document.removeEventListener("keydown", escape);
    };
  }, [open]);

  return (
    <div className="account-menu" ref={root} onBlur={(event) => {
      if (!event.currentTarget.contains(event.relatedTarget)) setOpen(false);
    }}>
      <button type="button" className="account-trigger" ref={trigger}
        aria-expanded={open} aria-controls={id} onClick={() => setOpen(!open)}>
        <span className="account-avatar" aria-hidden="true"><DashboardIcon name="profile" /></span>
        <span className="account-trigger-label">Mi cuenta</span>
        <DashboardIcon name="chevron" />
      </button>
      {open && (
        <div className="account-dropdown" id={id}>
          <div className="account-dropdown-heading">
            <span className="eyebrow">TU ESPACIO</span>
            <p>{visitor ? "Cuenta de visitante" : "Administración"}</p>
          </div>
          <Link href={visitor ? "/dashboard-user/perfil" : "/dashboard-admin/perfil"}
            className="account-menu-item" onClick={() => setOpen(false)}>
            <DashboardIcon name="profile" />
            <span>Mis datos<span className="account-item-description">{visitor ? "Perfil, vehículos y seguridad" : "Perfil, estacionamiento y seguridad"}</span></span>
          </Link>
          <div className="account-menu-divider" />
          <LogoutButton className="account-menu-item account-menu-logout" />
        </div>
      )}
    </div>
  );
}