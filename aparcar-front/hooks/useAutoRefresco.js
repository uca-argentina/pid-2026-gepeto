"use client";

import { useEffect, useRef } from "react";

/**
 * Vuelve a pedir datos cada tanto, sin que se note.
 *
 * Las pantallas del panel muestran algo que cambia por fuera: mientras el admin
 * mira la ocupación, alguien reserva desde su teléfono y la cochera que ve
 * libre ya no lo está. Sin esto hay que apretar F5 para enterarse.
 *
 * "Que no se note" es el requisito, y no sale solo de poner un `setInterval`.
 * Son cuatro cosas:
 *
 *   1. Nunca mientras la pestaña está oculta. No tiene sentido consultar un
 *      panel que nadie está mirando, y en una pestaña de fondo cada consulta es
 *      batería y datos del visitante a cambio de nada.
 *   2. Al volver a la pestaña, consulta enseguida en vez de esperar al próximo
 *      turno. Si no, al volver ves datos viejos justo cuando más atención
 *      prestás.
 *   3. Nunca dos consultas superpuestas. Con una red lenta, un intervalo corto
 *      encadena pedidos que se pisan y llegan desordenados, dejando en pantalla
 *      la respuesta más vieja.
 *   4. El refresco es silencioso: quien lo usa no prende el estado de carga ni
 *      avisa de errores. Eso corre por cuenta de cada pantalla, en su propia
 *      función de recarga, porque un esqueleto parpadeando cada veinte segundos
 *      o un aviso de error ante un microcorte serían justo lo contrario de
 *      imperceptible.
 *
 * @param {() => (void | Promise<void>)} alRefrescar Qué volver a pedir. Tiene
 *        que ser silencioso: sin estados de carga ni avisos.
 * @param {object} [opciones]
 * @param {number} [opciones.intervalo=20000] Milisegundos entre consultas.
 * @param {boolean} [opciones.activo=true] En false no consulta nada. Sirve para
 *        suspender el refresco mientras hay algo a medio hacer en pantalla.
 */
export function useAutoRefresco(alRefrescar, { intervalo = 20000, activo = true } = {}) {
  // La referencia evita que el efecto se reinicie en cada render: la función
  // que llega suele definirse dentro del componente, así que su identidad
  // cambia siempre, y sin esto el intervalo se destruiría y recrearía sin parar
  // sin llegar a cumplirse nunca.
  const callback = useRef(alRefrescar);
  callback.current = alRefrescar;

  useEffect(() => {
    if (!activo) return undefined;

    let vigente = true;
    let enVuelo = false;

    const refrescar = async () => {
      if (!vigente || enVuelo) return;
      if (typeof document !== "undefined" && document.visibilityState !== "visible") return;

      enVuelo = true;
      try {
        await callback.current?.();
      } catch {
        // Se ignora a propósito: el próximo turno reintenta. Un corte de red
        // momentáneo deja lo que ya estaba en pantalla, que es más útil que
        // vaciarlo o interrumpir con un cartel.
      } finally {
        enVuelo = false;
      }
    };

    const alVolver = () => {
      if (document.visibilityState === "visible") refrescar();
    };

    const temporizador = window.setInterval(refrescar, intervalo);
    document.addEventListener("visibilitychange", alVolver);
    window.addEventListener("focus", refrescar);

    return () => {
      vigente = false;
      window.clearInterval(temporizador);
      document.removeEventListener("visibilitychange", alVolver);
      window.removeEventListener("focus", refrescar);
    };
  }, [intervalo, activo]);
}

export default useAutoRefresco;
