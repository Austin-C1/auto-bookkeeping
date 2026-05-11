import { lazy, Suspense } from 'react'
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { ConfigProvider, Spin } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import Layout from './components/Layout'
import { hasToken } from './utils'

const Login = lazy(() => import('./pages/Login'))
const Bookkeeping = lazy(() => import('./pages/Bookkeeping'))

const ProtectedRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const location = useLocation()
  const isAuthPage = location.pathname === '/login'

  if (isAuthPage) {
    return <>{children}</>
  }

  if (!hasToken()) {
    return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />
  }

  return <Layout>{children}</Layout>
}

const RouteFallback: React.FC = () => (
  <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '50vh' }}>
    <Spin size="large" />
  </div>
)

const LazyRoute: React.FC<{ children: React.ReactNode }> = ({ children }) => (
  <Suspense fallback={<RouteFallback />}>{children}</Suspense>
)

function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
        <Routes>
          <Route path="/login" element={<LazyRoute><Login /></LazyRoute>} />
          <Route path="/" element={<ProtectedRoute><Navigate to="/bookkeeping" replace /></ProtectedRoute>} />
          <Route path="/bookkeeping/*" element={<ProtectedRoute><LazyRoute><Bookkeeping /></LazyRoute></ProtectedRoute>} />
          <Route path="*" element={<ProtectedRoute><Navigate to="/bookkeeping" replace /></ProtectedRoute>} />
        </Routes>
      </BrowserRouter>
    </ConfigProvider>
  )
}

export default App
