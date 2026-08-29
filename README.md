# 御剑工艺 / Yujian Craft

<p align="center">
  <img src="docs/images/hero-cover.png" alt="御剑工艺 / Yujian Craft" width="100%">
</p>

<p align="center">
  <strong>以神识驭剑，以原版材料铸造属于 Minecraft 的飞剑战斗。</strong><br>
  <em>Forge flying swords from familiar Minecraft materials, command them in formation, and take to the sky.</em>
</p>

<p align="center">
  <a href="https://www.curseforge.com/minecraft/mc-mods/yujian-craft">CurseForge</a>
  · <a href="https://github.com/forThousands/YujianCraft/releases">GitHub Releases</a>
  · <a href="CHANGELOG.md">Changelog</a>
  · <a href="https://github.com/forThousands/YujianCraft/issues">Issues</a>
</p>

> **当前版本 / Current version:** `0.14.0`（Release）<br>
> **环境 / Environment:** Minecraft `1.20.1` · Forge `47.4.22` · Java `17` · Client & Server<br>
> **协议 / License:** [MIT](LICENSE) · 无必需前置模组 / No required third-party dependency

## 模组简介 / About

御剑工艺是一个原创的 Minecraft Forge 御剑战斗与制作模组。它以灵矿资源和原版锻造语言为基础，加入六柄御器组成的动态阵列、三种索敌方式、六类御器术式、中近距离五段连势、御剑飞行、可逆飞剑模块，以及将其他模组武器、盾牌、工具与钓具炼成“万象御器”的淬灵系统。

Yujian Craft is an original flying-sword combat and crafting mod for Minecraft Forge. Built around Spirit Ore and vanilla-style progression, it adds six-implement formations, three targeting styles, six Yujian arts, a five-stage close-range combo stance, sword riding, reversible modules, and spirit tempering that turns third-party weapons, shields, tools, and fishing implements into Myriad artifacts.

## 剑阵与战斗 / Formations & Combat

<p align="center">
  <img src="docs/images/formation-fan-day.jpg" alt="模式 A 正向扇形阵 / Mode A forward fan formation" width="49%">
  <img src="docs/images/formation-ring-day.jpg" alt="模式 B 环形阵 / Mode B ring formation" width="49%">
</p>

<p align="center"><sub>模式 A 正向扇形阵 / Mode A forward fan · 模式 B 环形阵 / Mode B ring</sub></p>

- 六柄飞剑在玩家身后列阵，停靠、出击、回剑均使用平滑轨迹。
- 三种阵型各有独立的净空与追击路线，避免飞剑穿过玩家。
- 剑尖始终跟随飞行方向；剑罡、灵力流动、材质尾迹和破空声共同表现速度与力量。
- 单次出击适合稳定战斗；持续穿刺会越过目标、沿弧线折返，直至目标死亡或失效。

Six flying swords dock behind the player and transition smoothly between formation, attack, and return paths. Each formation uses its own clearance route, while blade orientation, energy shells, material-colored trails, and flight audio reinforce direction and speed. Choose reliable single passes or repeated piercing runs that curve back through the target.

| 阵型 / Formation | 行为 / Behaviour |
| --- | --- |
| **A（默认）正向扇形 / Forward Fan (default)** | 先向外净空并抬升，再追击目标 / Clears the player, climbs, then pursues |
| **B 环形 / Ring** | 位于玩家侧后方，可直接向前出击 / Docks around the rear flanks and launches directly |
| **C 侧锋扇形 / Side-blade Fan** | 保留独特侧向视觉，飞行路线与 A 相同 / Keeps the original side-facing look with Mode A routing |

## 三种索敌方式 / Three Targeting Styles

<p align="center">
  <img src="docs/images/spirit-sword-guidance-ascent.jpg" alt="神识御剑上升段 / Spirit Sword Guidance ascent" width="49%">
  <img src="docs/images/spirit-sword-guidance-impact.jpg" alt="神识御剑即将命中 / Spirit Sword Guidance before impact" width="49%">
</p>

