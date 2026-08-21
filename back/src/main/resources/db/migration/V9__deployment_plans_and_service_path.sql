ALTER TABLE deployments ADD COLUMN IF NOT EXISTS service_path VARCHAR(200) NOT NULL DEFAULT '.';

CREATE TABLE IF NOT EXISTS project_services (
  id BIGSERIAL PRIMARY KEY,
  project_id BIGINT NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
  service_key VARCHAR(100) NOT NULL,
  relative_path VARCHAR(200) NOT NULL,
  runtime VARCHAR(32) NOT NULL,
  internal_port INTEGER NOT NULL,
  visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
  dockerfile_source VARCHAR(16) NOT NULL DEFAULT 'REPOSITORY',
  selected BOOLEAN NOT NULL DEFAULT FALSE,
  evidence TEXT NOT NULL DEFAULT '[]',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE(project_id, service_key)
);
CREATE INDEX IF NOT EXISTS project_services_project_idx ON project_services(project_id, selected);
