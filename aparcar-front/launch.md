## 9. Ayuda memoria

### 9.1 Flujo de Git para empezar una tarea

**Antes de empezar**, partir de `dev` actualizado y crear una rama:

```bash
git checkout dev
git pull origin dev
git checkout -b feature/nombre-de-tu-tarea
```

**Al terminar**, guardar los cambios y subir la rama:

```bash
git add .
git commit -m "Descripción de lo que hiciste"
git push -u origin feature/nombre-de-tu-tarea
```

---

### 9.2 Cuándo correr `docker compose up -d --build server`

El backend corre desde una imagen de Docker que ya tiene el `.jar` compilado. Si cambia algo que forma parte de esa imagen, hay que reconstruirla: `docker compose restart` no alcanza, porque vuelve a usar la imagen anterior.

Desde `aparcar-api-back`:

```bash
docker compose up -d --build server
```

| Qué cambié | Qué hacer |
|---|---|
| Código Java en `src/main/java` | `docker compose up -d --build server` |
| Recursos en `src/main/resources` (`application*.yml`, migraciones de Liquibase) | `docker compose up -d --build server` |
| `pom.xml` o `dockerfiles/server.Dockerfile` | `docker compose up -d --build server` |
| `.env` o `docker-compose.yaml` del backend | `docker compose up -d server` (no hace falta `--build`) |
| Solo tests en `src/test` | No hace falta reconstruir: correr los tests (ver sección 8) |
| Frontend (`aparcar-front`) | No hace falta reconstruir el backend: `npm run dev` toma los cambios solo |

Para confirmar que levantó bien, mirar los logs hasta ver `Started AparcarApiApplication`:

```bash
docker compose logs -f server
```

> **Señal típica de imagen vieja:** el código parece correcto, pero el backend se comporta como antes del cambio. Por ejemplo, un valor nuevo de un enum devuelve `The request body is missing or malformed`.

---

### 9.3 Caso particular: agregar un tipo de vehículo o cochera

Ejemplo real: agregar `BICICLETA`. Además de sumar el valor a los enums y a las listas de opciones del front, hay que acordarse de estos cuatro archivos:

| Archivo | Qué cambiar | Si me lo olvido |
|---|---|---|
| `aparcar-front/app/dashboard-admin/tarifas/TarifasManagement.jsx` | `.length(4)` → `.length(5)` en el schema, texto "16 precios" → "20 precios", y `xl:grid-cols-4` → `xl:grid-cols-5` | Al guardar tarifas **no aparece ningún mensaje** y no se envía el pedido |
| `aparcar-front/utils/patenteValidation.js` | En `formatoPatenteValido`, agregar la regla del tipo nuevo (para `BICICLETA`, devolver `true`) | El front le exige al vehículo una patente con formato de auto |
| `aparcar-api-back/src/main/java/com/aparcar/api/dto/reserva/TarifasRequestDto.java` | `@Size(min = 4, max = 4)` → `@Size(min = 5, max = 5)` | Al guardar tarifas aparece `Validation failed` |
| `aparcar-api-back/src/test/java/com/aparcar/api/integration/TarifaControllerTests.java` | `.value(4)` → `.value(5)` y sumar el tipo a `"AUTO,MOTO,ACCESIBLE,CARGA,BICICLETA"` | Fallan los tests de tarifas |

---

### 9.4 Caso particular: agregar un estado de cochera

Ejemplo real: agregar `MANTENIMIENTO`.

**Back:** sumar el valor a `CocheraEstado.java`. Si el estado nuevo saca la cochera de servicio, sumarlo también a la condición que cancela reservas en `CocheraService.editar()`.

**Front:** sumar el valor al `z.enum` de `estado` y a los 3 `<select>` de `CocherasManagement.jsx`. Después, revisar estos lugares, que comparan contra `"DESHABILITADA"`:

| Archivo | Dónde | Cambio |
|---|---|---|
| `aparcar-front/app/dashboard-admin/cocheras/CocherasManagement.jsx` | Debajo de `TIPO_LABELS` y en la celda `data-label="Estado"` de la tabla | Ver **Badge de estado** más abajo |
| `aparcar-front/app/dashboard-admin/cocheras/CocherasManagement.jsx` | `onEditCochera`, condición del `window.confirm` | `data.estado === "DESHABILITADA"` → `data.estado !== "HABILITADA"` |
| `aparcar-front/components/OcupacionCocheras.jsx` | `const deshabilitada = ...` dentro del `.map` | `=== "DESHABILITADA"` → `!== "HABILITADA"` |
| `aparcar-front/utils/ocupacion.js` | `agruparOcupacion`, campo `deshabilitadas` | `=== "DESHABILITADA"` → `!== "HABILITADA"` |

