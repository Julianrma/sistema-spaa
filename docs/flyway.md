# Migraciones de SpaMoonBeauty

Flyway es el único responsable del esquema. Spring Boot 4.1.1 administra las
versiones de `spring-boot-starter-flyway` y `flyway-database-postgresql`.
Hibernate usa `validate`: comprueba las entidades, pero no crea ni altera tablas.
La inicialización SQL de Spring está desactivada y Flyway tiene `clean` deshabilitado.

## Esquema inicial y adopción

- `src/main/resources/db/migration/V1__initial_schema.sql` representa las cinco
  tablas actuales, sus columnas, identidades y relaciones declaradas en JPA.
  Incluye `spa_services.featured BOOLEAN NOT NULL DEFAULT false`,
  `spa_services.description TEXT` y `reservations.masseuse_id` desde el principio.
- En una base vacía Flyway ejecuta V1. No requiere restaurar datos personales ni
  ejecutar ALTER manualmente. La base PostgreSQL y su usuario deben existir.
- En una base existente SIN historial se adopta **baseline 1**: se registra una
  fila BASELINE en `flyway_schema_history` y se omite V1. No se recrean tablas,
  no se vuelven a agregar columnas, ni se cambian datos, IDs o secuencias.
- `baseline-on-migrate=false` es deliberado: un arranque normal rechaza una base
  no vacía sin historial. El perfil `baseline` habilita la adopción explícita.
- `beforeBaseline.sql` es un callback PostgreSQL, no una migración versionada.
  Antes de registrar el baseline comprueba la presencia y tipos de todas las
  columnas esperadas y el NOT NULL/default de `featured`. Rechaza esquemas
  incompletos, `description` distinto de TEXT y bases ajenas que no cumplen esas
  comprobaciones. No modifica el esquema para ocultar diferencias.

El baseline declara un estado previo; NO demuestra igualdad completa del esquema.
El callback y Hibernate no auditan todos los índices, constraints, longitudes,
permisos ni datos. Revisar el esquema existente y conservar un respaldo antes de
adoptarlo. Si falla la comprobación, investigar la diferencia antes de continuar;
no cambiar `ddl-auto` a `update` ni usar `clean`/`repair` para saltar el error.
Si falla después de registrar el baseline, inspeccionar el historial antes de
reintentar. No subir la versión de baseline para omitir migraciones pendientes.

## Primera adopción en cada computadora

1. Detener otras instancias de la aplicación, respaldar la base fuera del
   repositorio y confirmar que `DB_URL` apunta a la base local correcta,
   normalmente `jdbc:postgresql://localhost:5432/aura_spa2`.
2. Configurar `DB_USERNAME` y `DB_PASSWORD` localmente, sin guardarlos en Git.
3. Revisar tablas/constraints frente a V1; el esquema debe incluir ya los cambios
   históricos descritos arriba. Las versiones antiguas/incompletas requieren una
   reconciliación revisada; este baseline no las actualiza automáticamente.
4. Ejecutar una sola vez con el perfil de adopción:

Windows (PowerShell):

```powershell
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=baseline"
```

Fedora:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=baseline
```

5. Verificar el historial y la validación JPA. Detener esa ejecución y volver al
   arranque habitual sin el perfil `baseline`; no persistirlo en variables de entorno.

Los datos de cada computadora permanecen locales. Git distribuye el SQL del
esquema, no los registros ni las credenciales. Una base que ya tiene historial
continúa normalmente y no necesita volver a adoptar baseline.

## Instalación nueva

Crear `aura_spa2` solamente si aún no existe, configurar las variables de entorno
y arrancar sin perfil `baseline`. Flyway crea el historial y aplica V1 antes de
la validación de Hibernate. Los inicializadores existentes agregan sucursales y
masajistas cuando sus tablas están vacías; V1 no contiene datos de negocio.

## Cambios futuros y git pull

Crear archivos UTF-8 en `src/main/resources/db/migration/`:

```text
V2__descripcion_del_cambio.sql
V3__descripcion_del_siguiente_cambio.sql
```

Usar versiones únicas y SQL PostgreSQL revisado para preservar datos. Probar
tanto una instalación vacía como una base con el historial anterior. No modificar
ni renombrar migraciones ya aplicadas: Flyway valida sus checksums. Una corrección
se publica en una nueva versión. No introducir ALTER en runners Java, ni usar
`schema.sql`, `data.sql` o Hibernate para gestionar el esquema en paralelo.

Después de `git pull`, arrancar con `./mvnw spring-boot:run` en Fedora o
`.\mvnw.cmd spring-boot:run` en Windows. Flyway aplica una sola vez las versiones
pendientes, antes de que Hibernate valide. No hace falta copiar ALTER entre PCs.
Ambas computadoras necesitan permisos DDL en su esquema y versiones compatibles
de PostgreSQL. Las rutas de migración son de classpath, independientes del SO.

## Verificar el historial

Desde una conexión local autenticada a la base correcta:

```sql
SELECT current_database(), current_schema();
SELECT installed_rank, version, description, type, script, checksum, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

