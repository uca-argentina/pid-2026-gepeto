<div align="center">

# 🅿️ AparcAR

### Nadie debería dar vueltas buscando dónde estacionar.

![Sprint](https://img.shields.io/badge/Sprint-1_MVP-8b5cf6?style=flat-square)
![Backend](https://img.shields.io/badge/Backend-Java_%2F_Spring_Boot-6db33f?style=flat-square)
![Frontend](https://img.shields.io/badge/Frontend-Next.js_%2F_React-000000?style=flat-square)
![Status](https://img.shields.io/badge/Estado-En_desarrollo-orange?style=flat-square)

</div>

---

## El problema

Oficinas, universidades, sanatorios y organizadores de eventos administran sus cocheras a ojo: planillas sueltas, un guardia anotando patentes a mano, visitantes que llegan sin saber si van a tener lugar. El resultado es siempre el mismo — **sobreocupación, demoras en el acceso y cero control real de quién entra al predio.**

## Qué es AparcAR

**AparcAR** es la plataforma que un establecimiento usa para poner orden en su estacionamiento: cada visitante con su vehículo, su reserva y su cochera asignada — sin superposiciones, sin sorpresas en la barrera.

- 🔐 **Acceso por roles** para administradores y visitantes
- 👤 **Registro público de visitantes** desde la pantalla de inicio de sesión
- 🧍 **Registro de visitantes** y sus vehículos (patente y tipo)
- 🅿️ **Cocheras clasificadas** por número, sector y tipo (auto, moto, accesible, carga)
- 📅 **Reservas por fecha**, con asignación de una cochera compatible
- 🚫 **Cero sobreocupación** — el sistema jamás asigna dos reservas al mismo lugar

## Cómo está armado

### Registro de visitantes

Desde **Iniciar sesión → Registrate**, el visitante completa nombre, DNI/documento,
email, teléfono opcional y una contraseña de al menos 8 caracteres con confirmación.
No necesita cargar un vehículo ni hacer una reserva para crear su cuenta.

El formulario reutiliza `POST /register`, `AuthService`, BCrypt y el límite de
intentos existente. El servidor crea una cuenta **activa con rol USER**; los roles
enviados por el cliente no se utilizan. Al finalizar vuelve al login habitual y,
una vez autenticado, accede al mismo panel de vehículos y reservas. La cuenta
aparece automáticamente en **Gestión de usuarios** del administrador.

Se rechazan documentos y emails existentes, incluso cuentas inactivas. Se eliminan
espacios en los extremos y el email se compara sin distinguir mayúsculas. Las
contraseñas conservan exactamente lo escrito y se validan contra el límite de
72 bytes UTF-8 de BCrypt.

La migración Liquibase `005-identidad-visitante-unica.yaml` agrega índices únicos
en PostgreSQL para proteger esas mismas reglas ante solicitudes simultáneas. No
borra ni fusiona datos: si una base anterior contiene emails que solo difieren en
mayúsculas/espacios, o documentos que solo difieren en espacios extremos, hay que
resolver esos duplicados antes de aplicar la migración. El alta administrativa con
vehículo y reserva conserva su flujo.

### Estructura

Este repositorio contiene las dos mitades del proyecto:

| Carpeta | Qué encontrás ahí |
|---|---|
| [`aparcar-api-back/`](./aparcar-api-back) | Backend — Java, Spring Boot, PostgreSQL |
| [`aparcar-front/`](./aparcar-front) | Frontend — Next.js, React |

Cada una tiene su propio README con detalles técnicos específicos, pero la guía de instalación y ejecución completa (paso a paso, para levantar todo desde cero) está más abajo en este mismo archivo. También hay un [`ARCHIVOS.md`](./ARCHIVOS.md) con el detalle de qué hace cada archivo del proyecto.

## Estado actual

🚧 **Sprint 1 — primer MVP en desarrollo.** El foco de esta etapa: login de usuarios internos, ABM de cocheras, registro de visitantes y sus vehículos, y creación de reservas.

## Equipo

Proyecto desarrollado para **UCAio**, la software factory de la UCA — cátedra de Proyecto Integral de Desarrollo.

| Integrante | Rol |
|---|---|
| Devoto | Burning Vibe Coder |
| Coronel | Dev Slayer |
| Denti | SCRUM Master Pro Max |

---

# 🚀 Instalación y ejecución

Esta guía asume que **no tenés nada instalado todavía**. Seguila en orden.

## 1. Instalar lo necesario (una sola vez por máquina)

| Herramienta | Para qué | Descarga |
|---|---|---|
| **Git** | Clonar el repo | https://git-scm.com/downloads |
| **Docker Desktop** | Levantar el backend + la base de datos sin instalar Java/Maven/Postgres a mano | https://www.docker.com/products/docker-desktop |
| **Node.js 20 LTS** (incluye npm) | Correr el frontend | https://nodejs.org |

Después de instalar Docker Desktop, **abrilo y dejalo corriendo** (tiene que estar la ballenita activa en la barra de tareas / menu bar) antes de seguir. Sin eso, los comandos `docker` van a fallar.

Verificá que todo quedó instalado:
```bash
git --version
docker --version
node --version
npm --version
```

---

## 2. Clonar el repo

```bash
git clone https://github.com/martincoronels/aparcar.git
cd aparcar
```

---

## 3. Backend — build + run

> No hace falta instalar Java ni Maven: todo el "build" pasa **dentro** del contenedor de Docker.

```bash
cd aparcar-api-back
```

**Windows (PowerShell):**
```powershell
Copy-Item .env.example .env
```
**Mac / Linux:**
```bash
cp .env.example .env
```

Ese archivo `.env` no está en el repo (por seguridad, nunca se sube), pero `.env.example` ya trae valores por defecto que funcionan entre sí para desarrollo local — no hace falta editarlo.

Ahora el build + arranque, todo en un comando:
```bash
docker compose up --build
```

**Qué hace exactamente:**
- `docker compose up` levanta dos servicios definidos en `docker-compose.yaml`: `db` (Postgres) y `server` (el backend).
- `--build` es **el build**: le dice a Docker que compile la imagen del backend desde cero usando `dockerfiles/server.Dockerfile`, que por dentro corre `mvn clean install` con una versión de Maven que ya viene incluida en la imagen. Es fundamental pasarlo siempre que el código cambió (si no, Docker podría reusar una imagen vieja en caché).
- El servicio `server` espera automáticamente a que `db` esté sano antes de arrancar (chequeo de salud incluido), así que no hay que preocuparse por el orden.

**Qué vas a ver en pantalla:** primero mucho log de la build (descarga de dependencias, compilación), después el banner de Spring Boot, y termina con algo como:
```
Started AparcarApiApplication in X.XXX seconds
```
La terminal se queda mostrando logs — **es lo esperado**, significa que el servidor sigue corriendo. No la cierres.

**Verificación:** abrí **http://localhost:8080/docs** en el navegador. Si carga Swagger con la lista de endpoints, el backend está funcionando.

---

## 4. Frontend — build + run

Abrí **otra terminal nueva** (dejá la del backend corriendo) y desde la raíz del repo:

```bash
cd aparcar-front
```

**Windows (PowerShell):**
```powershell
Copy-Item .env.example .env
```
**Mac / Linux:**
```bash
cp .env.example .env
```

Este `.env` define `NEXT_PUBLIC_API_BASE_URL=http://localhost:8080`, la URL donde el frontend espera encontrar al backend. Si el backend corre en otro puerto o máquina, hay que editarlo.

Instalar dependencias (solo hace falta la primera vez, o cuando cambia `package.json`):
```bash
npm install
```

**Para desarrollo día a día** (recarga automática al guardar cambios):
```bash
npm run dev
```

**Para probar el build de producción** (lo que realmente se despliega, sin hot-reload):
```bash
npm run build
npm start
```

**Qué vas a ver en pantalla** (con `npm run dev`):
```
▲ Next.js 16.1.6
- Local:        http://localhost:3000
✓ Ready in XXXms
```

**Verificación:** abrí **http://localhost:3000**. Deberías caer en la app (te redirige a `/login` si no estás autenticado).

---

## 5. Orden de arranque

1. Docker Desktop abierto y corriendo.
2. Backend (`docker compose up --build` en `aparcar-api-back`) — esperar a ver `Started AparcarApiApplication`.
3. Frontend (`npm run dev` en `aparcar-front`) en otra terminal.

El frontend necesita al backend corriendo para funcionar (login, listar visitantes, crear reservas, etc.) — sin el backend arriba, vas a ver errores de red al usar los formularios.

---

## 6. Monitoreo — cómo saber si algo anda mal

| Dónde | Señal de problema | Causa probable |
|---|---|---|
| Backend | `Connection refused` / `Communications link failure` en los logs | La base de datos todavía no estaba lista, o Docker Desktop no está corriendo |
| Backend | `BUILD FAILURE` durante `docker compose up --build` | Error real de compilación — copiar el log completo para diagnosticarlo |
| Backend | `port is already allocated` | Ya hay algo usando el puerto 8080 o 5433 en tu máquina; cerralo o cambiá el puerto en `docker-compose.yaml` |
| Frontend | La página carga pero los formularios tiran error al enviar | El backend no está corriendo, o `NEXT_PUBLIC_API_BASE_URL` en `.env` no apunta a donde está el backend |
| Frontend | `npm install` falla | Versión de Node muy vieja — confirmá `node --version` (necesitás 20+) |

---

## 7. Apagar todo

En cada terminal, en este orden:
1. Frontend: `Ctrl+C`
2. Backend: `Ctrl+C`, y opcionalmente `docker compose down` (desde `aparcar-api-back`) para liberar los contenedores por completo.

---

## 8. Correr los tests (backend)

Los tests corren contra una base en memoria (H2): no necesitan `db` ni `server` levantados. Como la imagen final del backend no incluye Maven (solo el `.jar` ya compilado), para correr los tests sin instalar Maven en tu máquina usá un contenedor temporal con la imagen de Maven, montando el código fuente:

**Windows (PowerShell), desde `aparcar-api-back`:**
```powershell
docker run --rm -v "${PWD}:/app" -w /app maven:3.9.9-eclipse-temurin-21-alpine mvn test
```

**Mac / Linux, desde `aparcar-api-back`:**
```bash
docker run --rm -v "$(pwd):/app" -w /app maven:3.9.9-eclipse-temurin-21-alpine mvn test
```

Si preferís instalar Maven local (por ejemplo para usar `mvn spring-boot:run` en vez de Docker), después podés correr directamente `mvn test`.
