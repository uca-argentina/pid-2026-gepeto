# AparcAR API

Backend del Sistema de Reserva de Estacionamientos AparcAR — Proyecto Integral de Desarrollo.

Basado en un template interno de **Spring Boot 3.4+** (Java 21) con arquitectura productiva: JWT + roles, Liquibase, Docker Compose y OpenAPI.

## 🚀 Quick Start

### Prerequisites
- **Java 21+**
- **Maven 3.9+**
- **Docker & Docker Compose**
- **Task** — task runner used for all development commands:
  ```bash
  # macOS
  brew install go-task
  # or via npm
  npm install -g @go-task/cli
  ```
  See [taskfile.dev](https://taskfile.dev/installation/) for other installation options.

### Option A: Run Locally (Native)

1.  **Configuration**:
    - Copy `.env.example` to `.env`:
      ```bash
      cp .env.example .env
      ```
    - The application uses `spring-dotenv` to automatically load variables from `.env`.

2.  **Run Application**:
    ```bash
    task run
    # or
    make run
    # or
    mvn spring-boot:run
    ```

### Option B: Run with Docker (Recommended)

1.  **Setup Environment**:
    - Generar un archivo `server.env` y `db.env` dentro de la carpeta `dockerfiles` (puedes basarte en los archivos `.example` si existen).

2.  **Start Services**:
    ```bash
    docker compose up --build
    ```
    This will start Postgres and the Spring Boot application, exposing the API at `http://localhost:8080`.

---

## 🛠 Development Scripts

We use **Task** (`Taskfile.yml`) to automate common development tasks.

| Command (Task) | Command (Make) | Description |
| :--- | :--- | :--- |
| `task setup-brew` | `make setup-brew` | Install pre-commit hooks and tools (macOS). |
| `task fmt` | `make fmt` | Run code formatting and security checks. |
| `task migrate name=X` | `make migrate name=X` | Create a new SQL migration from entities. |
| `task test` | `make test` | Run all unit tests. |
| `task cover` | `make cover` | Run tests and generate Jacoco report. |
| `task run` | `make run` | Run the application locally. |
| `task build` | `make build` | Build the application JAR. |
| `task profile` | `make profile` | Run with **JFR Profiling** for 60s. |

---

## 🗄 Database Migrations

Utilizamos **Liquibase** para gestionar las versiones de la base de datos. El sistema compara automáticamente tus entidades de Java con la base de datos real y genera los cambios necesarios.

**Para crear una migración:**
```bash
task migrate name=nombre-de-la-migracion
# o
make migrate name=nombre-de-la-migracion
```
Esto generará un archivo YAML en `src/main/resources/db/changelog/` y lo registrará automáticamente en el `db.changelog-master.yaml`.

---

## 🛡 Code Quality & Security

We use **pre-commit** hooks to ensure code consistency and security.

### 1. Setup (One-time only)
```bash
task setup-brew   # macOS
# o
make setup-brew   # macOS
```

### 2. Workflow
Los hooks se ejecutan automáticamente en cada `git commit`.
- **Gitleaks**: Scans for hardcoded secrets/passwords.
- **end-of-file-fixer**: Asegura que los archivos terminen con una línea en blanco.
- **trailing-whitespace**: Elimina espacios innecesarios al final de las líneas.
- **check-yaml**: Valida la sintaxis de archivos `.yml` y `.yaml`.

---

## 🔬 Profiling

Para analizar el rendimiento de la aplicación bajo carga, puedes usar:
```bash
task profile
# o
make profile
```
Esto grabará 60 segundos de ejecución usando **JFR (Java Flight Recorder)** en un archivo `recording.jfr`, que puedes abrir en **IntelliJ IDEA** o **JDK Mission Control**.

---

## 🏗 Project Structure

| Path | Description |
| :--- | :--- |
| `src/main/java` | Código fuente. |
| `src/main/resources` | Configuraciones (application.yml) y assets. |
| `scripts/` | Script de utilidad para generar migraciones de Liquibase. |
| `dockerfiles/` | Configuraciones de Docker y variables de entorno para deploy. |
| `pom.xml` | Gestión de dependencias de Maven. |

## ⚙️ Configuration (Environment Variables)

Variables principales para ejecución local (vía `.env`):

| Variable | Description | Default |
| :--- | :--- | :--- |
| `PORT` | Puerto HTTP | `8080` |
| `SPRING_PROFILES_ACTIVE` | Perfil activo (dev, prod) | `dev` |
| `DB_URL` | URL JDBC de conexión | - |
| `DB_USERNAME` | Usuario de base de datos | - |
| `DB_PASSWORD` | Password de base de datos | - |
