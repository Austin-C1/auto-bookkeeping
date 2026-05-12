# 自动做账安装说明书

适用版本：v{{VERSION}}

这份说明给第一次安装的人使用，重点包含 Docker Desktop 的下载、安装、配置，以及自动做账的启动、快捷方式和数据保留。

## 一、安装前准备

电脑需要满足下面条件：

1. 系统是 Windows 10 64 位或 Windows 11 64 位。
2. 电脑可以正常联网。
3. 当前 Windows 用户可以安装软件。
4. C 盘或安装盘建议至少保留 10GB 可用空间。
5. 内存建议 8GB 或以上。
6. 浏览器建议使用 Chrome、Edge 或其他新版浏览器。

需要提前知道的事情：

1. 自动做账本身不需要你安装 Java 或 Node。
2. 程序会用 Docker Desktop 在本机准备一个 MySQL 数据库。
3. 第一次启动会下载 `mysql:8.1`，网速慢时可能需要几分钟。
4. 本机版本没有登录页、密码页、重置密码页，打开后直接进入自动做账。
5. 桌面会自动生成或修正 `自动做账启动` 快捷方式。

## 二、下载 Docker Desktop

只从 Docker 官方地址下载：

- Docker Desktop 官方下载页：https://www.docker.com/products/docker-desktop/
- Docker Windows 安装说明：https://docs.docker.com/desktop/setup/install/windows-install/
- WSL 官方说明：https://learn.microsoft.com/windows/wsl/install

下载步骤：

1. 打开 Docker Desktop 官方下载页。
2. 点击 Windows 版本的下载按钮。
3. 下载完成后会得到 `Docker Desktop Installer.exe`。
4. 不要从网盘、群文件或不明网站下载 Docker 安装包。

## 三、安装 Docker Desktop

安装步骤：

1. 双击 `Docker Desktop Installer.exe`。
2. 如果 Windows 弹出权限确认，点击允许。
3. 安装界面如果出现 `Use WSL 2 instead of Hyper-V`，保持勾选。
4. 安装界面如果提示启用 WSL 2、Virtual Machine Platform 或 Windows 相关组件，按提示继续。
5. 安装完成后，如果提示重启电脑，先重启电脑。
6. 重启后，从开始菜单打开 Docker Desktop。

安装时建议保持默认选项，不要切换到 Windows Containers。自动做账使用 Linux 容器。

## 四、配置 Docker Desktop

第一次打开 Docker Desktop 后，按下面检查：

1. 等 Docker Desktop 主界面显示已经运行，不要在它还在启动时打开自动做账。
2. 打开 Docker Desktop 的 Settings。
3. 在 General 里确认 `Use WSL 2 based engine` 是开启状态。
4. 在 Resources 里建议给 Docker 保留 4GB 左右内存。
5. 如果 C 盘空间很小，可以在 Resources 里把 Docker 数据位置改到空间更大的磁盘。
6. 如果网络需要代理，在 Docker Desktop 的代理设置里填写代理。
7. 修改设置后点击 Apply & Restart。

一般用户只需要确认 WSL 2 开启即可，不需要修改 Docker Engine。

## 五、验证 Docker 是否可用

普通用户可以只看 Docker Desktop 是否正常打开。如果需要进一步确认，可以用 PowerShell 检查：

```powershell
docker version
docker ps
```

正常情况：

1. `docker version` 能看到 Client 和 Server 信息。
2. `docker ps` 不报错。

如果只能看到 Client，看不到 Server，说明 Docker Desktop 还没有真正启动完成。

## 六、安装自动做账

步骤如下：

1. 把当前安装包解压到固定目录。
2. 建议放在：

```text
C:\AutoBookkeeping-Blank-v{{VERSION}}
```

3. 不要直接在压缩包里面双击运行。
4. 不建议放在权限受限的系统目录。
5. 打开 Docker Desktop，并等待 Docker Desktop 运行完成。
6. 双击 `一键安装启动.cmd` 或 `launch-blackcat.cmd`。
7. 第一次启动会自动创建本机数据库容器。
8. 启动完成后，浏览器会自动打开：

```text
http://127.0.0.1:18880/bookkeeping
```

第一次启动可能较慢，原因通常是 Docker 正在下载 MySQL 镜像。等待脚本窗口显示完成后再操作页面。

## 七、桌面快捷启动方式

程序启动后会自动在桌面创建或修正：

```text
自动做账启动
```

以后可以直接双击桌面的 `自动做账启动` 打开程序。

如果桌面已有旧快捷方式，程序会把它改成当前安装目录里的：

```text
launch-blackcat.cmd
```

## 八、本安装包使用的数据

本安装包会创建独立的 Docker 数据库：

```text
{{CONTAINER_NAME}}
```

数据库端口：

```text
{{DATABASE_PORT}}
```

不要随便删除 Docker 里的容器或数据卷。删除后，账号配置、群配置、登录状态和已有数据可能丢失。

## 九、常见问题

### 1. Docker Desktop 打不开

处理方式：

1. 重启 Docker Desktop。
2. 不行就重启 Windows。
3. 仍然不行，确认 Windows 的虚拟化功能已开启。
4. 公司电脑如果限制虚拟化，需要管理员处理。

### 2. 提示 WSL 2 没安装

处理方式：

1. 按 Docker Desktop 的提示安装 WSL 2。
2. 或参考 Microsoft 官方 WSL 说明安装。
3. 安装完成后重启 Windows。
4. 再重新打开 Docker Desktop。

### 3. 启动自动做账时提示找不到 Docker

通常是 Docker Desktop 没启动完成。

处理方式：

1. 先手动打开 Docker Desktop。
2. 等 Docker Desktop 运行稳定。
3. 再双击 `launch-blackcat.cmd` 或桌面 `自动做账启动`。

### 4. 第一次启动很慢

这是正常情况。第一次需要下载 `mysql:8.1`，网速慢时会更久。

处理方式：

1. 保持网络稳定。
2. 不要重复关闭启动窗口。
3. 如果下载一直失败，检查网络或 Docker Desktop 的代理设置。

### 5. 浏览器没有自动打开

手动打开下面地址：

```text
http://127.0.0.1:18880/bookkeeping
```

也可以双击：

```text
open-blackcat-frontend.cmd
```

### 6. Windows 提示脚本不安全

处理方式：

1. 确认文件来自 GitHub 官方 Release。
2. 右键文件，选择允许或解除锁定。
3. 再双击启动。

## 十、卸载和重装

只删除程序文件：

1. 关闭浏览器页面。
2. 删除解压出来的程序文件夹。
3. 桌面快捷方式可以删除。

彻底删除本机数据：

1. 打开 Docker Desktop。
2. 找到容器 `{{CONTAINER_NAME}}`。
3. 停止并删除容器。
4. 删除对应的数据卷。

注意：彻底删除会清空本机账号配置、群配置和做账数据。