<p align="center"><sub>神识御剑：放出就位飞剑，以视角引导远距离轨迹，再凝神锁敌。<br>Spirit Sword Guidance: launch ready swords, steer them by view direction, then commit them to a distant target.</sub></p>

| 模式 / Mode | 操作与用途 / Controls and use |
| --- | --- |
| **准心锁定（默认）/ Crosshair Lock (default)** | 手持飞剑左键锁定准心目标；标记持续到目标死亡或锁定新目标，同一次点击仍会正常近战 / Left-click a target under the crosshair; the marker remains until death or a new lock, and the same click still performs melee |
| **自动索敌 / Auto Targeting** | 在服务端配置半径内自动选择可攻击目标 / Automatically selects a valid target within the server-defined radius |
| **神识御剑 / Spirit Sword Guidance** | 左键放出当前就位飞剑，以视角引导；右键锁定准心目标 / Left-click to launch ready swords, steer by view, then right-click to lock a target |

友伤开启且服务器规则允许时，主动锁定可选择其他玩家。服务器负责最终目标与伤害校验。

When friendly fire and server rules allow it, manual lock can target other players. Final target and damage validation remain server-authoritative.

## 御剑飞行 / Sword Riding

<p align="center">
  <img src="docs/images/sword-riding-distant.jpg" alt="御剑飞行远景 / Sword riding from a distant view" width="100%">
</p>

启用御剑飞行后，只要背包中有飞剑，双击空格即可踏剑起飞或落下。承载玩家的飞剑独立于六柄战斗剑阵，不会占用已召唤的战斗飞剑；升降、转向与狭窄空间处理均围绕第三人称操作设计。

Once sword riding is enabled, carrying any flying sword in the inventory is enough to take off or land with a double-tap of Space. The riding sword is separate from the six combat swords, with ascent, descent, steering, and confined-space behaviour designed for third-person play.

## 剑阵终结与御剑连势 / Celestial Sword Array & Combo Stance

当前版本延续并完善剑阵术式的高空演出：六柄就绪飞剑升至目标上空，鎏金或三华阵盘持续旋转、追踪并降下环形剑气，最终召下一柄继承当前飞剑模型、剑罡和核心外观的巨大飞剑。落地伤害与范围由服务端配置，后处理使用专用识别信号区分剑阵、天空和太阳；亮度、扩张、粒子雾等可在开发者选项中比较。按 `H` 可直接对准索敌范围内目标发动，无需先建立持久锁定；`Ctrl+K` 切换剑阵形态。

The current release continues the complete celestial Sword Array performance. Six ready swords occupy stations over the target while a Gilded or Tricolour seal rotates, tracks and rains ring-shaped sword-qi, ending with a colossal sword that inherits the active item model, aura and installed-core visuals. Impact damage and radius are server-configurable. A dedicated signal channel separates the array from sky and sun during post-processing, while brightness, expansion and atmosphere remain optional developer comparisons. Aim at a valid target and press `H` without establishing a persistent lock; `Ctrl+K` switches the seal style.

按 `X` 可在六器展开时进入御剑连势。五段普攻依次完成左右交叉穿刺、近身环斩、后撤蓄势六剑齐发与加速巨剑终结；输入缓冲允许玩家提前点击下一段，软目标选择优先准心附近敌人。连势渲染使用客户端同帧轨迹重建以消除移动拖行，伤害和终结倍率仍只由服务端裁决。

Press `X` with all six implements deployed to enter Yujian Combo Stance. Five attacks chain left/right cross-piercing, a close circular sweep, a retreat-and-charge six-sword release, and an accelerated giant-sword finisher. Input buffering accepts the next click before the current animation ends, while soft targeting favours enemies near the crosshair. Client-side same-frame path reconstruction keeps the formation attached cleanly during movement; damage and finisher multipliers remain server-authoritative.

## 飞剑工作台与模块 / Flying Sword Workbench & Modules

<p align="center">
  <img src="docs/images/flying-sword-workbench.jpg" alt="飞剑工作台装配界面与实时预览 / Flying Sword Workbench with live preview" width="100%">
</p>

`0.14.0` 为飞剑工作台、淬灵台和补灵台重绘了统一的 `32×32` 像素贴图：顶面突出飞剑、灵炉与灵晶，侧面则使用更克制的结构和灵火观察窗，避免重复顶面构图。

