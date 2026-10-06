-- Read-only PostgreSQL preflight: refuse adoption of an incomplete/older schema.
-- Flyway executes this only when creating a baseline, never on a fresh V1 install.
DO $$
DECLARE
    mismatches TEXT;
BEGIN
    SELECT string_agg(expected.table_name || '.' || expected.column_name, ', ')
    INTO mismatches
    FROM (VALUES
        ('branches', 'id', 'bigint'),
        ('branches', 'name', 'character varying'),
        ('branches', 'sector', 'character varying'),
        ('branches', 'address', 'character varying'),
        ('branches', 'opening_hours', 'character varying'),
        ('branches', 'latitude', 'double precision'),
        ('branches', 'longitude', 'double precision'),
        ('branches', 'active', 'boolean'),
        ('masseuses', 'id', 'bigint'),
        ('masseuses', 'name', 'character varying'),
        ('masseuses', 'specialty', 'character varying'),
        ('masseuses', 'phone', 'character varying'),
        ('masseuses', 'email', 'character varying'),
        ('masseuses', 'active', 'boolean'),
        ('spa_services', 'id', 'bigint'),
        ('spa_services', 'name', 'character varying'),
        ('spa_services', 'category', 'character varying'),
        ('spa_services', 'description', 'text'),
        ('spa_services', 'duration_minutes', 'integer'),
        ('spa_services', 'price', 'numeric'),
        ('spa_services', 'image_url', 'character varying'),
        ('spa_services', 'active', 'boolean'),
        ('spa_services', 'featured', 'boolean'),
        ('reservations', 'id', 'bigint'),
        ('reservations', 'service_id', 'bigint'),
        ('reservations', 'branch_id', 'integer'),
        ('reservations', 'masseuse_id', 'bigint'),
        ('reservations', 'customer_name', 'character varying'),
        ('reservations', 'customer_id_number', 'character varying'),
        ('reservations', 'customer_phone', 'character varying'),
        ('reservations', 'customer_email', 'character varying'),
        ('reservations', 'reservation_date', 'date'),
        ('reservations', 'reservation_time', 'character varying'),
        ('reservations', 'status', 'character varying'),
        ('reservations', 'access_code_hash', 'character varying'),
        ('reviews', 'id', 'bigint'),
        ('reviews', 'customer_name', 'character varying'),
        ('reviews', 'branch_id', 'integer'),
        ('reviews', 'rating', 'integer'),
        ('reviews', 'comment', 'character varying'),
        ('reviews', 'status', 'character varying')
    ) AS expected(table_name, column_name, data_type)
    LEFT JOIN information_schema.columns actual
      ON actual.table_schema = current_schema()
     AND actual.table_name = expected.table_name
     AND actual.column_name = expected.column_name
    WHERE actual.data_type IS DISTINCT FROM expected.data_type;

    IF mismatches IS NOT NULL THEN
        RAISE EXCEPTION 'Baseline refused: missing columns or unexpected types: %', mismatches;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = current_schema() AND table_name = 'spa_services'
          AND column_name = 'featured' AND is_nullable = 'NO'
          AND column_default IN ('false', 'false::boolean')
    ) THEN
        RAISE EXCEPTION 'Baseline refused: spa_services.featured must be NOT NULL DEFAULT false';
    END IF;
END
$$;
