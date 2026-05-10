import { ReactNode, useMemo, useState } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { Layout as AntLayout, Button, Drawer, Menu, Modal, Tag } from 'antd'
import type { MenuProps } from 'antd'
import {
  CalculatorOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  FileExcelOutlined,
  LogoutOutlined,
  MenuOutlined,
  MessageOutlined,
  ReconciliationOutlined,
  UserAddOutlined,
} from '@ant-design/icons'
import { useMediaQuery } from 'react-responsive'
import { removeToken, getVersionInfo, getVersionText } from '../utils'

const { Header, Content, Sider } = AntLayout

interface LayoutProps {
  children: ReactNode
}

const menuItems: MenuProps['items'] = [
  { key: '/bookkeeping', icon: <DashboardOutlined />, label: '赛前工作台' },
  { key: '/bookkeeping/rolling', icon: <DatabaseOutlined />, label: '滚球工作台' },
  { key: '/bookkeeping/crown/accounts', icon: <UserAddOutlined />, label: '皇冠账号管理' },
  { key: '/bookkeeping/whatsapp/groups', icon: <MessageOutlined />, label: 'WhatsApp 群配置' },
  { key: '/bookkeeping/telegram/groups', icon: <MessageOutlined />, label: 'Telegram 群配置' },
  { type: 'divider' },
  { key: '/bookkeeping/crown/wagers', icon: <DatabaseOutlined />, label: '皇冠投注中心' },
  { key: '/bookkeeping/prematch/reconciliation', icon: <ReconciliationOutlined />, label: '赛前对账中心' },
  { key: '/bookkeeping/rolling/reconciliation', icon: <ReconciliationOutlined />, label: '滚球对账中心' },
  { key: '/bookkeeping/excel', icon: <FileExcelOutlined />, label: '报表中心' },
  { type: 'divider' },
  { key: 'logout', icon: <LogoutOutlined />, label: '退出登录' },
]

const pageKeys = [
  '/bookkeeping/rolling/reconciliation',
  '/bookkeeping/prematch/reconciliation',
  '/bookkeeping/crown/accounts',
  '/bookkeeping/whatsapp/groups',
  '/bookkeeping/telegram/groups',
  '/bookkeeping/crown/wagers',
  '/bookkeeping/rolling',
  '/bookkeeping/excel',
  '/bookkeeping',
]

const legacyPageKeys: Record<string, string> = {
  '/bookkeeping/whatsapp/orders': '/bookkeeping/prematch/reconciliation',
  '/bookkeeping/reconciliation': '/bookkeeping/rolling/reconciliation',
}

const Layout: React.FC<LayoutProps> = ({ children }) => {
  const navigate = useNavigate()
  const location = useLocation()
  const isMobile = useMediaQuery({ maxWidth: 768 })
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  const selectedKeys = useMemo(() => {
    const pathname = location.pathname.replace(/\/$/, '') || '/bookkeeping'
    const aliasedPathname = legacyPageKeys[pathname] || pathname
    const selected = pageKeys.find((key) => aliasedPathname === key || aliasedPathname.startsWith(`${key}/`))
    return [selected || '/bookkeeping']
  }, [location.pathname])

  const handleLogout = () => {
    removeToken()
    navigate('/login', { replace: true })
  }

  const handleMenuClick = ({ key }: { key: string }) => {
    if (key === 'logout') {
      Modal.confirm({
        title: '确认退出',
        content: '退出后需要重新登录。',
        okText: '确认',
        cancelText: '取消',
        onOk: handleLogout,
      })
      return
    }

    navigate(key)
    if (isMobile) {
      setMobileMenuOpen(false)
    }
  }

  const brand = (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10, minWidth: 0 }}>
      <CalculatorOutlined style={{ color: '#52c41a', fontSize: 22 }} />
      <span style={{ color: '#fff', fontSize: 18, fontWeight: 700, whiteSpace: 'nowrap' }}>
        自动做账
      </span>
      <Tag
        color="success"
        style={{
          margin: 0,
          background: 'rgba(82,196,26,0.12)',
          borderRadius: 4,
          lineHeight: 1.5,
          maxWidth: 72,
          overflow: 'hidden',
          textOverflow: 'ellipsis',
        }}
      >
        {getVersionInfo().gitTag || `v${getVersionText()}`}
      </Tag>
    </div>
  )

  const menu = (
    <Menu
      mode="inline"
      theme="dark"
      selectedKeys={selectedKeys}
      items={menuItems}
      onClick={handleMenuClick}
      style={{ borderRight: 0, height: '100%', background: 'transparent' }}
    />
  )

  if (isMobile) {
    return (
      <AntLayout style={{ minHeight: '100vh' }}>
        <Header style={{ background: '#06101f', padding: '0 16px', display: 'flex', justifyContent: 'space-between' }}>
          {brand}
          <Button type="text" icon={<MenuOutlined />} style={{ color: '#fff' }} onClick={() => setMobileMenuOpen(true)} />
        </Header>
        <Content style={{ padding: 12, background: '#eef2f7', minHeight: 'calc(100vh - 64px)', overflowX: 'hidden' }}>
          {children}
        </Content>
        <Drawer title="导航" placement="left" open={mobileMenuOpen} onClose={() => setMobileMenuOpen(false)} styles={{ body: { padding: 0, background: '#06101f' } }}>
          {menu}
        </Drawer>
      </AntLayout>
    )
  }

  return (
    <AntLayout style={{ height: '100vh', overflow: 'hidden' }}>
      <Sider width={236} style={{ background: '#06101f', height: '100vh', position: 'fixed', left: 0, top: 0 }}>
        <div style={{ padding: 18, borderBottom: '1px solid rgba(255,255,255,0.08)' }}>
          {brand}
        </div>
        <div style={{ height: 'calc(100vh - 74px)', overflowY: 'auto', paddingTop: 10 }}>{menu}</div>
      </Sider>
      <AntLayout style={{ marginLeft: 236, height: '100vh' }}>
        <Content style={{ padding: 24, background: '#eef2f7', height: '100vh', overflowY: 'auto', overflowX: 'hidden' }}>
          {children}
        </Content>
      </AntLayout>
    </AntLayout>
  )
}

export default Layout
