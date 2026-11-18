import CheckOutlinedIcon from "@mui/icons-material/CheckOutlined";
import ArrowDropDownOutlinedIcon from "@mui/icons-material/ArrowDropDownOutlined";
import {
  demoLoginEnabled,
  demoPassword,
  demoRolePresets,
} from "@/features/auth/demo";
import React, { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { useAuth } from "@/features/auth/contexts/AuthContext";

export const RoleSwitcher: React.FC = () => {
  const { t } = useTranslation(["auth", "common"]);
  const { user, switchRole } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const containerRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    const handler = (ev: MouseEvent) => {
      if (!containerRef.current) return;
      if (!containerRef.current.contains(ev.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handler);
    return () => document.removeEventListener("mousedown", handler);
  }, []);

  async function handleSwitch(email: string) {
    setBusy(true);
    setError("");
    try {
      await switchRole(email);
      setOpen(false);
      // Reload the home route so data reflects the new role.
      navigate("/", { replace: true });
    } catch (cause) {
      setError(
        cause instanceof Error ? cause.message : t("auth:errors.unknown"),
      );
    } finally {
      setBusy(false);
    }
  }

  if (!user) return null;

  const role = user.role || "USER";

  return (
    <div
      ref={containerRef}
      style={{ position: "relative", display: "inline-block" }}
    >
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        disabled={busy}
        className="role-switcher-trigger"
        aria-haspopup="menu"
        aria-expanded={open}
        title={t("auth:switchRoleTooltip", "Chuyển vai trò test")}
      >
        <span className="role-switcher-info">
          <span className={`role-badge role-${role}`}>{role}</span>
        </span>
        <ArrowDropDownOutlinedIcon fontSize="small" aria-hidden="true" />
      </button>

      {open && (
        <div className="role-switcher-menu" role="menu">
          <div className="role-switcher-header">
            <strong>{user.email}</strong>
            <small>
              {t("auth:switchAccountHeader", "Chuyển vai trò test nhanh")}
            </small>
          </div>

          {error && <p role="alert">{error}</p>}
          {demoLoginEnabled &&
            demoPassword &&
            demoRolePresets.map((preset) => {
              const isCurrent = preset.email === user.email;
              return (
                <button
                  type="button"
                  key={preset.email}
                  disabled={busy || isCurrent}
                  onClick={() => handleSwitch(preset.email)}
                  className={`role-switcher-item ${isCurrent ? "is-current" : ""}`}
                  role="menuitem"
                >
                  <span className={`role-badge role-${preset.role}`}>
                    {preset.role}
                  </span>
                  <span className="role-switcher-item-info">
                    <span>{t(`auth:roles.${preset.role}`)}</span>
                    <small>{preset.email}</small>
                  </span>
                  {isCurrent && (
                    <span className="role-switcher-current-mark" aria-hidden>
                      <CheckOutlinedIcon fontSize="small" />
                    </span>
                  )}
                </button>
              );
            })}
        </div>
      )}
    </div>
  );
};