Version `0.14.0` gives the Flying Sword Workbench, Spirit Tempering Table and Spirit Replenishing Table a unified `32×32` pixel-art set. Their top faces emphasize the sword, refining furnace and spirit crystal, while quieter sides avoid repeating the same top-down composition.

模块可以安装、替换和拆除，拆除时返还材料。界面右侧使用与实战相同的实体渲染管线实时预览；拖动可旋转，滚轮可缩放。三级模块通常按 `1 / 16 / 64` 份材料安装为 I / II / III。

Modules can be installed, replaced, and removed, returning their materials when detached. The workbench previews the real in-game sword renderer; drag to rotate and use the wheel to zoom. Tiered modules normally consume `1 / 16 / 64` items for levels I / II / III.

| 材料 / Material | 模块 / Module | 效果 / Effect |
| --- | --- | --- |
| 烈焰粉 / Blaze Powder | 火纹 / Flame Sigil | 独立灼烧效果与剑刃火星 / Custom burn effect and edge sparks |
| 避雷针 / Lightning Rod | 引雷 / Thunder Core | 不破坏方块的视觉闪电与可调伤害 / Non-destructive lightning with configurable damage |
| 毒马铃薯 / Poisonous Potato | 蚀毒 / Venom Core | 独立中毒效果与雾状尾迹 / Custom poison and a mist trail |
| 火药 / Gunpowder | 爆裂 / Burst Core | 不破坏方块的爆裂与橙红脉冲 / Non-destructive blast and orange-red pulse |
| 箭 / Arrow | 箭雨 / Arrow Rain | 从目标上方生成实体箭与风纹 / Physical arrows from above with wind streaks |
| 绿宝石 / Emerald | 锋锐 / Tempered Edge | 提升直接穿刺伤害 / Increases direct piercing damage |
| 钻石 / Diamond | 固本 / Enduring Core | 提升最大耐久 / Increases maximum durability |
| 下界之星 / Nether Star | 不毁 / Indestructible | 停止消耗耐久 / Prevents durability loss |
| 熔岩块 / Magma Block | 熔金剑心 / Molten Sword Heart | 白热能量剑身外观 / White-hot energy blade appearance |

命中特效对同一目标共享服务端冷却，避免持续穿刺高频触发造成卡顿。不同模块会以克制的火星、电弧、雾迹、脉冲或风纹改变飞剑外观；命中还具有短促闪光、冲击环和分层声音反馈。

Hit effects share a per-target server cooldown to keep repeated piercing efficient. Installed modules add restrained sparks, arcs, mist, pulses, or wind streaks, while short flashes, impact rings, and layered audio make each strike readable.

## 万象飞剑与淬灵 / Myriad Flying Swords & Spirit Tempering

淬灵台允许本模组飞剑、原版物品以及其他模组的非堆叠武器、盾牌、工具和钓具承载或重铸御剑系统。将待淬灵物品放入上方槽位，将一柄本模组飞剑作为灵性核心放入下方槽位，依次完成“器物定性”和“塑形”，确认后再进入试锋境。点击入境时会立即消耗经验和核心飞剑；每件物品最多淬灵两次。

The Spirit Tempering Table accepts native Yujian swords and non-stackable weapons, shields, tools, and fishing implements from Minecraft or other mods. Put the implement above and one native flying sword below, define its nature, calibrate the model and aura, confirm, then enter the Spirit Trial. Entry immediately consumes the required levels and the core sword. Each item can be tempered at most twice.

- 自动假玩家试锋和主手属性兜底已移除。唯一数值来源是玩家亲自进入悬空试锋台，对试锋傀儡进行十秒 DPS 测试；首个有效命中开始计时，结束时结果立即写为本源穿刺伤害并自动返程。<br>
  Fake-player probing and main-hand fallback have been removed. The only source is a player-driven ten-second DPS trial on the suspended platform. The first valid hit starts the timer; completion immediately writes intrinsic piercing damage and returns the player.
