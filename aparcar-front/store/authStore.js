import { create } from 'zustand';
import { jwtDecode } from 'jwt-decode';

// El backend emite el JWT con un claim "authorities" como string separado por
// comas (ej: "USER,ADMIN"), no con un campo "role" único. Esta función lo
// normaliza a un array de roles (["USER", "ADMIN"]) para que el resto del
// front no tenga que lidiar con el parsing.
function decodeUser(token) {
  const decoded = jwtDecode(token);
  const roles = decoded.authorities
    ? decoded.authorities.split(',').map((r) => r.trim()).filter(Boolean)
    : [];
  return { ...decoded, roles };
}

export const useAuthStore = create((set) => ({
  isAuthenticated: false,
  user: null,
  isHydrated: false,
  setAuth: (token) => {
    if (typeof window !== "undefined") {
      // Guardar en cookie para que el servidor Next.js y el cliente lo puedan leer
      document.cookie = `JWT=${token}; path=/; max-age=604800; SameSite=Lax`;

      try {
        const user = decodeUser(token);
        set({ isAuthenticated: true, user });
      } catch (e) {
        set({ isAuthenticated: false, user: null });
      }
    }
  },
  logout: () => {
    if (typeof window !== "undefined") {
      document.cookie = 'JWT=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
    }
    set({ isAuthenticated: false, user: null });
  },
  checkAuth: () => {
    if (typeof window !== "undefined") {
      const match = document.cookie.match(new RegExp('(^| )JWT=([^;]+)'));
      const token = match ? match[2] : null;

      if (token) {
        try {
          const user = decodeUser(token);

          // Verificar expiración si el token lo tiene (exp está en segundos)
          if (user.exp && user.exp * 1000 < Date.now()) {
            document.cookie = 'JWT=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
            set({ isAuthenticated: false, user: null, isHydrated: true });
            return;
          }

          set({ isAuthenticated: true, user, isHydrated: true });
        } catch (e) {
          document.cookie = 'JWT=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
          set({ isAuthenticated: false, user: null, isHydrated: true });
        }
      } else {
        set({ isAuthenticated: false, user: null, isHydrated: true });
      }
    }
  }
}));
