package hookfacing.blockstateicons;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.block.WallBlock;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * 读取物品的 minecraft:block_state 组件，在物品栏（GUI）里换成对应的图标模型；
 * 手持、掉落物、物品展示框等其他场景保持原版模型。
 *
 * 图标模型放在 hookfacing:item/ 下（与 26.3 资源包同一套文件）：
 *   绊线钩：hook_{facing}
 *   墙：    {墙id}_{方向}_{low|tall}，仅当 up=false；方向按 south、north、west、east 顺序取第一个有墙的
 */
@Mod(value = BlockStateIcons.MODID, dist = Dist.CLIENT)
public class BlockStateIcons {
    public static final String MODID = "blockstate_icons";
    static final String NS = "hookfacing";
    static final Logger LOGGER = LogUtils.getLogger();
    static final List<String> WALL_DIRS = List.of("south", "north", "west", "east");

    /** 模型名（item/ 之后的部分）→ 已烘焙模型 */
    static final Map<String, BakedModel> ICONS = new HashMap<>();

    public BlockStateIcons(IEventBus modBus) {
        modBus.addListener(BlockStateIcons::registerModels);
        modBus.addListener(BlockStateIcons::wrapModels);
    }

    static ModelResourceLocation standalone(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(NS, "item/" + name));
    }

    /** 把 hookfacing:models/item/*.json 全部注册为额外模型，让游戏去烘焙它们。 */
    private static void registerModels(ModelEvent.RegisterAdditional event) {
        var found = Minecraft.getInstance().getResourceManager().listResources("models/item",
                rl -> rl.getNamespace().equals(NS) && rl.getPath().endsWith(".json"));
        for (ResourceLocation rl : found.keySet()) {
            String path = rl.getPath(); // models/item/xxx.json
            String name = path.substring("models/item/".length(), path.length() - ".json".length());
            event.register(standalone(name));
        }
        LOGGER.info("[{}] registered {} icon models", MODID, found.size());
    }

    /** 给绊线钩和所有墙的物品模型套上包装。 */
    private static void wrapModels(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        ICONS.clear();
        models.forEach((mrl, model) -> {
            ResourceLocation id = mrl.id();
            if (id.getNamespace().equals(NS) && id.getPath().startsWith("item/")
                    && ModelResourceLocation.STANDALONE_VARIANT.equals(mrl.variant())) {
                ICONS.put(id.getPath().substring("item/".length()), model);
            }
        });

        int wrapped = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            boolean target = item == Items.TRIPWIRE_HOOK
                    || (item instanceof BlockItem bi && bi.getBlock() instanceof WallBlock);
            if (!target) continue;
            ModelResourceLocation key = ModelResourceLocation.inventory(BuiltInRegistries.ITEM.getKey(item));
            BakedModel original = models.get(key);
            if (original == null) continue;
            models.put(key, new Wrapped(original));
            wrapped++;
        }
        LOGGER.info("[{}] {} icon models baked, {} item models wrapped", MODID, ICONS.size(), wrapped);
    }

    /** 根据物品的方块状态算出图标模型名；不需要换图标时返回 null。 */
    @Nullable
    static String iconName(ItemStack stack) {
        BlockItemStateProperties state = stack.get(DataComponents.BLOCK_STATE);
        if (state == null || state.isEmpty()) return null;
        Map<String, String> p = state.properties();

        if (stack.is(Items.TRIPWIRE_HOOK)) {
            String facing = p.get("facing");
            return facing == null ? null : "hook_" + facing;
        }
        if (stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof WallBlock) {
            if (!"false".equals(p.get("up"))) return null;
            String wall = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            for (String dir : WALL_DIRS) {
                String h = p.get(dir);
                if ("low".equals(h) || "tall".equals(h)) return wall + "_" + dir + "_" + h;
            }
        }
        return null;
    }

    /** 原版物品模型的包装：解析 overrides 时按方块状态换成「只在 GUI 生效」的图标模型。 */
    static class Wrapped extends BakedModelWrapper<BakedModel> {
        private final ItemOverrides overrides;

        Wrapped(BakedModel original) {
            super(original);
            ItemOverrides originalOverrides = original.getOverrides();
            this.overrides = new ItemOverrides() {
                @Nullable
                @Override
                public BakedModel resolve(BakedModel model, ItemStack stack, @Nullable ClientLevel level,
                                          @Nullable LivingEntity entity, int seed) {
                    BakedModel base = originalOverrides.resolve(original, stack, level, entity, seed);
                    if (base == null) base = original;
                    String name = iconName(stack);
                    BakedModel icon = name == null ? null : ICONS.get(name);
                    return icon == null ? base : new GuiOnly(base, icon);
                }
            };
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }
    }

    /** GUI 里用图标模型，其他场景用原版模型。 */
    static class GuiOnly extends BakedModelWrapper<BakedModel> {
        private final BakedModel icon;

        GuiOnly(BakedModel original, BakedModel icon) {
            super(original);
            this.icon = icon;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
            return context == ItemDisplayContext.GUI
                    ? icon.applyTransform(context, poseStack, leftHand)
                    : originalModel.applyTransform(context, poseStack, leftHand);
        }

        /** 仅 GuiGraphics 渲染物品栏时用来决定平面/立体光照，所以跟随图标模型。 */
        @Override
        public boolean usesBlockLight() {
            return icon.usesBlockLight();
        }
    }
}