#### Badge de estado (`CocherasManagement.jsx`)

**1. Agregar** debajo de `const TIPO_LABELS = { ... };` (un estado nuevo = una entrada en cada mapa) (linea 74):

```jsx
const ESTADO_LABELS = {
  HABILITADA: "Habilitada",
  DESHABILITADA: "Deshabilitada",
  MANTENIMIENTO: "Mantenimiento",
};

const ESTADO_ESTILOS = {
  HABILITADA: "bg-accent/10 text-link ring-accent/20",
  DESHABILITADA: "bg-ink/5 text-ink/50 ring-ink/10",
  MANTENIMIENTO:
    "bg-amber-500/10 text-amber-800 ring-amber-600/30 dark:text-amber-300 dark:ring-amber-400/40",
};
```

**2. Reemplazar** el contenido de `<td role="cell" data-label="Estado" ...>` en la tabla (linea 909).

Antes:

```jsx
{cochera.estado === "HABILITADA" ? (
  <span className="rounded-full bg-accent/10 px-2.5 py-1 text-xs font-medium text-link ring-1 ring-inset ring-accent/20">
    Habilitada
  </span>
) : (
  <span className="rounded-full bg-ink/5 px-2.5 py-1 text-xs font-medium text-ink/50 ring-1 ring-inset ring-ink/10">
    Deshabilitada
  </span>
)}
```

Después:

```jsx
<span
  className={`rounded-full px-2.5 py-1 text-xs font-medium ring-1 ring-inset ${
    ESTADO_ESTILOS[cochera.estado] ?? ESTADO_ESTILOS.DESHABILITADA
  }`}
>
  {ESTADO_LABELS[cochera.estado] ?? cochera.estado}
</span>
```

> **Regla rápida:** en el front, preguntar `!== "HABILITADA"` en vez de `=== "DESHABILITADA"`. Así cualquier estado nuevo queda fuera de servicio sin tener que tocar cada comparación.

---

### 9.5 Caso particular: agregar un tipo de cochera exclusivo para un tipo de vehículo

Ejemplo: una cochera `CARGA_PESADA` que solo puedan usar vehículos `CARGA`.

> **Por qué no alcanza con sumar el valor al enum:** la compatibilidad entre vehículo y cochera compara el **nombre** de los dos enums (`CARGA` == `CARGA`). Un tipo de cochera con otro nombre no lo podría usar **ningún** vehículo.

#### Paso 1: centralizar la regla en `CocheraTipo.java`

`aparcar-api-back/src/main/java/com/aparcar/api/entity/reserva/CocheraTipo.java`

```java
package com.aparcar.api.entity.reserva;

public enum CocheraTipo {
    AUTO,
    MOTO,
    ACCESIBLE,
    CARGA,
    BICICLETA,
    CARGA_PESADA;

    /**
     * Que tipo de vehiculo entra fisicamente en esta cochera. ACCESIBLE admite
     * cualquiera: la restriccion ahi es sobre la PERSONA y se valida aparte.
     * El switch es exhaustivo a proposito: un tipo nuevo no compila hasta
     * decidir que vehiculos admite.
     */
    public boolean admite(VehiculoTipo vehiculo) {
        if (vehiculo == null) {
            return false;
        }
        return switch (this) {
            case AUTO -> vehiculo == VehiculoTipo.AUTO;
            case MOTO -> vehiculo == VehiculoTipo.MOTO;
            case BICICLETA -> vehiculo == VehiculoTipo.BICICLETA;
            case CARGA, CARGA_PESADA -> vehiculo == VehiculoTipo.CARGA;
            case ACCESIBLE -> true;
        };
    }
}
```

#### Paso 2: reemplazar la comparación por nombre en los 3 lugares que validan

Cada lugar conserva su manejo especial de `ACCESIBLE`; solo cambia la comparación.