- 试锋只接受本次武器、由其产生且可追溯的投射物，或单柄试锋飞剑的同一类伤害。切换背包武器、混合近战/投射物/剑阵以及模块持续效果不能叠加刷取数值。试锋平台八角灵灯会轮流降下无伤害雷光。<br>
  The trial accepts one damage channel from the marked ritual weapon, its traceable projectiles, or one trial flying sword. Inventory swaps, mixed melee/projectile/formation output, and module damage cannot stack the result. Eight corner lamps cycle harmless atmospheric lightning.
- “塑形”二级菜单使用实战实体管线预览模型，将轴向、发光、反转、`50%–200%` 模型缩放以及剑罡半径/长度集中在同一页；确认塑形后才可入境。<br>
  The Shaping sub-page uses the combat renderer and combines axis, glow, flip, `50%–200%` model scale, aura radius, and aura length. Entry is locked until shaping is confirmed.
- 完整剑体发光适合普通烘焙模型；“仅外部光效”和“保留原始模型”可兼容复杂动态模型。异常模型会被局部隔离并使用安全占位，不会让整个客户端退出。<br>
  Full-body glow suits ordinary baked models; Aura Only and Original Model are safer for complex animated renderers. A failing model is quarantined locally and replaced by a safe placeholder instead of terminating the client.
- 淬灵物品仍可在飞剑工作台安装和拆卸现有模块，也可用于索敌、持续穿刺、神识御剑与御剑飞行。按默认 `V` 键召出或收回剑阵，且不覆盖原物品的右键能力。<br>
  Tempered items accept the existing reversible modules and work with targeting, relentless piercing, Spirit-Sense control, and sword riding. Press the default `V` key to summon or recall the formation without replacing the item's original right-click action.
- 所有飞剑使用原版剑的 `1.6` 攻击速度并保留普通左键近战。基础穿刺值决定原版攻击属性；锋利、亡灵杀手等原版附魔又会反向作用于实际穿刺伤害，物品提示随附魔、药水、饰品与攻击属性实时更新。入境试锋保留并启用原版/第三方附魔；再次淬灵只令本模组装配核心散失，名称、附魔、耐久、阵型和其他 NBT 均保留。<br>
  Every flying sword uses vanilla sword attack speed (`1.6`) and ordinary melee. Its base pierce value drives the vanilla attack attribute, while Sharpness, Smite and other vanilla enchantments feed back into actual piercing damage. The tooltip responds live to enchantments, potions, accessories and attack attributes. Trials retain and activate vanilla/third-party enchantments; re-tempering disperses only installed Yujian cores while preserving names, enchantments, durability, formation and other NBT.

### 万象御器术式 / Myriad Implement Arts

器物“定性”决定每次展开剑阵时自动采用的术式和盾牌等特殊模型的安全姿态：兵刃默认穿刺、重器默认环斩、远兵默认剑阵、盾器默认护阵、工具默认役器、钓具默认灵钓，未定性器物使用穿刺。之后仍可在 `Ctrl+I` 或用 `Ctrl+J` 切换，服务端管理员也可按物品 ID 设置白名单。术式与 A/B/C 阵型相互独立：前者决定“做什么”，后者决定“停在哪里、如何离阵”。

An implement's nature selects its art whenever the array deploys: blades default to Piercing, heavy weapons to Sweep, ranged weapons to Sword Array, shields to Guard, tools to Toolcraft, rods to Spirit Fishing, and unclassified implements to Piercing. Players can still switch afterwards through `Ctrl+I` or `Ctrl+J`, unless the server applies an item-specific allow-list. Arts and A/B/C formations remain independent.

