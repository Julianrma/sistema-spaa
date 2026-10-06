# Ciclo de vida temporal de reservas

## Causa y política

Antes, el estado persistido no cambiaba al pasar la hora: las transiciones se
validaban solo por el texto del estado y el KPI contaba cualquier PENDIENTE.
`Reservation.status` es String/VARCHAR(255), sin enum ni CHECK en V1. EXPIRADA no
requiere migración ni cambios de entidades. V1 permanece intacta.

Ahora se usa exclusivamente un Clock inyectable para la hora de negocio,
convertido explícitamente a America/Guayaquil aunque el reloj del servidor sea UTC.
Al alcanzar el inicio (fecha/hora <= ahora), PENDIENTE pasa a EXPIRADA. El límite
exacto coincide con el cierre de la cancelación pública. No se espera al final del
tratamiento ni se supone que hubo asistencia.

| Situación | Comportamiento |
|---|---|
| PENDIENTE futura | Conserva las transiciones anteriores; no puede marcarse EXPIRADA manualmente. |
| PENDIENTE iniciada/pasada | Se persiste EXPIRADA; no admite confirmación, finalización ni cancelación. |
| EXPIRADA | Terminal, conserva el histórico y permite consultar/nueva reserva. |
| CONFIRMADA futura | Conserva las transiciones anteriores. |
| CONFIRMADA iniciada/pasada | Sigue CONFIRMADA, señalada para resolución. Administración puede completar o cancelar; no volver a pendiente. |
| CANCELADA pasada | No se reactiva. Una cancelada futura sigue requiriendo disponibilidad al reactivarse. |
| COMPLETADA | Sigue terminal. |

Permitir CANCELADA para una confirmada pasada conserva la resolución administrativa
existente cuando el servicio no ocurrió. No se introduce NO_ASISTIO. La gestión
pública nunca cancela después del inicio. Repetir el mismo estado permitido no
produce cambios.

## Reconciliación

`ReservationExpirationTask` ejecuta `ReservationLifecycle.reconcile()` al recibir
ApplicationReadyEvent (tras Flyway/JPA/inicializadores) y luego cada 60 segundos,
sin depender de visitas al dashboard. Al reiniciar se recuperan pendientes vencidas
mientras la aplicación estuvo detenida. La tarea solo funciona mientras hay una
instancia de la aplicación en marcha.

También se reconcilia antes de lecturas administrativas relacionadas. Consulta
pública, cancelación y cambio administrativo revisan el registro autenticado bajo
el bloqueo de fila existente, en la misma transacción de la operación. El backend
valida estado y tiempo incluso si se envía una petición manual o una pantalla vieja.

La tarea obtiene únicamente pendientes con fecha <= hoy, en orden de ID, interpreta
su hora y actualiza mediante JPQL condicional: mismo ID, fecha, hora y todavía
PENDIENTE. No sobrescribe confirmaciones/cancelaciones concurrentes. La operación
es transaccional, idempotente y no modifica IDs, relaciones, datos del cliente ni
reservas futuras. No contiene DDL y no es una migración histórica de datos.

La tarea está habilitada por defecto. `spa.reservations.expiration.enabled=false`
se usa en el perfil de tests para evitar trabajo de fondo no determinista; los tests
del ciclo la habilitan y disparan explícitamente el evento/tarea. El reloj se fija
en esos tests, no en producción.

## Vistas y disponibilidad

- Por confirmar cuenta solo PENDIENTE con inicio futuro y legible. EXPIRADA queda
  en la distribución e histórico, nunca en próximas citas.
- EXPIRADA tiene filtro y badge violeta; la agenda explica que expiró sin confirmar.
  Las confirmadas pasadas se identifican como pendientes de resolución.
- El cliente ve el histórico expirado, sin formulario de cancelación, y puede
  realizar otra reserva. Se mantienen código privado, correo y CSRF.
- La consulta de disponibilidad ya separaba por profesional y fecha. El servicio
  ahora excluye EXPIRADA y pendientes cuyo inicio ya pasó, incluso entre barridos;
  además no ofrece horas ya iniciadas del día actual. Una CONFIRMADA pasada puede
  seguir ocupando el intervalo de su tratamiento dentro de ese mismo día.
  `BookingAvailability` no cambia: sigue calculando solapamientos y duración.

## Límites y comprobación manual

Fechas/horas ilegibles no se adivinan: no se expiran automáticamente y no admiten
transiciones temporales. Requieren revisión administrativa del dato. Se soportan
HH:mm y el formato histórico h:mm AM/PM. No se elimina el histórico.

Con la aplicación correctamente conectada, SMB-6 del 30/09/2026 14:00, si aún está
PENDIENTE, pasará a EXPIRADA al arrancar. Dejará de contar en Por confirmar y no
ofrecerá Confirmada/Completada/Cancelada. La regla aplica a todas las reservas;
no hay condiciones especiales por ID, nombre o fecha del ejemplo.

No se conectó ni modificó manualmente aura_spa2/SMB-6 para validar esta tarea.
Las pruebas usan bases desechables y Clock fijo. No se crean migraciones Flyway,
no se ejecuta clean y no se realizan commit/push.

## Entrega y validación

Archivos nuevos: `ReservationLifecycle.java`, `ReservationExpirationTask.java`,
`ReservationTimeConfiguration.java`, `ReservationLifecycleTests.java` y este documento.

Archivos ajustados: `ReservationBookingService.java`, `ReservationRepository.java`,
`ReservationPolicy.java`, `ReservationSummary.java`, `WebController.java`,
`spamoonbeauty.css`, `admin-dashboard.html`, `admin-reservations.html`,
`reservation-lookup.html`, `fragments/reservation-summary.html`,
`ReservationBookingServiceTests.java`, `ReservationExperienceTests.java`,
`application-test.properties` y `reservation-experience.md`.

Se añadieron 12 pruebas con Clock fijo: pendiente futura, evento de arranque/tarea
idempotente, peticiones administrativas inválidas, consulta/cancelación pública,
KPIs/próximas/histórico, filtro/preview, resolución de confirmada pasada, límite
exacto y cruce de fecha UTC/Ecuador, hora ilegible, actualización condicional ante
un cambio concurrente, disponibilidad y creación futura. El test de cancelación
pasada existente ahora comprueba EXPIRADA para la pendiente, conservando el rechazo
de la cancelación y la inmutabilidad de una COMPLETADA.

Resultado: H2, 86 pruebas ejecutadas sin fallos (6 opt-in omitidas). PostgreSQL 18.6
aislado en puerto 55432, base desechable `lifecycle_ci`: **92 pruebas, 0 fallos,
0 errores, 0 omitidas**, `mvnw.cmd package` BUILD SUCCESS. No hubo migración nueva.
`git diff --check` limpio y revisión sin secretos nuevos ni DDL fuera de Flyway.

Estado Git al finalizar: 18 archivos modificados y 20 sin seguimiento, incluyendo
los cambios anteriores que siguen pendientes de commit. No se alteró el staging.