| Archivo y método | Qué hace | Antes | Después |
|---|---|---|---|
| `ReservaService.validarCompatibilidad` | **Bloquea** la reserva con una cochera elegida (400) | `!cochera.getTipo().name().equals(vehiculo.getTipo().name())` | `!cochera.getTipo().admite(vehiculo.getTipo())` |
| `PlanificacionReservas.compatible` | Valida la lista de espera y elige cochera en `disponible()` y `promover()` | `tipo != null && tipo.name().equals(vehiculo.getTipo().name())` | `tipo != null && tipo.admite(vehiculo.getTipo())` |
| `CocheraService.esCompatible` | Filtra el desplegable de `/disponibles` (no bloquea) | `cocheraTipo.name().equals(tipoVehiculo.name())` | `cocheraTipo.admite(tipoVehiculo)` |

> **Regla rápida:** para cambiar qué vehículo entra en qué cochera, se toca **solo** el `switch` de `CocheraTipo.admite`. Nunca comparar `.name()` entre los dos enums.

#### Paso 3: el front

**1. Archivo nuevo** `aparcar-front/utils/compatibilidad.js`. Es el espejo de `CocheraTipo.admite()` en el back: si cambia allá, cambia acá.

```js
// Espejo de CocheraTipo.admite (back). Si cambia allá, cambiarlo acá.
export const CATEGORIAS_POR_VEHICULO = {
  AUTO: ["AUTO"],
  MOTO: ["MOTO"],
  CARGA: ["CARGA", "CARGA_PESADA"],
  BICICLETA: ["BICICLETA"],
};

export const categoriasPara = (tipoVehiculo) => CATEGORIAS_POR_VEHICULO[tipoVehiculo] ?? [];
```

**2. `aparcar-front/utils/ocupacion.js`**: sumar la etiqueta en `TIPO_ETIQUETA`:

```js
  CARGA_PESADA: "Carga pesada",
```

**3. `aparcar-front/components/AvisoListaEspera.jsx`**: mostrar la etiqueta en vez del valor crudo (si no, dice "carga_pesada").

Agregar arriba:
```js
import { TIPO_ETIQUETA } from "@/utils/ocupacion";
```
Reemplazar:
```jsx
// antes
de tipo {tipoCochera?.toLowerCase()} mientras
// después
de tipo {(TIPO_ETIQUETA[tipoCochera] ?? tipoCochera)?.toLowerCase()} mientras
```

**4. `aparcar-front/app/dashboard-admin/VisitantesContent.jsx`** (alta de visitante, admin)

Imports (`TIPO_ETIQUETA` solo si no está importado):
```js
import { categoriasPara } from "@/utils/compatibilidad";
import { TIPO_ETIQUETA } from "@/utils/ocupacion";
```

Schema, debajo de `listaEspera: z.boolean().optional(),`. Hay que declararlo: zod descarta los campos que no están en el schema.
```js
    categoriaEspera: z.string().optional(),
```

Valores iniciales. Sumar `categoriaEspera: ""` **en los dos lugares**, el `defaultValues` y el `reset(...)` de después de enviar:
```js
{ tipoVehiculo: "AUTO", cocheraId: "", listaEspera: false, categoriaEspera: "", ...franjaPorDefecto() }
```

Categoría elegida, justo debajo de `const enEspera = puedeEsperar && listaEspera;`:
```js
  const categorias = categoriasPara(tipoVehiculo);
  const categoriaEspera = watch("categoriaEspera") || categorias[0] || tipoVehiculo;
```

Cotización:
```js
// antes
useCotizacion(cocheraElegida?.tipo ?? tipoVehiculo, desde, hasta, null, convenios.contextoCotizacion);
// después
useCotizacion(cocheraElegida?.tipo ?? categoriaEspera, desde, hasta, null, convenios.contextoCotizacion);
```

Limpiar la elección al cambiar vehículo o franja, en el `useEffect` que depende de `disponibilidad.solicitud`:
```js
  useEffect(() => {
    setValue("cocheraId", "");
    setValue("listaEspera", false);
    setValue("categoriaEspera", "");
  }, [disponibilidad.solicitud, setValue]);
```

Envío, en `onSubmit`:
```js
// antes
...(enEspera ? { listaEspera: true, tipoCochera: data.tipoVehiculo } : {}),
// después
...(enEspera ? { listaEspera: true, tipoCochera: categoriaEspera } : {}),
```