| 术式 / Art | 行为 / Behaviour |
| --- | --- |
| **穿刺 / Piercing** | 保留经典单次出击、持续穿刺与神识御剑 / Classic sorties, relentless piercing, and Spirit Sword Guidance |
| **环斩 / Circling Sweep** | 六器高速环绕多周，按可见轨迹进行近身范围攻击 / Six implements complete several fast visible circuits for close-range area attacks |
| **剑阵 / Sword Array** | 六器全部就绪后展开鎏金或三华高空阵盘，持续降下环形剑气并以继承当前飞剑外观的巨剑镇落；目标提前死亡也会完成演出 / Requires all six implements, forms a Gilded or Tricolour celestial seal, rains ring-shaped sword-qi and ends with a colossal copy of the current flying sword even after an early kill |
| **万象护阵 / Myriad Guard** | 按来向格挡并反伤，耐久随承受伤害增加，装配核心会传递给攻击者 / Directional interception reflects scaled damage, pays scaled durability, and passes installed core effects to the attacker |
| **役器 / Spirit Toolcraft** | 每次 `G` 派出下一件就绪御器，最多六件并发采掘，速度与掉落遵循原版工具规则 / Every `G` dispatches the next ready implement; up to six jobs follow vanilla tool rules |
| **灵钓 / Spirit Fishing** | 可直接瞄准水面连续派出六件钓具，收获直接返回背包 / Aim at water and dispatch up to six concurrent fishing implements; loot returns to inventory |

`Ctrl+J` 可不打开设置界面直接切换服务端允许的御器术式。补灵台每消耗一枚灵晶，会恢复飞剑实体耐久与耐久核心灵性耐久总和的 25%。

`Ctrl+J` cycles server-permitted arts without opening the settings screen. The Spirit Replenishing Table consumes one Spirit Crystal to restore 25% of combined physical and module durability.

环斩、剑阵、护阵、役器和灵钓的范围、伤害倍率、冷却、目标数量与等待时间保存在世界级 `serverconfig/yujiancraft-techniques-server.toml`，由服务端单独裁决。役器与灵钓会以服务端限频触发已装配核心的非破坏性工作回响；灵钓继承玩家幸运、海之眷顾和饵钓。优化第三人称会提交屏幕中央的方块，但服务端仍复核距离、区块和遮挡。

Ranges, multipliers, cooldowns, target caps, and waiting times live in the world-level `serverconfig/yujiancraft-techniques-server.toml` and remain server-authoritative. Spirit Fishing respects player Luck, Luck of the Sea, and Lure. The shoulder camera submits its actual screen-centre block, while the server still validates reach, chunk state, and obstruction.

每个世界会在首次启动或首次淬灵后创建 `<世界目录>/serverconfig/yujiancraft-wanxiang-weapons.json`。其中每个按需登记的物品 ID 包含：

The per-world server catalogue is created on startup or first tempering at `<world>/serverconfig/yujiancraft-wanxiang-weapons.json`. Each lazily registered item ID contains:

| 字段 / Field | 含义 / Meaning |
| --- | --- |
| `enabled` | 是否允许该登记物品造成伤害和飞行 / Enables combat and flight for the registered item |
| `damageOverride` | `null` 时使用该物品在试锋境重铸的穿刺伤害，否则使用指定基础伤害 / `null` uses per-stack trial damage; a number overrides it |
| `damageMultiplier` | 基础伤害倍率 / Base damage multiplier |
| `flightSpeedMultiplier` | 核心材质速度的倍率 / Multiplier applied to the core material's flight speed |
| `durabilityCost` | 每次有效命中消耗的耐久 / Durability consumed per successful strike |
| `roleOverride` | `auto` 使用物品自身定性，也可按物品 ID 强制器物类型 / `auto` uses per-stack nature; a role name forces the type for that item ID |
| `defaultTechnique` | `auto` 根据器物定性推荐，也可指定登记物品默认术式 / `auto` recommends from nature; an art name sets the item-ID default |
| `allowedTechniques` | 空列表允许全部术式；填写序列化名称可建立服务端白名单 / An empty list allows every art; serialized names form a server allow-list |

修改后由管理员执行 `/yujiancraft reload` 即可热重载。此文件只由服务端读取；客户端本地副本不能改变多人游戏数值。

After editing, an administrator can run `/yujiancraft reload`. The file is read only by the server; a client's local copy cannot alter multiplayer values.

## 飞剑与合成 / Swords & Crafting

