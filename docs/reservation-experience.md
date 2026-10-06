# Experiencia de reservas

Actualización posterior: las reglas de estados y tiempo se amplían con EXPIRADA
según [el ciclo de vida temporal](reservation-lifecycle.md). Ese documento prevalece
sobre la descripción original de transiciones que sigue en esta auditoría.

## Auditoría previa

La creación valida contacto, entidades activas, horario y solapamientos, y bloquea
la agenda de la profesional. Genera 32 bytes aleatorios; solo guarda SHA-256 del
código privado. El correo de confirmación se intenta después del commit; SMTP es
opcional y su fallo no revierte la reserva. Consulta y cancelación requieren correo
y código, nunca únicamente ID. Las rutas públicas actuales son `/reservas/consultar`
y `/reservas/cancelar`; `reservation-lookup` era el nombre del template.

La autorización administrativa usa `adminAuthenticated` en sesión, con renovación
de sesión al iniciar sesión; Spring Security mantiene CSRF. Las transiciones son:
PENDIENTE/CONFIRMADA hacia cualquiera de los cuatro estados; CANCELADA puede volver
a PENDIENTE/CONFIRMADA solo si el turno sigue disponible; COMPLETADA es terminal.
Se conservan las operaciones existentes y los bloqueos transaccionales.

Problemas encontrados: confirmación mezclada con el formulario, cancelación sin
límite temporal, filtros solo en navegador y carga de todas las reservas, acciones
administrativas inválidas visibles, confirmaciones dependientes de JavaScript y
analytics que interpreta mal HH:mm. Se corrigen dentro de esta entrega.

## Decisiones

- No se cambia el esquema ni V1; no se crea V2.
- Referencia visible `SMB-<id>` para identificar citas; NO es una credencial.
  El administrador no puede recuperar el código privado a partir de su hash.
- Cancelación pública: PENDIENTE o CONFIRMADA y estrictamente antes del inicio,
  en America/Guayaquil. Fechas/horas ilegibles se rechazan. Cancelar de nuevo una
  reserva ya cancelada es idempotente. La comprobación se repite bajo bloqueo.
- Confirmación mediante POST/redirect/GET para evitar reenvíos al actualizar.
  Último comprobante disponible 30 minutos en la misma sesión. Código y correo
  no se ponen en URLs, localStorage ni logs. Después se consulta con los datos
  guardados por el cliente. Un nuevo comprobante sustituye al anterior en sesión.
- Confirmación de cancelación pública y acciones administrativas en servidor,
  con CSRF y segunda acción explícita. No requieren JavaScript.
- Precio/duración corresponden al catálogo actual, no a una instantánea histórica;
  el modelo actual no guarda precios contratados. Se indica en la interfaz.
- Filtros administrativos reales y paginación de 20 registros. La búsqueda usa
  nombre de cliente o referencia SMB, no el secreto privado del cliente.
- Dashboard conserva analytics históricos (incluyen canceladas), y distingue la
  agenda activa de hoy y las próximas citas. No representa ingresos ni ocupación.
  El cálculo histórico aún lee el conjunto de reservas; agregación SQL queda como
  mejora futura si el volumen lo exige, fuera de esta renovación acotada.

## Rutas y seguridad

Se conservan `/reservas/confirmar`, `/reservas/consultar`, `/reservas/cancelar`,
`/admin/reservations`, `/admin/reservations/status` y `/admin/reservations/delete`.
Cambios de contrato deliberados: creación exitosa responde 302 hacia el nuevo GET
`/reservas/confirmada`; cancelación sin `confirmed=true` muestra una revisión y no
escribe. Fallos de acciones administrativas redirigen con un mensaje flash, en
lugar de una página técnica 409. Las validaciones de negocio siguen en el servicio.

Se agregan GET/POST `/reservation-lookup` como alias de consulta y GET
`/admin/reservations/action` para revisar una acción sin modificar datos.
Filtros GET: `q`, `status`, `date` (ISO), `branchId`, `masseuseId`, `page` (desde 0).
La búsqueda limita longitud y escapa comodines LIKE. Páginas fuera del rango se
ajustan a la última página disponible. Los códigos privados no se buscan ni se
muestran en administración; se usa la referencia visible SMB.

Se conserva la autenticación administrativa por sesión, la protección CSRF y los
headers no-store de Spring Security. Las nuevas vistas privadas incluyen
noindex/no-referrer. No se implementa recuperación del código perdido: almacenado
como hash, no se puede reconstruir. Las reservas antiguas sin hash requieren
atención administrativa. El código no equivale a la referencia secuencial.

