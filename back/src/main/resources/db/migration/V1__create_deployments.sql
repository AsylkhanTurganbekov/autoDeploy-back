CREATE TABLE deployments (
  id BIGSERIAL PRIMARY KEY,
  project_slug VARCHAR(200) NOT NULL,
  commit_sha VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX deployments_project_created_idx ON deployments (project_slug, created_at DESC);