模组会在主世界地下生成灵矿与深层灵矿，使用铁镐或更高等级工具开采可获得灵晶；精准采集会保留矿石方块，时运会提高灵晶产量。`0.12.0` 提高了矿脉大小与每区块尝试次数。每位玩家首次进入世界会获得一本可再次合成的《御剑要略》。模组包含木、石、铁、金、钻石和下界合金六种基础飞剑，以及对应的灵铸系列。基础飞剑配方中央为原版剑、边位为对应锻材、四角为灵晶；八枚灵晶环绕基础飞剑可合成灵铸飞剑。

Spirit Ore and Deepslate Spirit Ore generate underground in the Overworld. Mine them with an iron-tier tool or better for Spirit Crystals; Silk Touch preserves the ore and Fortune improves crystal yield. Version `0.12.0` increases vein size and placement attempts. Every player receives a craftable Yujian Guide on first join. Base flying swords place a vanilla sword in the centre, matching materials on the cardinal slots, and Spirit Crystals in all four corners. Eight more Spirit Crystals create the matching Spiritforged sword.

| 飞剑 / Sword | 四周锻材 / Four surrounding materials |
| --- | --- |
| 木质 / Wooden | 橡木木板 ×4 / Oak Planks ×4 |
| 石质 / Stone | 圆石 ×4 / Cobblestone ×4 |
| 铁质 / Iron | 铁锭 ×4 / Iron Ingots ×4 |
| 金质 / Golden | 金锭 ×4 / Gold Ingots ×4 |
| 钻石 / Diamond | 钻石 ×4 / Diamonds ×4 |
| 下界合金 / Netherite | 下界合金碎片 ×4 / Netherite Scraps ×4 |

飞剑工作台：工作台置于中央，四个边位放铁锭，四角放灵晶。基础材料也用于铁砧修复飞剑。

Flying Sword Workbench: place a crafting table in the centre, iron ingots on the four cardinal sides, and Spirit Crystals in the corners. Base materials also repair their matching flying swords in an anvil.

淬灵台：锻造台置于中央，四个边位放灵晶，四角放青金石。御剑要略：书与灵晶无序合成。<br>
Spirit Tempering Table: place a smithing table in the centre, Spirit Crystals on the four cardinal sides, and lapis lazuli in the corners. Craft the Yujian Guide shapelessly from a book and a Spirit Crystal.

## 快速开始 / Quick Start

1. 合成任意基础飞剑，主手持有后按 `V` 展开或收回六柄飞剑。<br>
   Craft any base flying sword, hold it in the main hand, then press `V` to deploy or recall six swords.
2. 默认准心锁定模式下，将准心指向生物并左键锁定。<br>
   In the default Crosshair Lock mode, aim at a creature and left-click to lock it.
3. 按 `Ctrl+R` 切换阵型，按 `Ctrl+J` 切换术式；剑阵术式对准目标按 `H`，役器或灵钓对准方块/水域按 `G`。<br>
   Press `Ctrl+R` to change formation and `Ctrl+J` to cycle arts. Aim and press `H` for Sword Array or `G` for Toolcraft/Spirit Fishing.
4. 六器展开时按 `X` 进入御剑连势，连续左键衔接五段攻击。<br>
   With all six implements deployed, press `X` for Combo Stance and chain five attacks with left click.
5. 在设置中启用御剑飞行后，背包中有飞剑时双击空格起飞或落下。<br>
   Enable Sword Riding in settings, carry a flying sword, and double-tap Space to take off or land.
6. 可选：在淬灵台中融合一个外部模组器物与一柄本模组飞剑，完成试锋后按 `V` 使用万象飞剑。<br>
   Optional: temper a third-party weapon with one native flying sword, then press `V` to command its Myriad formation.

完整默认键位：`V` 展开/收回、`Ctrl+R` 阵型、`Ctrl+I` 设置、`Ctrl+J` 术式、`H` 剑阵、`Ctrl+K` 剑阵形态、`X` 连势、`G` 役器/灵钓。以上均可在 Minecraft 的“选项 → 控制 → 按键绑定”中修改。<br>
Default bindings: `V` deploy/recall, `Ctrl+R` formation, `Ctrl+I` settings, `Ctrl+J` art, `H` Sword Array, `Ctrl+K` array style, `X` Combo Stance, and `G` Toolcraft/Fishing. All are rebindable under Minecraft **Options → Controls → Key Binds**.

