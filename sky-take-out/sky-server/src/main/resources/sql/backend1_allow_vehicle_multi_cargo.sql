-- Allow one vehicle to carry multiple active cargo records.
-- Keep one active vehicle per cargo by retaining ux_active_cargo_binding.

DROP INDEX IF EXISTS ux_active_vehicle_binding;

CREATE INDEX IF NOT EXISTS idx_active_vehicle_binding
  ON cargo_vehicle_binding(vehicle_id)
  WHERE status = 'ACTIVE';