Selector, reemplazando la línea del `<AvisoListaEspera ... />`:
```jsx
{puedeEsperar && categorias.length > 1 && (
  <div className="mt-3">
    <label className="ui-label" htmlFor="alta-categoria-espera">Esperar por cochera de tipo</label>
    <select id="alta-categoria-espera" {...register("categoriaEspera")} className="ui-input">
      {categorias.map((c) => (
        <option key={c} value={c}>{TIPO_ETIQUETA[c] ?? c}</option>
      ))}
    </select>
  </div>
)}
{puedeEsperar && (
  <AvisoListaEspera id="alta-lista-espera" registro={register("listaEspera")} tipoCochera={categoriaEspera} />
)}
```

**5. `aparcar-front/components/ReservasContent.jsx`** (reserva del usuario y del admin)

Son los mismos cambios que en el punto 4, pero el tipo sale de `vehiculoEncontrado?.tipo`:

| Qué | Antes | Después |
|---|---|---|
| Imports | — | Los mismos 2 imports del punto 4 |
| Schema `reservaSchema` | — | `categoriaEspera: z.string().optional(),` debajo de `listaEspera` |
| `defaultValues` y `reset(...)` | `cocheraId: "", listaEspera: false` | `cocheraId: "", listaEspera: false, categoriaEspera: ""` |
| Debajo de `const enEspera = ...` | — | `const categorias = categoriasPara(vehiculoEncontrado?.tipo);`<br>`const categoriaEspera = watch("categoriaEspera") \|\| categorias[0] \|\| vehiculoEncontrado?.tipo;` |
| `tipoTarifa` | `cocheraElegida?.tipo ?? vehiculoEncontrado?.tipo` | `cocheraElegida?.tipo ?? categoriaEspera` |
| `useEffect` que limpia `cocheraId` y `listaEspera` | — | Sumar `setValue("categoriaEspera", "");` |
| `onSubmit` | `tipoCochera: vehiculoEncontrado.tipo` | `tipoCochera: categoriaEspera` |
| `<AvisoListaEspera ... />` | `tipoCochera={vehiculoEncontrado?.tipo}` | El mismo bloque del punto 4, con `id="reserva-categoria-espera"` y `tipoCochera={categoriaEspera}` |

> **Por qué el selector solo aparece con más de una categoría:** para auto, moto o bicicleta no hay nada que elegir, y `categoriaEspera` vale lo mismo que antes (el tipo del vehículo). El comportamiento actual no cambia para ellos.

#### Paso 4: el checklist de siempre al sumar un tipo de cochera (ver 9.3)

- **Numeración:** prefijo nuevo en `NumeracionCocheras.prefijo`. El `switch` también es exhaustivo, así que si falta no compila.
- **Tarifas:** una fila más (pasan a ser 6). Hay que ajustarlo en tres lugares:
  - el `@Size` de `TarifasRequestDto`;
  - el `.length(...)` de `TarifasManagement.jsx`;
  - los tipos en `TarifaControllerTests`.
- **Front:** sumar el valor a `TIPOS_COCHERA`, `TIPO_LABELS`, `TIPOS_TARIFA` y a los `<select>` de `CocherasManagement.jsx`.
- **Back:** reconstruir con `docker compose up -d --build server` (ver 9.2).
- **Precios:** cargar la tarifa nueva desde **Gestionar tarifas**, porque el seeder no la agrega en una base con datos.

---

### 9.6 Caso particular: agregar un campo nuevo con un enum nuevo a la cochera

Ejemplo real: campo `cobertura` con los valores `TECHADA`, `SEMICUBIERTA` y `DESCUBIERTA`.

> **Diferencia con 9.4 (sumar un valor a un enum existente):** acá la columna no existe. Hace falta el enum, la columna en la base (migración), los DTOs de entrada y salida, el mapeo en el servicio y el schema de zod.

**Decisión:** el campo es **opcional en el request**, con `DESCUBIERTA` por defecto. Si fuera obligatorio (`@NotNull`), se romperían los tests que crean cocheras sin mandarlo.

#### Back

**1. Enum nuevo:** `aparcar-api-back/src/main/java/com/aparcar/api/entity/reserva/CocheraCobertura.java`

```java
package com.aparcar.api.entity.reserva;

/** Cuánto protege la cochera de la intemperie. */
public enum CocheraCobertura {
    TECHADA,
    SEMICUBIERTA,
    DESCUBIERTA
}
```

