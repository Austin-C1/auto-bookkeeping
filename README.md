# 自动做账

自动做账用于读取 Crown 投注、扫描 WhatsApp 和 Telegram 群订单，生成赛前和滚球账目报表。

## 当前功能

- 赛前工作台
- 滚球工作台
- Crown 账号和投注导入
- WhatsApp 群配置和订单扫描
- Telegram 群配置和订单扫描
- 赛果抓取
- 赛前对账
- 滚球对账
- Excel 报表
- 系统更新

## 本机启动

```powershell
.\launch-blackcat.cmd
```

默认打开：

```text
http://127.0.0.1:18880/bookkeeping
```

## 发布包

第一次安装使用：

```text
AutoBookkeeping-Blank-v版本号.zip
```

旧版本更新使用：

```text
auto-bookkeeping-update-v版本号.zip
```

发布说明和安装说明在 `docs/zh/` 目录。

## 上传前检查

```powershell
$env:JAVA_HOME='C:\Users\kesul\Desktop\自动做账\黑猫自动做账\.tools\jdk-17.0.18+8'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd backend
.\gradlew.bat test
.\gradlew.bat bootJar
cd ..\frontend
npm test
npm run build
```
