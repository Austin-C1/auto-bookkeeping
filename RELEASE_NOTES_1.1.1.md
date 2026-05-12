# v1.1.1

- Removed the local bookkeeping login route and logout entry so the app opens directly to `/bookkeeping`.
- Disabled packaged local API and WebSocket authentication by default to prevent "缺少认证令牌" blocking the page.
- Changed startup checks to verify the backend version and restart outdated backend processes.
- Made update apply cleanup remove old backend jars before copying the new jar.
- Added the v1.1.1 install and usage guide.