**2. Entidad:** en `Cochera.java`, debajo del campo `estado`:

```java
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CocheraCobertura cobertura = CocheraCobertura.DESCUBIERTA;
```

El `= DESCUBIERTA` hace que todo `new Cochera()` (seeders, tests, servicio) arranque con un valor válido.

**3. Migración:** `aparcar-api-back/src/main/resources/db/changelog/018-cobertura-cocheras.yaml`

> ⚠️ Tiene que ir **dentro de `db/changelog/`** y con extensión **`.yaml`**, la misma que usa el `include`. Si no, el back no arranca ("file ... was not found in the configured search path"), y desde el front se ve como un **error de login**.

```yaml
databaseChangeLog:
  - changeSet:
      id: 018-cobertura-cocheras
      author: aparcar
      changes:
        - addColumn:
            tableName: cocheras
            columns:
              - column:
                  name: cobertura
                  type: varchar(20)
                  defaultValue: DESCUBIERTA
                  constraints:
                    nullable: false
      rollback:
        - dropColumn:
            tableName: cocheras
            columnName: cobertura
```

`defaultValue` es obligatorio: ya hay filas en `cocheras`, y Postgres no puede agregar una columna `NOT NULL` sin un valor para ellas.

Al final de `db.changelog-master.yaml`:

```yaml
  - include:
      file: db/changelog/018-cobertura-cocheras.yaml
```

**4. DTO de entrada:** en `CocheraRequestDto.java`, debajo de `estado`. Va **sin** `@NotNull`:

```java
import com.aparcar.api.entity.reserva.CocheraCobertura;
```
```java
    /** Opcional. Al crear, si no viene: DESCUBIERTA. Al editar, si no viene: se mantiene la actual. */
    private CocheraCobertura cobertura;
```

**5. DTO de salida:** `CocheraResponseDto.java` es un `record`. Agregar el componente **después de `estado`**, porque el orden importa:

```java
        CocheraEstado estado,
        CocheraCobertura cobertura,
        Boolean disponibleEnFecha
```

**6. Los lugares que construyen el `CocheraResponseDto`**

Si no los actualizás, no compila. Son **4**: 2 en el código y 2 en los tests.

**a) `CocheraService.java`, método `toResponseDto` (~línea 315):**
```java
                cochera.getEstado(),
                cochera.getCobertura(),
                disponibleEnFecha);
```

**b) `ReservaService.java`, armado de `cocheraDto` (~línea 495):**
```java
                reserva.getCochera().getEstado(),
                reserva.getCochera().getCobertura(),
                null);
```

**c) `src/test/java/com/aparcar/api/service/VisitanteDiscapacidadTests.java` (~línea 96):**

Import:
```java
import com.aparcar.api.entity.reserva.CocheraCobertura;
```
Antes:
```java
                new CocheraResponseDto(UUID.randomUUID(), "A-01", "Planta Baja", CocheraTipo.AUTO,
                        CocheraEstado.HABILITADA, null),
```
Después:
```java
                new CocheraResponseDto(UUID.randomUUID(), "A-01", "Planta Baja", CocheraTipo.AUTO,
                        CocheraEstado.HABILITADA, CocheraCobertura.DESCUBIERTA, null),
```

**d) `src/test/java/com/aparcar/api/service/VisitanteServiceTests.java` (~línea 110):**

Import:
```java
import com.aparcar.api.entity.reserva.CocheraCobertura;
```
Antes:
```java
                new CocheraResponseDto(COCHERA_ID, "A-01", "Planta Baja", CocheraTipo.AUTO, CocheraEstado.HABILITADA, null),
```
Después:
```java
                new CocheraResponseDto(COCHERA_ID, "A-01", "Planta Baja", CocheraTipo.AUTO, CocheraEstado.HABILITADA, CocheraCobertura.DESCUBIERTA, null),
```

**7. Guardar el valor: `CocheraService.java`**

Hay tres `setEstado(dto.getEstado())`. Agregá un bloque debajo de cada uno.

**`crear` (~línea 59), `crearEnLote` (~línea 95) y `editar` (~línea 203)**, donde `cochera` es nueva:
```java
        if (dto.getCobertura() != null) {
            cochera.setCobertura(dto.getCobertura());
        }
```

