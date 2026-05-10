$frontendUrl = if ($env:AUTO_BOOKKEEPING_FRONTEND_URL) {
    $env:AUTO_BOOKKEEPING_FRONTEND_URL
} else {
    'http://127.0.0.1:18880/bookkeeping'
}

Start-Process $frontendUrl | Out-Null
