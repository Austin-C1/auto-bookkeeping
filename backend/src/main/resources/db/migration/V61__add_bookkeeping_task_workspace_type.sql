ALTER TABLE bookkeeping_tasks
    ADD COLUMN workspace_type VARCHAR(32) NOT NULL DEFAULT 'prematch';

CREATE INDEX idx_bookkeeping_tasks_workspace_type ON bookkeeping_tasks (workspace_type);