**`crearPorPlanta`:** no hace falta tocarlo, las cocheras quedan en `DESCUBIERTA`. Si querés elegirla también ahí:
1. Agregá `private CocheraCobertura cobertura;` en `CocheraAltaPorPlantaDto`.
2. Calculá la variable arriba, igual que `estado`: `dto.getCobertura() == null ? CocheraCobertura.DESCUBIERTA : dto.getCobertura()`.
3. Junto a `cochera.setEstado(estado);` (~línea 142), agregá `cochera.setCobertura(cobertura);`.

**8. Reconstruir:** `docker compose up -d --build server` (ver 9.2). En los logs tiene que aparecer el changeset `018-cobertura-cocheras` aplicado y después `Started AparcarApiApplication`.

#### Front: `aparcar-front/app/dashboard-admin/cocheras/CocherasManagement.jsx`

> Sin el paso A, zod descarta el campo y **nunca llega al back**.

**A. Schema `cocheraSchema`:** agregar debajo de `estado`:

```js
  cobertura: z.enum(["TECHADA", "SEMICUBIERTA", "DESCUBIERTA"], {
    message: "Selecciona una cobertura",
  }),
```

**B. Etiquetas:** agregar debajo de `ESTADO_ESTILOS`:

```js
const COBERTURA_LABELS = {
  TECHADA: "Techada",
  SEMICUBIERTA: "Semicubierta",
  DESCUBIERTA: "Descubierta",
};
```

**C. Valores por defecto:** en **todos** los objetos `{ numero: "", sector: "", tipo: "AUTO", estado: "HABILITADA" }`. Son los `defaultValues` del alta y de la edición, `cancelEditing` y el `reset` después de crear. Reemplazar cada uno por:

```js
{ numero: "", sector: "", tipo: "AUTO", estado: "HABILITADA", cobertura: "DESCUBIERTA" }
```

**D. Cargar el valor al editar:** en `resetEdit({...})`, debajo de `estado: cochera.estado,`:

```js
      cobertura: cochera.cobertura ?? "DESCUBIERTA",
```

Sin esto, al editar el select arranca en el default y se **pisa** la cobertura guardada.

**E. Select en el alta:** agregar después del `</div>` que cierra el bloque de **Estado** del formulario de alta (~linea 520):

```jsx
<div>
  <label htmlFor="cobertura" className={labelClasses}>
    Cobertura
  </label>
  <select id="cobertura" {...registerCreate("cobertura")} className={inputClasses}>
    {Object.entries(COBERTURA_LABELS).map(([valor, etiqueta]) => (
      <option key={valor} value={valor}>{etiqueta}</option>
    ))}
  </select>
  {createErrors.cobertura && (
    <p className="mt-1 text-sm text-red-500">{createErrors.cobertura.message}</p>
  )}
</div>
```

**F. Select en la edición:** agregar después del `</div>` del bloque `edit-estado` (~linea 783):

```jsx
<div>
  <label htmlFor="edit-cobertura" className={labelClasses}>
    Cobertura
  </label>
  <select id="edit-cobertura" {...registerEdit("cobertura")} className={inputClasses}>
    {Object.entries(COBERTURA_LABELS).map(([valor, etiqueta]) => (
      <option key={valor} value={valor}>{etiqueta}</option>
    ))}
  </select>
</div>
```

Si la grilla del formulario queda desacomodada, sumar una columna (por ejemplo `lg:grid-cols-5`).

**G. Columna en la tabla:**

Encabezado, después del `<th>` de **Estado** (~linea 886):

```jsx
<th scope="col" role="columnheader" className="px-6 py-3 text-left text-xs font-semibold uppercase tracking-wider text-ink/50">
  Cobertura
</th>
```

Celda, después del `</td>` de `data-label="Estado"` (~linea 921):

```jsx
<td role="cell" data-label="Cobertura" className="whitespace-nowrap px-6 py-4 text-sm text-ink/70">
  {COBERTURA_LABELS[cochera.cobertura] ?? cochera.cobertura}
</td>
```

**Si me olvido:**
- **Un `new CocheraResponseDto` sin actualizar:** el build de Docker falla en `testCompile`.
- **El changelog fuera de `db/changelog/` o con `.yml`:** el back no arranca, y en el front se ve como "credenciales incorrectas".
- **El campo en el schema de zod:** el select se ve, pero el valor no llega al back y todo queda `DESCUBIERTA`.
- **`cobertura` en `resetEdit`:** cada edición pisa la cobertura con el default.