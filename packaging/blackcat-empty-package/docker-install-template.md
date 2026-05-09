# 先安装 Docker Desktop

这份黑猫空白包给 Windows 新机器用，机器上还没有 Docker 也没关系，先把 Docker Desktop 装好就能直接跑。

## 安装前确认

- 电脑系统是 Windows 10 或 Windows 11 64 位。
- 安装 Docker Desktop 时需要管理员权限。
- 电脑能正常联网，首次安装和首次启动黑猫都要下载内容。

## 官方下载地址

- Docker Desktop 下载页：https://www.docker.com/products/docker-desktop/
- Docker Desktop Windows 安装说明：https://docs.docker.com/desktop/setup/install/windows-install/

## 安装步骤

1. 打开上面的官方下载页，下载 Docker Desktop for Windows。
2. 双击 `Docker Desktop Installer.exe` 开始安装。
3. 安装界面如果出现 `Use WSL 2 instead of Hyper-V`，直接保持勾选。
4. 安装过程中如果提示开启 WSL 2、Virtualization 或要求重启电脑，按提示继续并完成重启。
5. 安装完成后，从开始菜单打开 Docker Desktop。
6. 第一次打开时，等到 Docker Desktop 显示运行中，再去启动黑猫。

## 怎么判断 Docker 已经准备好

- Docker Desktop 主界面能正常打开，没有报错。
- 托盘里的 Docker 图标已经稳定，不再一直转。
- 如果你会用 PowerShell，可以执行 `docker version`，能看到版本信息就表示已经好。

## 常见问题

### 安装时提示 WSL 2 没开

按安装器提示继续，必要时重启电脑。重启后再打开 Docker Desktop。

### Docker Desktop 打不开或者一直启动中

先重启 Docker Desktop；还是不行，就重启一次 Windows。大多数新机器卡在这里，都是系统功能还没完全启用。

### 已经装好了，但启动黑猫时还是提示找不到 Docker

通常是 Docker Desktop 还没完全启动。先手动打开 Docker Desktop，看到运行中后，再双击 `launch-blackcat.cmd`。
