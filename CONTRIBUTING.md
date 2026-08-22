# 参与开发

感谢参与 Swordflight。提交改动前，请先确认目标分支能够在 Java 17 下完成构建。

## 环境

- Minecraft 1.20.1
- Forge 47.4.22
- Java 17
- 项目自带 Gradle Wrapper

## 本地检查

```powershell
./gradlew.bat build
python -m unittest discover -s devtools/control_panel -p "test_*.py" -v
```

涉及配方时，请只编辑 `devtools/control_panel/recipes.json`，随后运行数据生成任务；不要单独维护生成目录中的配方副本。

涉及平衡数值时，优先使用根目录的 `启动御剑开发台.bat`。本机路径只保存在被忽略的 `.local.json`，不要提交个人游戏目录、令牌、日志或构建产物。

## 提交建议

- 一个提交聚焦一个可验证的改动。
- Bug 修复说明复现条件、预期结果和验证方式。
- 视觉改动附原版渲染与常见光影环境的截图，并说明性能影响。
- 不使用其他作品的专有名称、角色、剧情、造型或可识别素材。
