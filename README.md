# Aura Spa

## PostgreSQL con pgAdmin

1. En pgAdmin crea una base de datos llamada `aura_spa`.
2. Ejecuta la aplicación con las credenciales de tu instalación:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/aura_spa
export DB_USERNAME=postgres
export DB_PASSWORD=tu_clave_de_postgres
bash mvnw spring-boot:run
```

Hibernate crea y actualiza las tablas `spa_services`, `branches`, `masseuses`, `reservations` y `reviews` automáticamente. Los servicios, sucursales y masajistas viven en PostgreSQL: el portal administrativo permite agregarlos, editarlos, activarlos, desactivarlos y eliminarlos sin modificar el código.

Cada reserva se guarda cuando el cliente pulsa “Confirmar y Agendar Turno”. La aplicación registra el servicio, sucursal, masajista, nombre, cédula, teléfono, correo, fecha, hora y estado `PENDIENTE`. Los turnos se calculan cada 30 minutos según el horario de la sucursal y la duración del servicio; también se descartan los horarios que se solapan con otra cita activa de la masajista. En pgAdmin puedes verlo en `aura_spa > Schemas > public > Tables > reservations`; para consultar los datos usa `SELECT * FROM reservations ORDER BY id DESC;`.

Para administrar el catálogo entra al dashboard, busca `Catálogo de servicios` y usa `Nuevo servicio` o las acciones de cada fila. Los servicios desactivados no aparecen para los clientes, pero permanecen en la base de datos.

Las reseñas se guardan en `reviews` con estado `PENDIENTE`; el administrador puede aprobarlas o eliminarlas. Solo las reseñas aprobadas forman el promedio mostrado en el dashboard y en el portal público.

El portal administrativo incluye las pantallas `/admin/branches` para crear, editar, activar, desactivar o eliminar sucursales, `/admin/masseuses` para administrar masajistas y `/admin/reservations` para filtrar visualmente las citas y cambiar su estado a `CONFIRMADA`, `COMPLETADA` o `CANCELADA`. La agenda elimina automáticamente los horarios ocupados y rechaza fechas pasadas o reservas duplicadas.

Desde VS Code también puedes ejecutar `SistemaSpaApplication` desde Run. La configuración `.vscode/launch.json` solicitará el usuario y la contraseña de PostgreSQL en cada inicio y no guardará la contraseña en el proyecto.

## Portal administrativo

Antes de iniciar la aplicación, configura `SPA_ADMIN_USERNAME` y `SPA_ADMIN_PASSWORD` con credenciales propias. Sin ambas variables, el acceso administrativo queda deshabilitado; no hay credenciales predeterminadas. Por ejemplo, expórtalas en la terminal antes de ejecutar la app y luego abre `http://localhost:8080/admin/login`.

## Pruebas

```bash
bash mvnw test
```