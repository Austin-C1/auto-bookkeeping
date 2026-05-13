# v1.1.2

- Removed unrelated frontend and backend features instead of only hiding their routes.
- Removed unrelated legacy modules instead of only hiding their entry points.
- Trimmed frontend and backend dependencies to the bookkeeping runtime.
- Removed stale source tests and package checks that referenced deleted features.
- Removed obsolete package scripts and local generated artifacts.
- Kept database migration history so installed users can still update safely.
- Verified local launch at `http://127.0.0.1:18880/bookkeeping` with no password, reset, missing-auth, or network blocking page.
