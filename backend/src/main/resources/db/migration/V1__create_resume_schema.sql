CREATE TABLE resumes (
  id UUID PRIMARY KEY,
  original_filename VARCHAR(255) NOT NULL,
  mime_type VARCHAR(100) NOT NULL,
  checksum VARCHAR(64) NOT NULL,
  status VARCHAR(20) NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_resumes_status CHECK (status IN ('PARSING', 'READY', 'FAILED')),
  CONSTRAINT chk_resumes_checksum CHECK (checksum ~ '^[0-9a-f]{64}$')
);

CREATE INDEX idx_resumes_checksum ON resumes (checksum);
CREATE INDEX idx_resumes_status ON resumes (status);

CREATE TABLE resume_contents (
  id UUID PRIMARY KEY,
  resume_id UUID NOT NULL REFERENCES resumes (id) ON DELETE CASCADE,
  version_number INT NOT NULL CHECK (version_number >= 1),
  content JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_resume_contents_resume_version UNIQUE (resume_id, version_number)
);

CREATE TABLE resume_templates (
  id UUID PRIMARY KEY,
  resume_id UUID NOT NULL UNIQUE REFERENCES resumes (id) ON DELETE CASCADE,
  document_type VARCHAR(20) NOT NULL,
  source_checksum VARCHAR(64) NOT NULL,
  template JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_resume_templates_doc_type CHECK (document_type IN ('DOCX', 'PDF'))
);

CREATE TABLE resume_files (
  id UUID PRIMARY KEY,
  resume_id UUID NOT NULL REFERENCES resumes (id) ON DELETE CASCADE,
  kind VARCHAR(20) NOT NULL,
  storage_key VARCHAR(512) NOT NULL UNIQUE,
  mime_type VARCHAR(100) NOT NULL,
  size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
  checksum VARCHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT chk_resume_files_kind CHECK (kind IN ('ORIGINAL', 'GENERATED', 'PREVIEW'))
);

CREATE INDEX idx_resume_files_resume_kind ON resume_files (resume_id, kind);

CREATE TABLE resume_versions (
  id UUID PRIMARY KEY,
  resume_id UUID NOT NULL REFERENCES resumes (id) ON DELETE CASCADE,
  version_number INT NOT NULL CHECK (version_number >= 1),
  content_snapshot_id UUID REFERENCES resume_contents (id),
  template_snapshot_id UUID REFERENCES resume_templates (id),
  generated_storage_key VARCHAR(512),
  generated_checksum VARCHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_resume_versions_resume_version UNIQUE (resume_id, version_number)
);

CREATE TABLE resume_sections (
  id UUID PRIMARY KEY,
  resume_id UUID NOT NULL REFERENCES resumes (id) ON DELETE CASCADE,
  section_key VARCHAR(100) NOT NULL,
  title VARCHAR(255) NOT NULL,
  position INT NOT NULL CHECK (position >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT uq_resume_sections_resume_key UNIQUE (resume_id, section_key)
);

CREATE INDEX idx_resume_sections_resume_position ON resume_sections (resume_id, position);
