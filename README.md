# 自动做账

自动做账用于扫描 WhatsApp 群聊、读取皇冠注单、自动结算赛前和滚球账目，并生成每个群的账单文件和公司盈亏表。

## 当前能力

- 赛前工作台：扫描上游群、下游群，生成上游群账单、下游群账单、公司跟单表和公司总盈亏表。
- 滚球工作台：扫描滚球群，读取皇冠账号真实注单，生成滚球群账单、皇冠注单表和滚球盈亏表。
- WhatsApp 群配置：支持上游群、下游群、公司跟单群、滚球群、忽略群。
- 下游退水：只在下游赢单或赢半时按退水格数从赔率中扣除。
- 金额识别：`1a` 和 `1au` 按 `10000U` 处理。
- 文件清理：支持一键清除程序已生成的账单文件。

## 本机启动

在项目目录运行：

```powershell
.\launch-blackcat.cmd
```

默认入口：

```text
http://127.0.0.1:18882/bookkeeping
```

## GitHub 远程更新

1. 在 `config/update.json` 中配置：

```json
{
  "githubRepo": "你的 GitHub 用户名/auto-bookkeeping",
  "releaseApiUrl": "",
  "githubToken": ""
}
```

2. 构建更新包：

```powershell
.\build-blackcat-update-package.ps1
```

3. 将生成的 `auto-bookkeeping-update-v版本号.zip` 上传到 GitHub Release。
4. 其他用户在系统里的“系统更新”页面点击“检查更新”和“立即更新”。

更新包只覆盖程序文件，不覆盖本地配置、账号数据、数据库、日志、备份和更新缓存。

## 上传前检查

推荐在上传或发布前运行：

```powershell
$env:JAVA_HOME='C:\Users\kesul\Desktop\自动做账\黑猫自动做账\.tools\jdk-17.0.18+8'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
cd backend
.\gradlew.bat test
cd ..\frontend
npm test
npm run build
```
