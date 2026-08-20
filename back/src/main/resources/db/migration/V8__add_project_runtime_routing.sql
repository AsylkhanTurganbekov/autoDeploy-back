ALTER TABLE projects ADD COLUMN public_port INTEGER;
ALTER TABLE projects ADD COLUMN health_path VARCHAR(256) NOT NULL DEFAULT '/health';
