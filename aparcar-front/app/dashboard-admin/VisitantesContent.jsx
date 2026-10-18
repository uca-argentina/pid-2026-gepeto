"use client";

import { useEffect, useMemo } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";
import api from "@/app/api";
import { formatoPatenteValido, MENSAJE_FORMATO_INVALIDO, normalizarPatente } from "@/utils/patenteValidation";
import AtajosJornada from "@/components/AtajosJornada";
import CotizacionReserva from "@/components/CotizacionReserva";
import AvisoListaEspera from "@/components/AvisoListaEspera";
import useCotizacion from "@/hooks/useCotizacion";
import useDisponibilidad from "@/hooks/useDisponibilidad";
import {
  PASO_MINUTOS,
  franjaPorDefecto,
  redondearAlBloqueLocal,
} from "@/utils/franjaHoraria";

const visitanteSchema = z
  .object({
    nombre: z.string().min(1, "El nombre es obligatorio"),
    documento: z.string().min(1, "El documento es obligatorio"),
    email: z.string().min(1, "El email es obligatorio").email("Ingresa un correo válido"),
    telefono: z.string().optional(),
    patente: z.string().transform(normalizarPatente).pipe(z.string().min(1, "La patente es obligatoria")),
    tipoVehiculo: z.enum(["AUTO", "MOTO", "CARGA"], {
      message: "Selecciona un tipo de vehículo",
    }),
    cocheraId: z.string().optional(),
    listaEspera: z.boolean().optional(),
    desde: z.string().min(1, "Elegí desde cuándo"),
    hasta: z.string().min(1, "Elegí hasta cuándo"),
  })
  .superRefine((data, ctx) => {
    if (!data.listaEspera && !data.cocheraId) {
      ctx.addIssue({ code: z.ZodIssueCode.custom, path: ["cocheraId"], message: "Selecciona una cochera" });
    }
    if (data.desde && data.hasta && data.hasta <= data.desde) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["hasta"],
        message: "El fin tiene que ser posterior al inicio",
      });
    }
    if (!formatoPatenteValido(data.patente, data.tipoVehiculo)) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        path: ["patente"],
        message: MENSAJE_FORMATO_INVALIDO[data.tipoVehiculo],
      });
    }
  });

const inputClasses =
  "ui-input";
const labelClasses = "ui-label";

