/**
 * Ayudas de la verificación de discapacidad. La validación del archivo es
 * solo una comodidad para avisar antes de subir: el backend vuelve a validar
 * todo, incluido el contenido real del archivo.
 */

export const TIPOS_CERTIFICADO = {
  extensiones: ["pdf", "jpg", "jpeg", "png"],
  mime: ["application/pdf", "image/jpeg", "image/png"],
  accept: ".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png",
};

/** Devuelve el problema del archivo, o null si se puede enviar. */
export const validarCertificado = (archivo, maxMb) => {
  if (!archivo) return "Elegí un archivo.";
  const extension = archivo.name?.split(".").pop()?.toLowerCase() ?? "";
  if (!TIPOS_CERTIFICADO.extensiones.includes(extension)
      || (archivo.type && !TIPOS_CERTIFICADO.mime.includes(archivo.type))) {
    return "El certificado tiene que ser un PDF, JPG o PNG.";
  }
  if (archivo.size === 0) return "El archivo está vacío.";
  if (archivo.size > maxMb * 1024 * 1024) return `El certificado no puede superar los ${maxMb} MB.`;
  return null;
};

const dosDigitos = (n) => String(n).padStart(2, "0");

/**
 * "10/10/2026". Acepta un día ("2026-10-10", sin hora: se lee como fecha
 * local, no UTC) o un instante del backend.
 */
export const fechaCorta = (valor) => {
  if (!valor) return "";
  const fecha = /^\d{4}-\d{2}-\d{2}$/.test(valor)
    ? new Date(...valor.split("-").map((parte, i) => Number(parte) - (i === 1 ? 1 : 0)))
    : new Date(valor);
  if (Number.isNaN(fecha.getTime())) return "";
  return `${dosDigitos(fecha.getDate())}/${dosDigitos(fecha.getMonth() + 1)}/${fecha.getFullYear()}`;
};

export const ESTADO_VERIFICACION_ETIQUETA = {
  EN_REVISION: "En revisión",
  VERIFICADA: "Verificada",
  RECHAZADA: "Rechazada",
  REVOCADA: "Revocada",
  VENCIDA: "Vencida",
  CANCELADA: "Cancelada",
};
