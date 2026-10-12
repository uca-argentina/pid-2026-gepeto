# Poner AparcAR en producción (AWS EC2)

Guía para dejar el sistema andando 24/7 en una instancia EC2, con despliegue
automático al pushear a `main`.

## Qué vamos a armar

Una sola instancia con cuatro contenedores. El único que se asoma a internet es
Caddy; los otros tres se hablan por la red interna de Docker.

```
                    internet
                       │ :80 / :443
                  ┌────▼────┐
                  │  Caddy  │  HTTPS automático
                  └──┬───┬──┘
         /backend/*  │   │  /*
                  ┌──▼─┐ └─▼────────┐
                  │API │ │ Frontend │
                  └──┬─┘ └──────────┘
                  ┌──▼─┐
                  │ DB │  sin puerto publicado
                  └────┘
```

**El frontend y el backend comparten dominio.** Caddy manda todo lo que empieza
con `/backend/` a la API y le recorta ese prefijo, así que el backend recibe sus
rutas de siempre sin cambiar una sola línea. Lo importante: el navegador ve un
solo origen, así que **CORS no se activa nunca**.

El prefijo no es decorativo. `/login` y `/register` existen en los dos lados —
en el front son páginas, en el backend son endpoints — y sin separarlos no hay
forma de saber a quién mandar cada pedido.

---

## Paso 1 — Crear la instancia

En la consola de EC2, **Launch instance**:

| Campo | Valor |
|---|---|
| **AMI** | Ubuntu Server 24.04 LTS |
| **Instance type** | **t3.small** (2 vCPU, 2 GB) |
| **Key pair** | Creá una nueva y guardá el `.pem`. Es la única copia |
| **Storage** | 30 GB gp3 |

**Sobre el tamaño.** Medí la pila completa corriendo: **~496 MB** de
contenedores en reposo (backend 395, base 57, front 31, Caddy 13), más
Ubuntu y Docker (~330 MB). Son **~830 MB**.

- **t3.micro (1 GB) no alcanza** aunque esté en la capa gratuita: queda al 80%
  ocupado sin un solo usuario, y la JVM lo tumba en cuanto crece el heap.
- **t3.small (2 GB)** es el mínimo real y deja margen.
- **t3.medium (4 GB)** si querés tranquilidad total. Si la elegís, subí
  `BACKEND_MEM=2500M` en el `.env`.

**Region:** `us-east-1` es la más barata; `sa-east-1` (São Paulo) tiene mucha
menos latencia desde Argentina (~30 ms contra ~120 ms) pero es de las más caras
de AWS. Para una demo en vivo, São Paulo se nota.

### Security group

Tres reglas de entrada, nada más:

| Puerto | Origen | Para qué |
|---|---|---|
| 22 | Tu IP | SSH |
| 80 | 0.0.0.0/0 | HTTP y la validación de Let's Encrypt |
| 443 | 0.0.0.0/0 | HTTPS |

### IP elástica

**Hacelo, no lo saltees.** Sin una IP elástica, la IP pública cambia cada vez
que la instancia se detiene y arranca, y se te rompen el DNS y el despliegue
automático.

En **Elastic IPs → Allocate**, y después asociala a la instancia.

> AWS cobra por toda IPv4 pública (~US$3,60/mes), tanto si usás IP elástica
> como si no. No hay forma de evitarlo.

## Paso 2 — Preparar la máquina

```bash
chmod 400 tu-clave.pem
ssh -i tu-clave.pem ubuntu@TU_IP_ELASTICA
```

Y adentro:

```bash
sudo apt update && sudo apt upgrade -y
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker ubuntu
exit    # salí y volvé a entrar para que tome el grupo
```

> El firewall ya lo hace el security group. No hace falta `ufw`, y activarlo
> sin cuidado es la forma más común de quedarse afuera de la propia instancia.

## Paso 3 — Clonar el repo

```bash
git clone https://github.com/uca-argentina/pid-2026-gepeto.git ~/aparcar
cd ~/aparcar/deploy
cp .env.example .env
```

Generá los dos secretos y completá el resto:

```bash
openssl rand -base64 36   # para JWT_SECRET
openssl rand -base64 36   # para ACCESO_CODIGO_SECRET
nano .env
```

