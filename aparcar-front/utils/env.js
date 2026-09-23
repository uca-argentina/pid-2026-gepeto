// Next.js reemplaza `process.env.NEXT_PUBLIC_*` por su valor real al compilar,
// pero solo si la referencia es literal (no funciona con `process.env[key]`
// dinámico). Por eso las variables se listan acá una sola vez, de forma
// literal, para que el build las pueda inlinear correctamente.
const PUBLIC_ENV = {
    NEXT_PUBLIC_API_BASE_URL: process.env.NEXT_PUBLIC_API_BASE_URL,
};

export const getEnv = (key) => {
    if (typeof window !== "undefined" && window.__ENV && window.__ENV[key]) {
        return window.__ENV[key];
    }
    return PUBLIC_ENV[key];
};