En una instalación nueva aparece V1 con tipo SQL. En una existente adoptada
aparece versión 1 con tipo BASELINE (V1 omitida). Las siguientes versiones
aparecen como SQL. El log de arranque informa validación y migraciones aplicadas.

## Pruebas

`./mvnw test` (o `.\mvnw.cmd test`) ejecuta la suite habitual sobre H2 con la misma
V1 y `ddl-auto=validate`. No usa create/create-drop. H2 no sustituye la validación
de SQL específico de PostgreSQL.

`FlywayPostgresTests` es opt-in. Configurar `FLYWAY_TEST_URL`, `FLYWAY_TEST_USER`
y, si corresponde, `FLYWAY_TEST_PASSWORD` para una base PostgreSQL **desechable**,
y ejecutar `./mvnw -Dtest=FlywayPostgresTests test`. Nunca apuntar a `aura_spa2`.
Crea esquemas aleatorios de prueba y los conserva; no ejecuta DROP ni clean.
Comprueba V1, reejecución, baseline, conservación de registros/IDs/relaciones/
secuencias, rechazo de adopción implícita/incompleta y una V2 temporal exclusiva
de pruebas. Las pruebas no incorporan V2 al catálogo de producción.

## Auditoría y límites de esta entrega

- Se retiró el ALTER de `migrateReservationMasseuseColumn`: V1 ya incluye esa
  columna y la adopción exige que exista.
- Se retira también el backfill histórico ejecutado en cada arranque, que asignaba
  la primera masajista a reservas con `masseuse_id` nulo. Es una migración de datos
  no versionada que cambiaría relaciones durante la adopción. No se convierte en
  una migración automática; los valores nulos existentes se conservan.
- Se conserva el tipo INTEGER de `Reservation.branchId` y `Review.branchId`,
  aunque `Branch.id` es BIGINT. Es una diferencia preexistente del modelo; no se
  cambian IDs ni tipos como parte de esta tarea.
- El esquema real de `aura_spa2` no pudo inspeccionarse: PostgreSQL rechazó la
  autenticación con la configuración disponible. No se aplicó baseline ni SQL
  a esa base. La adopción local queda pendiente de credenciales válidas y revisión
  del esquema. No se modificaron contraseñas.
- La validación se realizó con PostgreSQL 18.6 aislado en el puerto 55432 y con H2.
  No se ejecutó Fedora; se revisaron rutas, comandos y permisos del Maven Wrapper.
  `mvnw` está versionado como ejecutable y con finales LF.

Resultados de validación (2026-10-05):

- Antes de los cambios: 60 pruebas, sin fallos; el test histórico
  `customerCanReserveAtSixPmWithSelectedMasseuse` también pasó.
- Suite de aplicación sobre PostgreSQL temporal más las primeras cinco pruebas
  de Flyway: 65 pruebas, sin fallos; Hibernate validó las cinco entidades.
- Verificación final: `mvnw.cmd package`, BUILD SUCCESS, 66 pruebas sin fallos ni
  omisiones (60 habituales sobre H2 y seis pruebas opt-in sobre PostgreSQL).
- Dependencias resueltas: Flyway core/PostgreSQL 12.4.0, administradas por Boot.
- JAR verificado: contiene V1, el callback y el perfil baseline. V1 aplicada en
  bases vacías; baseline 1 y V2 temporal verificadas en esquemas de prueba.
  Ninguna migración aplicada a `aura_spa2`.
- No se agregaron operaciones destructivas ni credenciales; se revisaron diff,
  nombres de archivos y reglas de exclusión. No se hizo commit ni push.

Referencias oficiales:
[Spring Boot: inicialización de bases](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
y [Flyway: baseline-on-migrate](https://documentation.red-gate.com/fd/flyway-baseline-on-migrate-setting-277578974.html).