| Variable | Qué poner |
|---|---|
| `DB_PASSWORD` | Una contraseña larga cualquiera. Solo la usan los contenedores entre sí |
| `JWT_SECRET` | El primer `openssl`. Firma las sesiones |
| `ACCESO_CODIGO_SECRET` | El segundo `openssl`. Firma los códigos QR. **La app no arranca sin esto en producción** |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` / `ADMIN_DOCUMENTO` | Tu cuenta de administrador |
| `DOMINIO` | Dejala **vacía** por ahora |
| `MAIL_*` | Vacías salvo que quieras recuperación de contraseña |

> `.env` está en `.gitignore` y nunca sale de esta máquina. Es el único lugar
> donde viven las contraseñas reales.

## Paso 4 — Conectar GitHub con la instancia

En el repo: **Settings → Secrets and variables → Actions → New secret**.

| Secret | Valor |
|---|---|
| `VM_HOST` | La IP elástica |
| `VM_USER` | `ubuntu` |
| `VM_SSH_KEY` | El contenido completo del `.pem`, incluidas las líneas `BEGIN`/`END` |

`GITHUB_TOKEN` no hay que cargarlo: lo genera GitHub solo.

## Paso 5 — Primer despliegue

Hacé push a `main`, o corré el workflow a mano desde la pestaña **Actions**.

El workflow compila las dos imágenes en GitHub, las sube a GHCR, entra por SSH
y levanta los contenedores. El primero tarda unos 8 minutos; los siguientes,
alrededor de 2, porque las dependencias quedan en caché.

Cuando termine, entrá a `http://TU_IP_ELASTICA`. Deberías ver el login y poder
entrar con tu `ADMIN_EMAIL`.

## Paso 6 — El dominio y el HTTPS

Cuando consigan el dominio:

1. Apuntá un registro **A** a la IP elástica.
2. En la instancia, editá `~/aparcar/deploy/.env` y completá
   `DOMINIO=tu-dominio.com`.
3. `cd ~/aparcar/deploy && docker compose up -d`

Caddy pide el certificado a Let's Encrypt solo y lo renueva sin que haya que
tocar nada. Los certificados quedan en un volumen de Docker; no los borres,
porque Let's Encrypt limita cuántas veces podés pedirlos.

---

## El día a día

**Desplegar:** push a `main`. Nada más.

**Volver atrás** si un despliegue salió mal. Cada imagen queda etiquetada con
el hash del commit, así que no hay que recompilar:

```bash
cd ~/aparcar/deploy
nano .env            # IMAGE_TAG=<hash del commit que andaba>
docker compose up -d
```

**Ver qué pasa:**

```bash
cd ~/aparcar/deploy
docker compose ps                      # estado de los cuatro
docker compose logs -f backend         # registros en vivo
docker stats --no-stream               # memoria
```

**Respaldar la base.** Es lo único que no se puede recrear:

```bash
docker compose exec db pg_dump -U aparcar aparcar_db > respaldo-$(date +%F).sql
```

---

## Qué queda cargado de entrada

| Dato | Estado |
|---|---|
| Cocheras | Ya vienen. Las crea la migración `002` |
| Tarifas | Valores de arranque, editables desde "Gestionar tarifas" |
| Cuenta ADMIN | La de tu `ADMIN_EMAIL` |
| Visitantes | Ninguno. Los das de alta desde el panel |

---

## Lo que te va a costar

Aproximado, en `us-east-1`:

| Concepto | Mensual |
|---|---|
| t3.small | ~US$15 |
| 30 GB gp3 | ~US$2,50 |
| IPv4 pública | ~US$3,60 |
| **Total** | **~US$21** |

En `sa-east-1` es bastante más. Dos cosas que conviene saber:

- **Poné una alarma de facturación** (Billing → Budgets) apenas crees la
  cuenta. Es lo que evita el susto a fin de mes.
- Si tenés créditos de AWS Educate o de la capa gratuita, cubren esto un buen
  tiempo. Pero la capa gratuita solo alcanza para t3.micro, que **no sirve**
  para esta aplicación.

---

## Problemas frecuentes

**El backend no arranca y el log dice `Falta ACCESO_CODIGO_SECRET`.**
Es a propósito: con el secreto de desarrollo, cualquiera que conozca el id de
una reserva podría calcular su código QR y entrar con una reserva ajena.
Completá la variable en `.env`.

**La página carga pero no trae datos.**
Consola del navegador (F12) → *Network*. Si los pedidos a `/backend/...` dan
502, el backend todavía está arrancando: esperá un minuto y mirá
`docker compose logs backend`.

**No puedo entrar por SSH.**
Casi siempre es el security group: la regla del 22 apunta a tu IP anterior y
tu proveedor te la cambió. Editala en la consola de EC2.

**Caddy reinicia en bucle.**
Suele ser el `Caddyfile`. Validalo sin levantar nada:
`docker compose exec caddy caddy validate --config /etc/caddy/Caddyfile`

**El certificado no sale.**
El dominio tiene que resolver a la IP *antes* de completar `DOMINIO`.
Comprobalo con `dig +short tu-dominio.com`. Y el puerto 80 tiene que estar
abierto en el security group: Let's Encrypt lo necesita para verificar.

**Los horarios aparecen corridos tres horas.**
Falta `TZ` en el `.env`. El contenedor arranca en UTC si no se le dice otra cosa.

**Se llenó el disco.**
`docker system prune -af`. El workflow ya hace un `image prune` en cada
despliegue, pero conviene revisarlo cada tanto.