La eliminación administrativa preexistente sigue disponible con una pantalla de
confirmación explícita; no se eliminó ninguna reserva real durante el desarrollo.
No hay nuevos envíos de notificaciones: se conserva el correo opcional tras commit.

## Experiencia y accesibilidad

Confirmación y consulta comparten el resumen de reserva; dashboard y agenda
comparten navegación. Colores de estado incluyen texto, no solo color. La agenda
usa tarjetas adaptables, no una tabla desbordada en móvil. Los formularios tienen
labels, foco visible, mensajes y confirmaciones de servidor. Copiar el código es
una mejora opcional: siempre se puede seleccionar manualmente.

Agendar permite elegir hora mediante select nativo. Sin JavaScript ofrece un
formulario separado para consultar disponibilidad, sin enviar datos personales por
GET. Los contenidos y navegación permanecen visibles sin JavaScript. Se conservan
prefers-reduced-motion y las animaciones son secundarias.

## Validación

- Pruebas nuevas: `ReservationExperienceTests` (12 recorridos HTTP/MockMvc) y
  `ReservationPolicyTests` (límites temporales y clasificación de horarios).
- Se ajustó la expectativa HTTP del test existente de reserva exitosa a la nueva
  redirección; mantiene su aserción sobre la reserva persistida.
- H2: 73 pruebas ejecutadas sin errores; seis pruebas PostgreSQL opt-in omitidas.
- PostgreSQL 18.6 temporal: suite final completa, 80 pruebas sin fallos ni omisiones;
  `mvnw.cmd package` genera el JAR. Flyway V1 y Hibernate validate compatibles.
- Flujo HTTP real probado en `reservation_preview`, servidor local 8081 y cluster
  aislado en 55432: login, catálogo, disponibilidad, reserva, comprobante, consulta,
  cancelación de dos pasos, filtro, reactivación administrativa y estado actualizado.
- No se modificó `aura_spa2`. La base real ya está adoptada según la información
  del usuario. No se necesita volver a ejecutar el perfil baseline.
- No hubo navegador conectado disponible: no se afirma una validación visual por
  capturas. Se verificaron plantillas renderizadas, formularios y CSS responsive;
  queda recomendable revisar visualmente móvil/escritorio en un navegador local.

Los archivos de trabajo y resultados temporales se guardan solo en `target/`,
excluido de Git. Los cambios de Flyway de la entrega anterior se conservan sin
commit; deben distinguirse de esta renovación al revisar `git status`.

## Archivos de esta entrega

Modificados (11):

- `src/main/java/com/spa/sistema_spa/DashboardAnalytics.java`
- `src/main/java/com/spa/sistema_spa/ReservationBookingService.java`
- `src/main/java/com/spa/sistema_spa/ReservationRepository.java`
- `src/main/java/com/spa/sistema_spa/WebController.java`
- `src/main/resources/static/css/spamoonbeauty.css`
- `src/main/resources/static/js/spamoonbeauty-ui.js`
- `src/main/resources/templates/admin-dashboard.html`
- `src/main/resources/templates/admin-reservations.html`
- `src/main/resources/templates/agendar.html`
- `src/main/resources/templates/reservation-lookup.html`
- `src/test/java/com/spa/sistema_spa/SistemaSpaApplicationTests.java`

Creados (10):

- `docs/reservation-experience.md`
- `src/main/java/com/spa/sistema_spa/ReservationFilters.java`
- `src/main/java/com/spa/sistema_spa/ReservationPolicy.java`
- `src/main/java/com/spa/sistema_spa/ReservationSummary.java`
- `src/main/resources/templates/reservation-confirmation.html`
- `src/main/resources/templates/admin-reservation-action.html`
- `src/main/resources/templates/fragments/admin-nav.html`
- `src/main/resources/templates/fragments/reservation-summary.html`
- `src/test/java/com/spa/sistema_spa/ReservationExperienceTests.java`
- `src/test/java/com/spa/sistema_spa/ReservationPolicyTests.java`

Estado final: 17 archivos modificados y 15 nuevos sin seguimiento, contando también
los 6 modificados y 5 nuevos de Flyway que ya estaban presentes al comenzar.
Sin staging, commit ni push. `git diff --check` y comprobación de sintaxis JavaScript
sin errores. No se agregaron secretos ni archivos temporales fuera de `target/`.

Commit recomendado (no realizado):
`feat: unify reservation confirmation, self-service and admin experience`
