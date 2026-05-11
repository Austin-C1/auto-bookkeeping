# v1.1.0

- Removed the old reset password and first-use backend flow from the packaged app.
- Changed package startup checks to use passwordless local login instead of the removed first-use endpoint.
- Kept the packaged app on `http://127.0.0.1:18880/bookkeeping` with backend API on `http://127.0.0.1:18001`.
- Added a v1.1.0 install and usage guide for end users.
- Rebuilt both the blank installer and update package from the corrected source.