## 安装与兼容 / Installation & Compatibility

1. 安装 Minecraft `1.20.1`、Forge `47.4.22` 与 Java `17`。<br>
   Install Minecraft `1.20.1`, Forge `47.4.22`, and Java `17`.
2. 从 [CurseForge](https://www.curseforge.com/minecraft/mc-mods/yujian-craft) 或 [GitHub Releases](https://github.com/forThousands/YujianCraft/releases) 下载 `yujiancraft-<版本>.jar`。<br>
   Download `yujiancraft-<version>.jar` from CurseForge or GitHub Releases.
3. 将 JAR 放入游戏实例的 `mods` 文件夹。客户端和服务器应使用相同版本。<br>
   Place the JAR in the instance's `mods` folder. Clients and servers should use the same version.

本模组不是纯客户端模组。开发预览版可能调整物品数据、配置格式与平衡数值，更新前建议备份世界。`0.9.17` 将内部模组 ID 从 `swordflight` 改为 `yujiancraft`，旧版本物品、方块与配置不会自动迁移。

This is not a client-only mod. Preview releases may change item data, configuration formats, and balance; back up worlds before updating. Version `0.9.17` changed the internal mod ID from `swordflight` to `yujiancraft`, so older items, blocks, and configs do not migrate automatically.

## 设置与多人游戏 / Configuration & Multiplayer

`Ctrl+I` 提供索敌方式、攻击方式、御器术式、优化第三人称、御剑飞行、飞剑光效与亮度档。光影开启时会自动切换为柔和亮度，玩家仍可手动关闭或调整光效。

`Ctrl+I` exposes targeting, attack mode, Yujian art, over-the-shoulder camera, sword riding, visual effects, and brightness levels. Shader detection automatically selects a softer brightness profile, while players remain free to reduce or disable effects.

首次启动会生成 `config/yujiancraft/client-options.json`。将 `showDeveloperOptions` 改为 `true` 后，拥有 OP 2 级权限的玩家可查看开发者选项。伤害、目标、效果、平衡参数与各世界的万象飞剑登记表均只由服务端执行或读取；相机、准心、亮度和本地视听实验属于客户端表现。

On first launch, the mod creates `config/yujiancraft/client-options.json`. Setting `showDeveloperOptions` to `true` exposes developer options to players with OP level 2. Damage, targets, effects, balance, and the per-world Myriad catalogue remain server-authoritative; camera, crosshair, brightness, and local audiovisual experiments are client-side presentation.

> 高亮、闪烁或光影叠加可能造成眼睛不适。光敏性癫痫风险者应关闭相关效果，如有不适立即停止使用。<br>
> Bright flashes and shader combinations may cause discomfort. Players sensitive to flashing imagery should disable these effects and stop immediately if discomfort occurs.

## 反馈、原创边界与协议 / Feedback, Originality & License

Bug 与建议请提交到 [GitHub Issues](https://github.com/forThousands/YujianCraft/issues)，并注明模组、Minecraft、Forge、Java、其他模组和光影版本及复现步骤。上传日志前请移除令牌、服务器地址、个人目录等隐私信息。

Report bugs and suggestions through [GitHub Issues](https://github.com/forThousands/YujianCraft/issues), including mod, Minecraft, Forge, Java, modpack, and shader versions plus reproduction steps. Remove tokens, server addresses, personal paths, and other private information before uploading logs.

本项目仅使用“可操控的悬浮武器”这一通用玩法概念，不采用现有作品的专有名称、角色、剧情、阵法名称、造型或素材。项目代码以 [MIT License](LICENSE) 发布；Minecraft、Minecraft Forge 及第三方组件归各自权利人所有，Forge 相关致谢见 [CREDITS.txt](CREDITS.txt)。

This project uses only the general gameplay idea of controllable floating weapons. It does not use proprietary names, characters, plots, formation names, designs, or assets from existing works. Source code is released under the [MIT License](LICENSE). Minecraft, Minecraft Forge, and third-party components remain the property of their respective owners; Forge acknowledgements are listed in [CREDITS.txt](CREDITS.txt).
