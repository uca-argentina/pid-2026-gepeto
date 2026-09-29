"use client";

import { useEffect, useMemo, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { toast } from "sonner";
import api from "@/app/api";
import { formatoPatenteValido, MENSAJE_FORMATO_INVALIDO } from "@/utils/patenteValidation";
import AtajosJornada from "@/components/AtajosJornada";
import CotizacionReserva from "@/components/CotizacionReserva";
import useCotizacion from "@/hooks/useCotizacion";
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
    // Opcional: el admin lo marca si ya sabe que la persona tiene una
    // discapacidad. Habilita reservar cocheras ACCESIBLE.
    tieneDiscapacidad: z.boolean().optional(),
    patente: z.string().min(1, "La patente es obligatoria"),
    tipoVehiculo: z.enum(["AUTO", "MOTO", "CARGA"], {
      message: "Selecciona un tipo de vehículo",
    }),
    cocheraId: z.string().min(1, "Selecciona una cochera"),
    desde: z.string().min(1, "Elegí desde cuándo"),
    hasta: z.string().min(1, "Elegí hasta cuándo"),
  })
  .superRefine((data, ctx) => {
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
  const [cocheras, setCocheras] = useState([]);

  const {
    register,
    handleSubmit,
    watch,
    reset,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm({
    resolver: zodResolver(visitanteSchema),
    defaultValues: { tipoVehiculo: "AUTO", cocheraId: "", tieneDiscapacidad: false, ...franjaPorDefecto() },
  });

  const tipoVehiculo = watch("tipoVehiculo");
  const desde = watch("desde");
  const hasta = watch("hasta");
  const tieneDiscapacidad = watch("tieneDiscapacidad");
  const cocheraId = watch("cocheraId");
  const rangoValido = Boolean(desde && hasta && hasta > desde);

  // El backend le muestra todas las cocheras al admin (porque suele reservar
  // para otra persona) y recién rechaza al confirmar si se elige una ACCESIBLE
  // para alguien sin discapacidad declarada. Acá ya sabemos si la persona que
  // se está dando de alta la tiene (es el checkbox de este mismo formulario),
  // así que no ofrecemos una opción que el backend va a rechazar.
  const cocherasOfrecidas = useMemo(
    () => (tieneDiscapacidad ? cocheras : cocheras.filter((c) => c.tipo !== "ACCESIBLE")),
    [cocheras, tieneDiscapacidad]
  );
  const hayAccesiblesOcultas = cocherasOfrecidas.length < cocheras.length;
  const cocheraElegida = cocherasOfrecidas.find((c) => c.id === cocheraId);
  const cotizacion = useCotizacion(cocheraElegida?.tipo ?? tipoVehiculo, desde, hasta);

  // Si el admin eligió una ACCESIBLE y después desmarca el checkbox, esa
  // cochera deja de estar en la lista: se limpia la selección para no mandar
  // una opción que ya no se ve.
  useEffect(() => {
    if (cocheraId && !cocherasOfrecidas.some((c) => c.id === cocheraId)) {
      setValue("cocheraId", "");
    }
  }, [cocheraId, cocherasOfrecidas, setValue]);

  // La disponibilidad depende de la franja y del tipo de vehículo.
  useEffect(() => {
    let vigente = true;
    setValue("cocheraId", "");
    setCocheras([]);
    if (!tipoVehiculo || !rangoValido) return;

    api
      // Se pregunta por el bloque y no por el horario a medio escribir.
      .get("/api/v1/cocheras/disponibles", {
        params: {
          desde: redondearAlBloqueLocal(desde),
          hasta: redondearAlBloqueLocal(hasta),
          tipoVehiculo,
        },
      })
      .then((res) => {
        if (vigente) setCocheras(res.data);
      })
      .catch(() => {
        if (vigente) toast.error("No se pudieron cargar las cocheras disponibles.");
      });
    return () => { vigente = false; };
  }, [tipoVehiculo, desde, hasta, rangoValido, setValue]);

  const onSubmit = async (data) => {
    if (!cotizacion.lista || !cocheraElegida || !rangoValido) return;
    try {
      await api.post("/api/v1/visitantes/alta", {
        nombre: data.nombre,
        documento: data.documento,
        email: data.email,
        telefono: data.telefono || undefined,
        tieneDiscapacidad: Boolean(data.tieneDiscapacidad),
        patente: data.patente,
        tipoVehiculo: data.tipoVehiculo,
        cocheraId: data.cocheraId,
        // Cinturón: el blur ya lo acomoda, pero el valor también puede venir
        // de un atajo o de un reset.
        desde: redondearAlBloqueLocal(data.desde),
        hasta: redondearAlBloqueLocal(data.hasta),
        precioEsperado: cotizacion.datos.total,
      });

      toast.success(
        `Visitante dado de alta y cochera reservada. Su contraseña inicial es su documento (${data.documento}).`
      );
      reset({ tipoVehiculo: "AUTO", cocheraId: "", tieneDiscapacidad: false, ...franjaPorDefecto() });
      onAltaCreada?.();
    } catch (err) {
      cotizacion.recargar();
      toast.error(
        err.response?.data?.message || "Ocurrió un error al dar de alta al visitante."
      );
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
                <div className="sm:col-span-2">
                  <label className="flex cursor-pointer items-center gap-2 text-sm text-ink/70" htmlFor="alta-discapacidad">
                    <input
                      id="alta-discapacidad"
                      type="checkbox"
                      {...register("tieneDiscapacidad")}
                      className="h-4 w-4 rounded border-ink/20 bg-surface accent-accent"
                    />
                    Persona con discapacidad (opcional)
                  </label>
                  <p className="mt-1 text-xs text-ink/50">
                    Marcalo si ya lo sabés: habilita las cocheras accesibles. El visitante lo puede cambiar después desde su perfil.
                  </p>
                </div>
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
                    placeholder="ABC123 / AB123CD"
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
                  <select id="alta-cocheraId" {...register("cocheraId")} className={inputClasses} disabled={!rangoValido || !tipoVehiculo}>
                    <option value="">Seleccioná una cochera</option>
                    {cocherasOfrecidas.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.numero} — {c.sector} ({c.tipo})
                      </option>
                    ))}
                  </select>
                  {errors.cocheraId && <p className="mt-1 text-sm text-red-500">{errors.cocheraId.message}</p>}
                  {rangoValido && cocherasOfrecidas.length === 0 && (
                    <p className="mt-1 text-sm text-ink/50">
                      No hay cocheras libres durante toda esa franja para ese tipo de vehículo.
                    </p>
                  )}
                  {hayAccesiblesOcultas && (
                    <p className="mt-1 text-xs text-ink/50">
                      Las cocheras accesibles aparecen al marcar &quot;Persona con discapacidad&quot;.
                    </p>
                  )}
                </div>
              </div>
            </div>

            <CotizacionReserva cotizacion={cotizacion} />

            <div>
              <button
                type="submit"
                disabled={isSubmitting || !cotizacion.lista}
                className="ui-primary group relative flex w-full justify-center px-3 py-3 text-sm font-semibold focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-accent transition-all disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {isSubmitting ? "Guardando..." : "Dar de alta y reservar"}
              </button>
            </div>
        </form>
      </div>
    </div>
  );
}
