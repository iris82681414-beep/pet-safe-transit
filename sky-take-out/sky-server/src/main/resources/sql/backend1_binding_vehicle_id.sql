-- Move cargo_vehicle_binding from plate-based vehicle reference to vehicle_id.
-- Run this once if your local database was initialized before this change.

ALTER TABLE cargo_vehicle_binding
  ADD COLUMN IF NOT EXISTS vehicle_id BIGINT;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = 'public'
      AND table_name = 'cargo_vehicle_binding'
      AND column_name = 'plate'
  ) THEN
    UPDATE cargo_vehicle_binding b
    SET vehicle_id = v.id
    FROM vehicles v
    WHERE b.vehicle_id IS NULL
      AND b.plate = v.plate;
  END IF;

  IF EXISTS (SELECT 1 FROM cargo_vehicle_binding WHERE vehicle_id IS NULL) THEN
    RAISE EXCEPTION 'cargo_vehicle_binding.vehicle_id cannot be filled from existing data';
  END IF;
END $$;

ALTER TABLE cargo_vehicle_binding
  ALTER COLUMN vehicle_id SET NOT NULL;

ALTER TABLE cargo_vehicle_binding
  DROP CONSTRAINT IF EXISTS cargo_vehicle_binding_plate_fkey;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conrelid = 'cargo_vehicle_binding'::regclass
      AND conname = 'cargo_vehicle_binding_vehicle_id_fkey'
  ) THEN
    ALTER TABLE cargo_vehicle_binding
      ADD CONSTRAINT cargo_vehicle_binding_vehicle_id_fkey
      FOREIGN KEY (vehicle_id) REFERENCES vehicles(id);
  END IF;
END $$;

DROP INDEX IF EXISTS ux_active_vehicle_binding;

CREATE INDEX IF NOT EXISTS idx_active_vehicle_binding
  ON cargo_vehicle_binding(vehicle_id)
  WHERE status = 'ACTIVE';

ALTER TABLE cargo_vehicle_binding
  DROP COLUMN IF EXISTS plate;
