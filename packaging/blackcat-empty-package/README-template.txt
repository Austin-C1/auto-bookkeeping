自动做账空白安装包 v{{VERSION}}

这份安装包不带任何业务数据，会在本机自动创建独立数据库，并默认免密进入。

首次使用请先看：

1. 01-install-docker-desktop.md
   说明 Docker Desktop 怎么下载、安装、配置和验证。

2. 02-start-blackcat-blank.md
   说明自动做账怎么启动、怎么配置群、怎么生成表格、怎么更新。

推荐启动方式：

- 一键安装启动.cmd

其他启动方式：

- launch-blackcat.cmd
- 桌面快捷方式：自动做账启动

只重新打开前端页面：

- open-blackcat-frontend.cmd

本包数据库容器：

- {{CONTAINER_NAME}}

本包数据库端口：

- {{DATABASE_PORT}}

注意：

1. 第一次启动前需要先安装并打开 Docker Desktop。
2. 第一次启动会下载 mysql:8.1，可能需要几分钟。
3. 启动后会自动创建或修正桌面快捷方式“自动做账启动”。
4. 更新程序不会覆盖本机登录状态、皇冠账号、上下游群配置和 Docker 数据库。
5. 不要随意删除 Docker 里的容器和数据卷，否则本机配置和做账数据可能丢失。
