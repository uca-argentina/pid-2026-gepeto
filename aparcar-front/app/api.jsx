import axios from "axios";

import { getEnv } from "../utils/env";

const api = axios.create({
  headers: {
    "Content-Type": "application/json",
  },
});

api.interceptors.request.use((config) => {
  config.baseURL = getEnv("NEXT_PUBLIC_API_BASE_URL");

  // No pisar un Authorization que la propia llamada ya haya seteado a mano
  // (ej. el Basic Auth de /login) con el Bearer de una cookie vieja.
  if (!config.headers.Authorization) {
    let token = null;
    if (typeof document !== "undefined") {
      const match = document.cookie.match(new RegExp('(^| )JWT=([^;]+)'));
      if (match) token = match[2];
    }

    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      document.cookie = 'JWT=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';

      if (typeof window !== "undefined") {
        window.location.href = "/login";
      }
    }
    return Promise.reject(error);
  }
);

export default api; 