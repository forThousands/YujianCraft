<!--
本文件是 CurseForge、Modrinth 与 MC 百科发布页的本地双语底稿。
CurseForge / Modrinth：可直接复制 Markdown；图片使用 GitHub 公共原始链接。
MC 百科：建议复制正文后，在编辑器中将同名图片重新上传，以避免外链策略差异。
-->

# 御剑工艺 / Yujian Craft

![御剑工艺 / Yujian Craft](https://raw.githubusercontent.com/forThousands/YujianCraft/main/docs/images/hero-cover.png)

**以神识驭剑，以原版材料铸造属于 Minecraft 的飞剑战斗。**<br>
*Forge flying swords from familiar Minecraft materials, command them in formation, and take to the sky.*

御剑工艺是一个原创的 Minecraft Forge 御剑战斗与制作模组。它以原版材料和生存进程为基础，加入六柄飞剑组成的动态剑阵、主动或自动索敌、神识御剑、持续穿刺、御剑飞行，以及可逆的飞剑模块装配。

Yujian Craft is an original flying-sword combat and crafting mod for Minecraft Forge. Built around vanilla materials and survival progression, it adds six-sword formations, manual and automatic targeting, Spirit Sword Guidance, repeated piercing attacks, sword riding, and reversible sword modules.

> **当前版本 / Current version:** `0.9.19` Development Preview<br>
> **运行环境 / Environment:** Minecraft `1.20.1` · Forge `47.4.22` · Java `17` · Client & Server<br>
> **前置 / Dependencies:** 无必需第三方前置 / No required third-party dependencies

## 动态剑阵 / Dynamic Formations

![正向扇形阵 / Forward fan formation](https://raw.githubusercontent.com/forThousands/YujianCraft/main/docs/images/formation-fan-day.jpg)

六柄飞剑会在玩家身后列阵，并在停靠、出击、穿刺与回剑之间平滑过渡。三种阵型使用不同的净空路线，剑尖始终跟随飞行方向；剑罡、灵力流动、材质尾迹和破空声共同表现速度与力量。

Six swords form up behind the player and move smoothly between docking, attack, piercing, and return states. Three formations use different clearance routes, while blade direction, energy shells, flowing light, material-colored trails, and flight audio reinforce speed and impact.

- **A 正向扇形 / Forward Fan (default):** 先向外净空、抬升，再追击目标 / clears the player, climbs, then pursues.
- **B 环形 / Ring:** 位于玩家侧后方，可直接向前出击 / docks around the rear flanks and launches directly.
- **C 侧锋扇形 / Side-blade Fan:** 保留独特侧向视觉 / preserves the original side-facing formation.

![环形阵 / Ring formation](https://raw.githubusercontent.com/forThousands/YujianCraft/main/docs/images/formation-ring-aerial.jpg)

## 三种索敌与两种攻击 / Three Targeting and Two Attack Styles

- **准心锁定（默认）/ Crosshair Lock:** 左键锁定准心目标，不自动吸附 / left-click the creature under the crosshair, with no aim assist.
- **自动索敌 / Auto Targeting:** 在服务端配置半径内选择目标 / automatically selects a valid target in the server-defined radius.
- **神识御剑 / Spirit Sword Guidance:** 左键放出就位飞剑，以视角远程引导，再右键凝神锁敌 / launch ready swords, steer them by view direction, then commit them to a target.
- **单次出击 / Single Pass:** 每柄飞剑命中一次后返回阵位 / each sword returns after one hit.
- **持续穿刺 / Repeated Piercing:** 飞剑越过目标并沿弧线折返，直至目标死亡或失效 / swords pass through and curve back until the target dies or becomes invalid.

![神识御剑 / Spirit Sword Guidance](https://raw.githubusercontent.com/forThousands/YujianCraft/main/docs/images/spirit-sword-guidance-distant.jpg)

友伤开启且服务器规则允许时，主动锁定可选择其他玩家。目标、伤害和效果由服务端最终校验。Manual targeting can select other players when friendly fire and server rules allow it. Targets, damage, and effects remain server-authoritative.

## 御剑飞行 / Sword Riding

![御剑飞行 / Sword riding](https://raw.githubusercontent.com/forThousands/YujianCraft/main/docs/images/sword-riding-distant.jpg)

在设置中启用御剑飞行后，只要背包中有飞剑，双击空格即可起飞或落下。承载玩家的飞剑独立于六柄战斗剑阵，不会占用战斗飞剑。

Once Sword Riding is enabled, carrying any flying sword is enough to take off or land with a double-tap of Space. The riding sword is separate from the six combat swords.

## 可逆模块装配 / Reversible Module Crafting

![飞剑装配台 / Flying Sword Workbench](https://raw.githubusercontent.com/forThousands/YujianCraft/main/docs/images/flying-sword-workbench.jpg)

飞剑工作台支持安装、替换和拆除模块，拆除时返还材料，并通过实战同款渲染器实时预览外观。模块可加入独立灼烧、引雷、蚀毒、爆裂、箭雨、伤害、耐久、不毁和白热能量外观。

The Flying Sword Workbench installs, replaces, and removes modules while returning detached materials. Its live preview uses the same renderer as combat. Modules add custom burning, lightning, poison, bursts, arrow rain, damage, durability, indestructibility, and a white-hot energy appearance.

| 材料 / Material | 模块 / Module | 效果 / Effect |
| --- | --- | --- |
| 烈焰粉 / Blaze Powder | 火纹 / Flame Sigil | 灼烧与火星 / Burn and sparks |
| 避雷针 / Lightning Rod | 引雷 / Thunder Core | 非破坏性闪电 / Non-destructive lightning |
| 毒马铃薯 / Poisonous Potato | 蚀毒 / Venom Core | 中毒与雾迹 / Poison and mist |
| 火药 / Gunpowder | 爆裂 / Burst Core | 非破坏性爆裂 / Non-destructive blast |
| 箭 / Arrow | 箭雨 / Arrow Rain | 从天生成实体箭 / Physical arrows from above |
| 绿宝石 / Emerald | 锋锐 / Tempered Edge | 提升穿刺伤害 / More piercing damage |
| 钻石 / Diamond | 固本 / Enduring Core | 提升最大耐久 / More maximum durability |
| 下界之星 / Nether Star | 不毁 / Indestructible | 停止耐久消耗 / Prevents durability loss |
| 熔岩块 / Magma Block | 熔金剑心 / Molten Sword Heart | 白热能量剑身 / White-hot energy blade |

## 飞剑、合成与修复 / Swords, Crafting & Repair

木、石、铁、金、钻石、下界合金六种基础飞剑均由对应原版剑和四份相关材料合成。八枚紫水晶碎片环绕基础飞剑可合成对应的灵铸飞剑。飞剑可在原版铁砧中使用基础材料修复。

Wood, stone, iron, gold, diamond, and netherite flying swords are crafted from the matching vanilla sword plus four related materials. Surround a base flying sword with eight amethyst shards for its Spiritforged variant. Flying swords can be repaired in a vanilla anvil with their base material.

飞剑工作台配方：工作台位于中央，四个边位放铁锭，四角放紫水晶碎片。<br>
Flying Sword Workbench recipe: crafting table in the centre, iron ingots on the four sides, and amethyst shards in the corners.

## 默认操作 / Default Controls

- 主手持飞剑右键：召唤或收回六柄飞剑 / Right-click with a flying sword: summon or recall six swords.
- 左键：在准心锁定模式中锁定目标 / Left-click: lock a target in Crosshair Lock mode.
- `Ctrl+R`：切换阵型 / change formation.
- `Ctrl+I`：打开设置 / open settings.
- 背包中有飞剑时双击空格：御剑起飞或落下 / double-tap Space with a flying sword in the inventory to take off or land.

全部按键均可在 Minecraft 的按键绑定中修改。All controls are rebindable in Minecraft's key settings.

## 安装 / Installation

1. 安装 Minecraft `1.20.1`、Forge `47.4.22` 与 Java `17`。<br>
   Install Minecraft `1.20.1`, Forge `47.4.22`, and Java `17`.
2. 下载 `yujiancraft-<版本>.jar` 并放入实例的 `mods` 文件夹。<br>
   Download `yujiancraft-<version>.jar` and place it in the instance's `mods` folder.
3. 多人游戏中，客户端与服务器应安装相同版本。<br>
   In multiplayer, clients and the server should use the same version.

开发预览版可能调整物品数据、配置格式与平衡数值，更新前请备份世界。`0.9.17` 将内部模组 ID 从 `swordflight` 改为 `yujiancraft`，更早版本的物品、方块与配置不会自动迁移。

Preview releases may change item data, configuration formats, and balance. Back up worlds before updating. Version `0.9.17` changed the internal mod ID from `swordflight` to `yujiancraft`; content from older versions does not migrate automatically.

## 设置、反馈与协议 / Settings, Feedback & License

`Ctrl+I` 可调整索敌、攻击、优化第三人称、御剑飞行、光效与亮度。光影开启时自动使用柔和亮度；光敏性癫痫风险者应关闭高亮和闪烁效果，如有不适立即停止使用。

`Ctrl+I` configures targeting, attacks, the over-the-shoulder camera, sword riding, effects, and brightness. Shader detection selects a softer brightness profile. Players sensitive to flashing imagery should disable bright effects and stop immediately if discomfort occurs.

- 源代码与下载 / Source & downloads: https://github.com/forThousands/YujianCraft
- 问题反馈 / Issues: https://github.com/forThousands/YujianCraft/issues
- 版本记录 / Changelog: https://github.com/forThousands/YujianCraft/blob/main/CHANGELOG.md
- 协议 / License: MIT

本项目围绕 Minecraft 的材料、锻造与生物生态独立设计，不采用现有作品的专有名称、角色、剧情、阵法名称、造型或素材。

This is an original design built around Minecraft materials, crafting, and ecology. It does not use proprietary names, characters, plots, formation names, designs, or assets from existing works.