// Da de alta un visitante: crea la cuenta, su vehículo y la reserva para la
// franja elegida en una sola llamada. El backend lo resuelve
// en una transacción, así que o entra todo o no entra nada — antes esto eran
// dos llamadas sueltas y si la segunda fallaba quedaba un visitante huérfano.
//
// `onAltaCreada` es opcional: el panel lo usa para refrescar la ocupación y el
// listado de reservas, que viven en componentes hermanos.
export default function VisitantesContent({ onAltaCreada }) {
  const {
    register,
    handleSubmit,
    watch,
    reset,
    setValue,
    setError,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(visitanteSchema),
    defaultValues: { tipoVehiculo: "AUTO", cocheraId: "", listaEspera: false, ...franjaPorDefecto() },
  });

  const tipoVehiculo = watch("tipoVehiculo");
  const desde = watch("desde");
  const hasta = watch("hasta");
  const cocheraId = watch("cocheraId");
  const listaEspera = watch("listaEspera");
  const rangoValido = Boolean(desde && hasta && hasta > desde);
  const disponibilidad = useDisponibilidad(tipoVehiculo, desde, hasta);
  const cocheras = disponibilidad.cocheras;

  // El backend le muestra todas las cocheras al admin (porque suele reservar
  // para otra persona) y rechaza al confirmar una ACCESIBLE para alguien sin
  // la discapacidad verificada. Una cuenta que se está creando nunca la tiene
  // (se verifica después, con el certificado), así que no se ofrecen.
  const cocherasOfrecidas = useMemo(
    () => cocheras.filter((c) => c.tipo !== "ACCESIBLE"),
    [cocheras]
  );
  const hayAccesiblesOcultas = cocherasOfrecidas.length < cocheras.length;
  const cocheraElegida = cocherasOfrecidas.find((c) => c.id === cocheraId);
  const puedeEsperar = disponibilidad.lista && cocherasOfrecidas.length === 0;
  const enEspera = puedeEsperar && listaEspera;
  const cotizacion = useCotizacion(cocheraElegida?.tipo ?? tipoVehiculo, desde, hasta);

  // Si la cochera elegida deja de estar en la lista (cambió la franja o el
  // tipo), se limpia la selección para no mandar una opción que ya no se ve.
  useEffect(() => {
    if (cocheraId && !cocherasOfrecidas.some((c) => c.id === cocheraId)) {
      setValue("cocheraId", "");
    }
  }, [cocheraId, cocherasOfrecidas, setValue]);

  useEffect(() => {
    setValue("cocheraId", "");
    setValue("listaEspera", false);
  }, [disponibilidad.solicitud, setValue]);

  const onSubmit = async (data) => {
    if (!cotizacion.lista || (!cocheraElegida && !enEspera) || !rangoValido) return;
    try {
      const res = await api.post("/api/v1/visitantes/alta", {
        nombre: data.nombre,
        documento: data.documento,
        email: data.email,
        telefono: data.telefono || undefined,
        patente: data.patente,
        tipoVehiculo: data.tipoVehiculo,
        cocheraId: enEspera ? null : data.cocheraId,
        ...(enEspera ? { listaEspera: true, tipoCochera: data.tipoVehiculo } : {}),
        // Cinturón: el blur ya lo acomoda, pero el valor también puede venir
        // de un atajo o de un reset.
        desde: redondearAlBloqueLocal(data.desde),
        hasta: redondearAlBloqueLocal(data.hasta),
        precioEsperado: cotizacion.datos.total,
      });

      toast.success(
        `Visitante dado de alta y ${res.data?.reserva?.estado === "LISTA_ESPERA" ? "reserva en lista de espera" : "cochera reservada"}. Su contraseña inicial es su documento (${data.documento}).`
      );
      reset({ tipoVehiculo: "AUTO", cocheraId: "", listaEspera: false, ...franjaPorDefecto() });
      disponibilidad.recargar();
      onAltaCreada?.();
    } catch (err) {
      cotizacion.recargar();
      disponibilidad.recargar();
      const respuesta = err.response?.data;
      let mensaje = respuesta?.message || "Ocurrió un error al dar de alta al visitante.";
      if (mensaje === "Validation failed") {
        const detalles = Array.isArray(respuesta.details) ? respuesta.details.filter((detalle) => typeof detalle === "string") : [];
        const campos = ["nombre", "documento", "email", "telefono", "patente", "tipoVehiculo", "cocheraId", "desde", "hasta"];
        const mensajes = detalles.map((detalle) => {
          const separador = detalle.indexOf(": ");
          const campo = detalle.slice(0, separador);
          if (separador > 0 && campos.includes(campo)) {
            const message = detalle.slice(separador + 2);
            setError(campo, { type: "server", message });
            return message;
          }
          return detalle;
        });
        mensaje = mensajes.join(" ") || "Revisá los datos del visitante y del vehículo antes de continuar.";
      }
      setError("root", { message: mensaje });
      toast.error(mensaje);
    }
  };

  return (
    <div className="visitor-section">
      <p className="eyebrow mb-3">RECEPCIÓN DE VISITANTES</p>
      <h1 className="text-3xl font-extrabold tracking-tight text-ink mb-2">
        Nuevo visitante
      </h1>
      <p className="text-sm text-ink/60 mb-8">
        Cargá sus datos y su vehículo, y elegí la franja y la cochera de la reserva. Queda con
        cuenta creada y su documento como contraseña inicial.
      </p>

      <div className="ui-card p-8">
        <form className="space-y-6" onSubmit={handleSubmit(onSubmit)}>
            <div>
              <h2 className="text-sm font-semibold text-link uppercase tracking-wide mb-4">
                Datos del visitante
              </h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className={labelClasses} htmlFor="nombre">Nombre</label>
                  <input id="nombre" {...register("nombre")} className={inputClasses} placeholder="Nombre completo" />
                  {errors.nombre && <p className="mt-1 text-sm text-red-500">{errors.nombre.message}</p>}
                </div>
                <div>
                  <label className={labelClasses} htmlFor="documento">Documento</label>
                  <input id="documento" {...register("documento")} className={inputClasses} placeholder="DNI / documento" />
                  {errors.documento && <p className="mt-1 text-sm text-red-500">{errors.documento.message}</p>}
                </div>
                <div>
                  <label className={labelClasses} htmlFor="email">Email</label>
                  <input id="email" type="email" {...register("email")} className={inputClasses} placeholder="Con esto inicia sesión" />
                  {errors.email && <p className="mt-1 text-sm text-red-500">{errors.email.message}</p>}
                </div>
                <div>
                  <label className={labelClasses} htmlFor="telefono">Teléfono (opcional)</label>
                  <input id="telefono" {...register("telefono")} className={inputClasses} placeholder="Teléfono" />
                </div>
                <p className="sm:col-span-2 text-xs text-ink/50">
                  Si la persona tiene una discapacidad, puede pedir la verificación desde su perfil, subiendo su
                  certificado. Las cocheras accesibles se habilitan cuando un administrador lo aprueba.
                </p>
              </div>
            </div>

            <div>
              <h2 className="text-sm font-semibold text-link uppercase tracking-wide mb-4">
                Vehículo y cochera
              </h2>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div>
                  <label className={labelClasses} htmlFor="patente">Patente</label>
                  <input
                    id="patente"
                    {...register("patente")}
                    className={`${inputClasses} uppercase`}
                    placeholder={tipoVehiculo === "MOTO" ? "123ABC / A123BCD" : "ABC123 / AB123CD"}
                  />
                  {errors.patente && <p className="mt-1 text-sm text-red-500">{errors.patente.message}</p>}
                </div>
                <div>
                  <label className={labelClasses} htmlFor="tipoVehiculo">Tipo de vehículo</label>
                  <select id="tipoVehiculo" {...register("tipoVehiculo")} className={inputClasses}>
                    <option value="AUTO">Auto</option>
                    <option value="MOTO">Moto</option>
                    <option value="CARGA">Carga</option>
                  </select>
                  {errors.tipoVehiculo && <p className="mt-1 text-sm text-red-500">{errors.tipoVehiculo.message}</p>}
                </div>
                <div>
                  <label className={labelClasses} htmlFor="alta-desde">Desde</label>
                  {/* Lo que se escribe se redondea al bloque de 15 más
                      cercano al salir del campo, no en cada tecla: acomodarlo
                      en el momento impedía escribir los minutos. */}
                  <input
                    id="alta-desde"
                    type="datetime-local"
                    step={PASO_MINUTOS * 60}
                    {...register("desde", {
                      onBlur: (e) =>
                        setValue("desde", redondearAlBloqueLocal(e.target.value), {
                          shouldValidate: true,
                        }),
                    })}
                    className={inputClasses}
                  />
                  {errors.desde && <p className="mt-1 text-sm text-red-500">{errors.desde.message}</p>}
                </div>
                <div>
                  <label className={labelClasses} htmlFor="alta-hasta">Hasta</label>
                  <input
                    id="alta-hasta"
                    type="datetime-local"
                    step={PASO_MINUTOS * 60}
                    min={desde}
                    {...register("hasta", {
                      onBlur: (e) =>
                        setValue("hasta", redondearAlBloqueLocal(e.target.value), {
                          shouldValidate: true,
                        }),
                    })}
                    className={inputClasses}
                  />
                  {errors.hasta && <p className="mt-1 text-sm text-red-500">{errors.hasta.message}</p>}
                  {desde && hasta && hasta <= desde && (
                    <p className="mt-1 text-sm text-red-500">El fin tiene que ser posterior al inicio</p>
                  )}
                </div>
                <div className="sm:col-span-2">
                  <AtajosJornada
                    idPrefijo="alta"
                    desde={desde}
                    hasta={hasta}
                    onChange={(h) => setValue("hasta", h, { shouldValidate: true })}
                  />
                </div>
                <div className="sm:col-span-2">
                  <label className={labelClasses} htmlFor="alta-cocheraId">Cochera</label>
                  <select id="alta-cocheraId" {...register("cocheraId")} className={inputClasses} disabled={!rangoValido || !tipoVehiculo || !disponibilidad.lista || cocherasOfrecidas.length === 0}>
                    <option value="">Seleccioná una cochera</option>
                    {cocherasOfrecidas.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.numero} — {c.sector} ({c.tipo})
                      </option>
                    ))}
                  </select>
                  {errors.cocheraId && <p className="mt-1 text-sm text-red-500">{errors.cocheraId.message}</p>}
                  {disponibilidad.cargando && <p className="mt-1 text-sm text-ink/50">Consultando disponibilidad…</p>}
                  {disponibilidad.error && <div className="mt-1 text-sm text-red-500" role="alert">
                    <p>{disponibilidad.error}</p>
                    <button type="button" className="font-semibold underline" onClick={disponibilidad.recargar}>Reintentar disponibilidad</button>
                  </div>}
                  {disponibilidad.lista && cocherasOfrecidas.length === 0 && (
                    <p className="mt-1 text-sm text-ink/50">
                      No hay cocheras libres durante toda esa franja para ese tipo de vehículo.
                    </p>
                  )}
                  {puedeEsperar && <AvisoListaEspera id="alta-lista-espera" registro={register("listaEspera")} tipoCochera={tipoVehiculo} />}
                  {hayAccesiblesOcultas && (
                    <p className="mt-1 text-xs text-ink/50">
                      Las cocheras accesibles no se ofrecen en el alta: requieren el certificado verificado.
                    </p>
                  )}
                </div>
              </div>
            </div>

            <CotizacionReserva cotizacion={cotizacion} />

            {errors.root && <p role="alert" className="text-sm text-red-500">{errors.root.message}</p>}

            <div>
              <button
                type="submit"
                disabled={isSubmitting || !cotizacion.lista}
                className="ui-primary group relative flex w-full justify-center px-3 py-3 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent transition-all disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isSubmitting ? "Guardando..." : enEspera ? "Dar de alta e ingresar a lista de espera" : "Dar de alta y reservar"}
              </button>
            </div>
        </form>
      </div>
    </div>
  );
}
