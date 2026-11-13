# BlockState Icons

NeoForge 1.21.1 纯客户端模组：根据物品上的 `minecraft:block_state` 组件，在**物品栏**里给绊线钩和墙显示不同的图标，方便区分用 [Axiom](https://axiom.moulberry.com/) 复制出来、带有固定方块状态的物品。

手持、掉落物、物品展示框等其他场景保持原版模型不变；不带方块状态的普通物品也显示原版图标。

## 效果

### 绊线钩

按 `facing` 显示四种图标：

| facing | 图标 |
|---|---|
| north | 侧视图（钩子朝右） |
| south | 侧视图（钩子朝左） |
| east  | 物品图标（环朝上） |
| west  | 物品图标（环朝下） |

### 墙（所有种类）

仅当 `up=false`（没有柱子）时生效，按 `south`、`north`、`west`、`east` 的顺序取第一个有墙（`low` / `tall`）的方向。图标是从侧面看过去的一段墙，空白处的字母表示「玩家朝哪个方向看」：

| 方向 | 墙段位置 | 字母 | 含义 |
|---|---|---|---|
| south | 左半边 | W | 朝西看，墙在左 |
| north | 右半边 | W | 朝西看，墙在右 |
| west  | 左半边 | N | 朝北看，墙在左 |
| east  | 右半边 | N | 朝北看，墙在右 |

`tall` 占满格子高度，`low` 稍矮一些。墙的材质优先使用 Terra 1912 材质包中的版本。

## 下载与安装

[`dist/blockstate_icons-1.0.0.jar`](dist/) 是模组本体，放进 `mods/` 文件夹即可。模组只有代码，**不含图标贴图**；本仓库也不提供图标资源包（其中包含第三方材质，不便公开分发）。

模组需要配合一个提供图标的资源包才能显示效果。资源包中需要有以下文件（`pack_format` 34）：

```
assets/hookfacing/
├─ models/item/
│  ├─ hook_{north,south,east,west}.json
│  └─ {墙id}_{south,north,west,east}_{low,tall}.json   例：brick_wall_south_tall.json
└─ textures/item/
   └─ （模型引用的贴图）
```

模型就是普通的物品模型，例如：

```json
{ "parent": "minecraft:item/generated", "textures": { "layer0": "hookfacing:item/hook_north" } }
```

缺少某个模型时，对应物品在物品栏中显示原版图标。

- 环境：Minecraft 1.21.1 + NeoForge 21.1.x
- 只需要客户端安装，服务器不用装
- 建议同时使用 Terra 1912 和砖墙修复包，物品栏图标与世界中的方块外观更一致

## 工作原理

模组在模型烘焙完成后，把绊线钩和所有墙的物品模型包一层：

1. 解析物品模型时读取物品的 `block_state` 组件，算出图标模型名（如 `hookfacing:item/hook_north`、`hookfacing:item/brick_wall_south_tall`）。
2. 返回一个只在 `ItemDisplayContext.GUI` 下使用图标模型、其他场景仍使用原版模型的包装模型。

图标模型和贴图位于 `hookfacing` 命名空间（`assets/hookfacing/models/item/`、`assets/hookfacing/textures/item/`），可以通过资源包替换。

代码见 [`BlockStateIcons.java`](blockstate_icons/src/main/java/hookfacing/blockstateicons/BlockStateIcons.java)。

## 从源码编译

需要 JDK 21。

```bash
cd blockstate_icons
./gradlew build
```

产物：`blockstate_icons-1.0.0.jar`（只有代码，图标由资源包提供）。

注意：

- `build.gradle` 把编译输出目录设在了 `~/.gradle-builds/blockstate_icons`，产物在其中的 `libs/` 下，可按需改回默认的 `build/`。

## 许可

代码以 [MIT](LICENSE) 协议发布。仓库和模组 jar 中均不包含第三方材质。

## 相关

Terra 1912（WBC）：

- 官网：[terra1912.com](https://terra1912.com)
- Discord：[discord.gg/cmcVH3GVu9](https://discord.gg/cmcVH3GVu9)
- YouTube：[@WBCBuilds](https://www.youtube.com/@WBCBuilds)
