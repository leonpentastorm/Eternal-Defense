package dev.eternaldefense.beacon;

import java.util.List;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.client.event.EntityRenderersEvent;

/** The worn geometry of the five Age armor sets, one layer per set and slot. */
public final class ArmorSetLayers {
    public static final List<String> SETS = List.of("frontier", "worldwars", "modern", "advanced", "exotic");

    public static final ModelLayerLocation FRONTIER_HELMET = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/frontier"), "helmet");
    public static final ModelLayerLocation FRONTIER_CHESTPLATE = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/frontier"), "chestplate");
    public static final ModelLayerLocation FRONTIER_LEGGINGS = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/frontier"), "leggings");
    public static final ModelLayerLocation FRONTIER_BOOTS = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/frontier"), "boots");
    public static final ModelLayerLocation WORLDWARS_HELMET = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/worldwars"), "helmet");
    public static final ModelLayerLocation WORLDWARS_CHESTPLATE = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/worldwars"), "chestplate");
    public static final ModelLayerLocation WORLDWARS_LEGGINGS = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/worldwars"), "leggings");
    public static final ModelLayerLocation WORLDWARS_BOOTS = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/worldwars"), "boots");
    public static final ModelLayerLocation MODERN_HELMET = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/modern"), "helmet");
    public static final ModelLayerLocation MODERN_CHESTPLATE = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/modern"), "chestplate");
    public static final ModelLayerLocation MODERN_LEGGINGS = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/modern"), "leggings");
    public static final ModelLayerLocation MODERN_BOOTS = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/modern"), "boots");
    public static final ModelLayerLocation ADVANCED_HELMET = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/advanced"), "helmet");
    public static final ModelLayerLocation ADVANCED_CHESTPLATE = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/advanced"), "chestplate");
    public static final ModelLayerLocation ADVANCED_LEGGINGS = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/advanced"), "leggings");
    public static final ModelLayerLocation ADVANCED_BOOTS = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/advanced"), "boots");
    public static final ModelLayerLocation EXOTIC_HELMET = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/exotic"), "helmet");
    public static final ModelLayerLocation EXOTIC_CHESTPLATE = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/exotic"), "chestplate");
    public static final ModelLayerLocation EXOTIC_LEGGINGS = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/exotic"), "leggings");
    public static final ModelLayerLocation EXOTIC_BOOTS = new ModelLayerLocation(new ResourceLocation("eternal_defense", "armor/exotic"), "boots");

    public static void register(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(FRONTIER_HELMET, ArmorSetLayers::frontierHelmet);
        event.registerLayerDefinition(FRONTIER_CHESTPLATE, ArmorSetLayers::frontierChestplate);
        event.registerLayerDefinition(FRONTIER_LEGGINGS, ArmorSetLayers::frontierLeggings);
        event.registerLayerDefinition(FRONTIER_BOOTS, ArmorSetLayers::frontierBoots);
        event.registerLayerDefinition(WORLDWARS_HELMET, ArmorSetLayers::worldwarsHelmet);
        event.registerLayerDefinition(WORLDWARS_CHESTPLATE, ArmorSetLayers::worldwarsChestplate);
        event.registerLayerDefinition(WORLDWARS_LEGGINGS, ArmorSetLayers::worldwarsLeggings);
        event.registerLayerDefinition(WORLDWARS_BOOTS, ArmorSetLayers::worldwarsBoots);
        event.registerLayerDefinition(MODERN_HELMET, ArmorSetLayers::modernHelmet);
        event.registerLayerDefinition(MODERN_CHESTPLATE, ArmorSetLayers::modernChestplate);
        event.registerLayerDefinition(MODERN_LEGGINGS, ArmorSetLayers::modernLeggings);
        event.registerLayerDefinition(MODERN_BOOTS, ArmorSetLayers::modernBoots);
        event.registerLayerDefinition(ADVANCED_HELMET, ArmorSetLayers::advancedHelmet);
        event.registerLayerDefinition(ADVANCED_CHESTPLATE, ArmorSetLayers::advancedChestplate);
        event.registerLayerDefinition(ADVANCED_LEGGINGS, ArmorSetLayers::advancedLeggings);
        event.registerLayerDefinition(ADVANCED_BOOTS, ArmorSetLayers::advancedBoots);
        event.registerLayerDefinition(EXOTIC_HELMET, ArmorSetLayers::exoticHelmet);
        event.registerLayerDefinition(EXOTIC_CHESTPLATE, ArmorSetLayers::exoticChestplate);
        event.registerLayerDefinition(EXOTIC_LEGGINGS, ArmorSetLayers::exoticLeggings);
        event.registerLayerDefinition(EXOTIC_BOOTS, ArmorSetLayers::exoticBoots);
    }

    public static ModelLayerLocation layer(String set, EquipmentSlot slot) {
        return switch (set) {
            case "frontier" -> switch (slot) { case HEAD -> FRONTIER_HELMET; case CHEST -> FRONTIER_CHESTPLATE; case LEGS -> FRONTIER_LEGGINGS; default -> FRONTIER_BOOTS; };
            case "worldwars" -> switch (slot) { case HEAD -> WORLDWARS_HELMET; case CHEST -> WORLDWARS_CHESTPLATE; case LEGS -> WORLDWARS_LEGGINGS; default -> WORLDWARS_BOOTS; };
            case "modern" -> switch (slot) { case HEAD -> MODERN_HELMET; case CHEST -> MODERN_CHESTPLATE; case LEGS -> MODERN_LEGGINGS; default -> MODERN_BOOTS; };
            case "advanced" -> switch (slot) { case HEAD -> ADVANCED_HELMET; case CHEST -> ADVANCED_CHESTPLATE; case LEGS -> ADVANCED_LEGGINGS; default -> ADVANCED_BOOTS; };
            case "exotic" -> switch (slot) { case HEAD -> EXOTIC_HELMET; case CHEST -> EXOTIC_CHESTPLATE; case LEGS -> EXOTIC_LEGGINGS; default -> EXOTIC_BOOTS; };
            default -> throw new IllegalArgumentException("unknown armor set " + set);
        };
    }

    public static LayerDefinition frontierHelmet() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(32, 17).addBox(-5F, -10F, -5F, 10.0F, 4.0F, 10.0F, new CubeDeformation(0F))
                .texOffs(90, 45).addBox(-4F, -11F, -3F, 8.0F, 1.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(72, 30).addBox(-5F, -7.5F, -5F, 10.0F, 1.0F, 10.0F, new CubeDeformation(0.25F))
                .texOffs(72, 0).addBox(-7F, -6.5F, -7F, 14.0F, 1.0F, 14.0F, new CubeDeformation(-0.25F))
                .texOffs(122, 52).addBox(1F, -8.5F, -6F, 2.0F, 2.0F, 1.0F, new CubeDeformation(-0.25F))
                .texOffs(84, 48).addBox(-6F, -10.5F, 1F, 1.0F, 4.0F, 2.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition frontierChestplate() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5F, -0.5F, -3F, 10.0F, 13.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(24, 19).addBox(-1.5F, 0.5F, -3.5F, 3.0F, 10.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(24, 31).addBox(-4F, 0F, -4F, 2.0F, 1.0F, 8.0F, new CubeDeformation(0F))
                .texOffs(114, 52).addBox(-4F, 1F, -4F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(0, 55).addBox(-4F, 1F, 3F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(8, 55).addBox(-2.5F, 3F, -4F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(16, 55).addBox(-2.5F, 3F, 3F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(24, 55).addBox(-1F, 5F, -4F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(32, 55).addBox(-1F, 5F, 3F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(40, 55).addBox(0.5F, 7F, -4F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(48, 55).addBox(0.5F, 7F, 3F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(56, 55).addBox(1.5F, 9F, -4F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(64, 55).addBox(1.5F, 9F, 3F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(58, 41).addBox(-5F, 10.5F, -3F, 10.0F, 1.0F, 6.0F, new CubeDeformation(0.25F))
                .texOffs(84, 54).addBox(-1F, 10.5F, -4F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-3.5F, -2.5F, -2.5F, 5.0F, 12.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(44, 31).addBox(-4F, -3F, -3F, 4.0F, 3.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(44, 48).addBox(-3.5F, 7.5F, -2.5F, 5.0F, 2.0F, 5.0F, new CubeDeformation(0.25F)), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(52, 0).addBox(-1.5F, -2.5F, -2.5F, 5.0F, 12.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(24, 40).addBox(0F, -3F, -3F, 4.0F, 3.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(64, 48).addBox(-1.5F, 7.5F, -2.5F, 5.0F, 2.0F, 5.0F, new CubeDeformation(0.25F)), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(112, 37).addBox(-3.5F, 0.5F, 2.5F, 6.0F, 7.0F, 1.0F, new CubeDeformation(0F)), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(44, 40).addBox(-2.5F, 0.45F, 2.45F, 6.0F, 7.0F, 1.0F, new CubeDeformation(0F)), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition frontierLeggings() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(72, 15).addBox(-2.5F, -0.5F, -2.5F, 5.0F, 10.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(24, 49).addBox(-2.5F, 0.5F, -3.5F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(112, 15).addBox(-3.5F, 0.5F, -3.5F, 1.0F, 5.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(118, 45).addBox(-4.5F, 2F, -1.5F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0F))
                .texOffs(72, 55).addBox(-4.5F, 1F, -1F, 1.0F, 1.0F, 2.0F, new CubeDeformation(0F)), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(92, 15).addBox(-2.5F, -0.55F, -2.55F, 5.0F, 10.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(34, 49).addBox(-1.5F, 0.45F, -3.55F, 4.0F, 5.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(112, 26).addBox(2.5F, 0.45F, -3.55F, 1.0F, 5.0F, 6.0F, new CubeDeformation(0F)), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition frontierBoots() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 19).addBox(-3F, 7.5F, -3F, 6.0F, 5.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(0, 41).addBox(-3F, 7.5F, -3F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.25F))
                .texOffs(90, 52).addBox(-2.5F, 10.5F, -4F, 5.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(96, 55).addBox(-2.5F, 11.5F, 3F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(120, 55).addBox(-0.5F, 11.25F, 4F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(78, 55).addBox(-1F, 10.75F, 5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0F)), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 30).addBox(-3F, 7.45F, -3.05F, 6.0F, 5.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(0, 48).addBox(-3F, 7.45F, -3.05F, 6.0F, 1.0F, 6.0F, new CubeDeformation(0.25F))
                .texOffs(102, 52).addBox(-2.5F, 10.45F, -4.05F, 5.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(108, 55).addBox(-2.5F, 11.45F, 2.95F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(124, 55).addBox(-0.5F, 11.2F, 3.95F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(90, 55).addBox(-1F, 10.7F, 4.95F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0F)), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition worldwarsHelmet() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(72, 0).addBox(-5F, -9F, -5F, 10.0F, 4.0F, 10.0F, new CubeDeformation(0F))
                .texOffs(24, 30).addBox(-4F, -10F, -4F, 8.0F, 1.0F, 8.0F, new CubeDeformation(0F))
                .texOffs(72, 14).addBox(-6F, -5.5F, -6F, 12.0F, 1.0F, 12.0F, new CubeDeformation(-0.25F))
                .texOffs(40, 46).addBox(-6F, -5F, 5F, 12.0F, 3.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(96, 27).addBox(-6F, -5F, -2F, 1.0F, 3.0F, 7.0F, new CubeDeformation(0F))
                .texOffs(112, 27).addBox(5F, -5F, -2F, 1.0F, 3.0F, 7.0F, new CubeDeformation(0F))
                .texOffs(12, 50).addBox(-6F, -2F, 0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(16, 50).addBox(5F, -2F, 0F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(78, 51).addBox(-5F, -7F, -6F, 10.0F, 1.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(40, 50).addBox(-3F, -7.5F, -7F, 6.0F, 2.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition worldwarsChestplate() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5F, -0.5F, -3F, 10.0F, 13.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(120, 0).addBox(-3.5F, 0F, -3.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(124, 0).addBox(2.5F, 0F, -3.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(120, 11).addBox(-3.5F, 0F, 2.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(124, 11).addBox(2.5F, 0F, 2.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(80, 37).addBox(-5F, 9.5F, -3F, 10.0F, 2.0F, 6.0F, new CubeDeformation(0.25F))
                .texOffs(120, 42).addBox(-4.5F, 9F, -4F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(120, 46).addBox(1.5F, 9F, -4F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(12, 47).addBox(-1F, 9.5F, -4F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(44, 39).addBox(1F, 7.5F, 3F, 3.0F, 4.0F, 2.0F, new CubeDeformation(0F))
                .texOffs(112, 37).addBox(-2.5F, 1F, 3F, 1.0F, 6.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(120, 37).addBox(-3F, 7F, 3F, 3.0F, 4.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(32, 0).addBox(-3.5F, -2.5F, -2.5F, 5.0F, 12.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(66, 46).addBox(-3.5F, -3.5F, -1.5F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0F))
                .texOffs(24, 39).addBox(-3.5F, 8.5F, -2.5F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0.25F))
                .texOffs(20, 45).addBox(-3.5F, 2F, -2.5F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0.25F)), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(52, 0).addBox(-1.5F, -2.5F, -2.5F, 5.0F, 12.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(0, 47).addBox(0.5F, -3.5F, -1.5F, 3.0F, 1.0F, 3.0F, new CubeDeformation(0F))
                .texOffs(0, 41).addBox(-1.5F, 8.5F, -2.5F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0.25F)), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition worldwarsLeggings() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(32, 17).addBox(-3F, 0.5F, -3F, 6.0F, 7.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(80, 45).addBox(-2.5F, -0.5F, -2.5F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(56, 27).addBox(-2.5F, 6F, -2.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0F)), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(0, 19).addBox(-3F, 0.45F, -3.05F, 6.0F, 7.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(100, 45).addBox(-2.5F, -0.55F, -2.55F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(76, 27).addBox(-2.5F, 5.95F, -2.55F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0F)), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition worldwarsBoots() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-3F, 9.5F, -3F, 6.0F, 3.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(54, 50).addBox(-2.5F, 10.5F, -4F, 5.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(0, 51).addBox(-2.5F, 11.5F, 3F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0F)), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(56, 37).addBox(-3F, 9.45F, -3.05F, 6.0F, 3.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(66, 50).addBox(-2.5F, 10.45F, -4.05F, 5.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(20, 51).addBox(-2.5F, 11.45F, 2.95F, 5.0F, 1.0F, 1.0F, new CubeDeformation(0F)), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition modernHelmet() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(70, 15).addBox(-5F, -9F, -5F, 10.0F, 4.0F, 10.0F, new CubeDeformation(0F))
                .texOffs(82, 29).addBox(-4F, -10F, -4F, 8.0F, 1.0F, 8.0F, new CubeDeformation(0F))
                .texOffs(10, 47).addBox(-6F, -6F, 5F, 12.0F, 4.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(82, 38).addBox(-6F, -6F, -2F, 1.0F, 2.0F, 7.0F, new CubeDeformation(0F))
                .texOffs(98, 38).addBox(5F, -6F, -2F, 1.0F, 2.0F, 7.0F, new CubeDeformation(0F))
                .texOffs(82, 51).addBox(-5F, -6F, -6F, 10.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(114, 8).addBox(-7F, -6F, -3F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(114, 15).addBox(6F, -6F, -3F, 1.0F, 1.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(10, 52).addBox(-1F, -7F, -7F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(122, 43).addBox(-1F, -9F, -8F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(36, 47).addBox(-2F, -10F, -10F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0F))
                .texOffs(72, 46).addBox(-6.5F, -4.5F, -1.5F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0F))
                .texOffs(0, 47).addBox(4.5F, -4.5F, -1.5F, 2.0F, 3.0F, 3.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition modernChestplate() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5F, -0.5F, -2.5F, 10.0F, 13.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(30, 17).addBox(-4F, 0F, -4F, 8.0F, 10.0F, 2.0F, new CubeDeformation(0F))
                .texOffs(50, 17).addBox(-4F, 0F, 2F, 8.0F, 10.0F, 2.0F, new CubeDeformation(0F))
                .texOffs(0, 29).addBox(-5.5F, 8F, -3F, 11.0F, 4.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(122, 28).addBox(-3.5F, 6F, -5F, 2.0F, 4.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(48, 47).addBox(-3F, 5F, -5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(122, 33).addBox(-1F, 6F, -5F, 2.0F, 4.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(48, 49).addBox(-0.5F, 5F, -5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(122, 38).addBox(1.5F, 6F, -5F, 2.0F, 4.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(48, 51).addBox(2F, 5F, -5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(106, 50).addBox(-2F, 2F, -5F, 4.0F, 3.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(114, 22).addBox(1F, 4F, 4F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0F))
                .texOffs(122, 22).addBox(2F, -1F, 4.5F, 1.0F, 5.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(114, 0).addBox(-3F, 2F, 4F, 3.0F, 7.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(30, 0).addBox(-3.5F, -2.5F, -2.5F, 5.0F, 12.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(114, 29).addBox(-4.5F, 0F, -1.5F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0F))
                .texOffs(52, 39).addBox(-3.5F, 8.5F, -2.5F, 5.0F, 2.0F, 5.0F, new CubeDeformation(0.25F)), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(50, 0).addBox(-1.5F, -2.5F, -2.5F, 5.0F, 12.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(114, 36).addBox(3.5F, 0F, -1.5F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0F))
                .texOffs(52, 46).addBox(-1.5F, 8.5F, -2.5F, 5.0F, 2.0F, 5.0F, new CubeDeformation(0.25F)), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition modernLeggings() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(70, 0).addBox(-2.5F, -0.5F, -2.5F, 5.0F, 10.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(82, 47).addBox(-2.5F, 4F, -3.5F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(72, 39).addBox(-3.5F, 1F, -1.5F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0F)), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(90, 0).addBox(-2.5F, -0.55F, -2.55F, 5.0F, 10.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(94, 47).addBox(-2.5F, 3.95F, -3.55F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(114, 43).addBox(2.5F, 0.95F, -1.55F, 1.0F, 4.0F, 3.0F, new CubeDeformation(0F)), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition modernBoots() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 39).addBox(-3F, 11.5F, -4F, 6.0F, 1.0F, 7.0F, new CubeDeformation(0F))
                .texOffs(34, 29).addBox(-3F, 7.5F, -3F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(116, 50).addBox(-2.5F, 9.5F, -4F, 5.0F, 2.0F, 1.0F, new CubeDeformation(0F)), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(26, 39).addBox(-3F, 11.45F, -4.05F, 6.0F, 1.0F, 7.0F, new CubeDeformation(0F))
                .texOffs(58, 29).addBox(-3F, 7.45F, -3.05F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(36, 51).addBox(-2.5F, 9.45F, -4.05F, 5.0F, 2.0F, 1.0F, new CubeDeformation(0F)), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition advancedHelmet() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5F, -9.5F, -5F, 10.0F, 9.0F, 10.0F, new CubeDeformation(0F))
                .texOffs(18, 54).addBox(-5F, -6.5F, -6F, 10.0F, 1.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(110, 45).addBox(-4F, -3.5F, -6F, 8.0F, 3.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(46, 45).addBox(-1F, -10.5F, -3F, 2.0F, 1.0F, 7.0F, new CubeDeformation(0F))
                .texOffs(118, 0).addBox(-6F, -7.5F, -2F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0F))
                .texOffs(118, 8).addBox(5F, -7.5F, -2F, 1.0F, 4.0F, 4.0F, new CubeDeformation(0F)), PartPose.ZERO);
        head.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(0, 53).addBox(-4F, -5.5F, -6F, 8.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(74, 55).addBox(-7F, -5.5F, -1F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition advancedChestplate() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(40, 0).addBox(-5F, -0.5F, -3F, 10.0F, 12.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(64, 45).addBox(-4F, 11.5F, -2.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(118, 24).addBox(-2F, 3F, -4F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(72, 0).addBox(-4F, 1F, 3F, 8.0F, 9.0F, 3.0F, new CubeDeformation(0F)), PartPose.ZERO);
        body.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(122, 53).addBox(-1F, 4F, -5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(122, 49).addBox(-3F, 6F, 6F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(72, 51).addBox(1F, 6F, 6F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(94, 0).addBox(-3.5F, 2.5F, -2.5F, 5.0F, 7.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(92, 12).addBox(-5.5F, -3F, -3.5F, 6.0F, 4.0F, 7.0F, new CubeDeformation(0F))
                .texOffs(24, 40).addBox(-5F, 1F, -3F, 5.0F, 2.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(66, 24).addBox(-4F, 5F, -3F, 6.0F, 5.0F, 6.0F, new CubeDeformation(0F)), PartPose.offset(-5F, 2F, 0F));
        rightArm.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(78, 51).addBox(-6.5F, -2F, -1F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0F))
                .texOffs(42, 48).addBox(-5F, 6F, -1F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(72, 12).addBox(-1.5F, 2.5F, -2.5F, 5.0F, 7.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(40, 18).addBox(-0.5F, -3F, -3.5F, 6.0F, 4.0F, 7.0F, new CubeDeformation(0F))
                .texOffs(0, 41).addBox(0F, 1F, -3F, 5.0F, 2.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(26, 29).addBox(-2F, 5F, -3F, 6.0F, 5.0F, 6.0F, new CubeDeformation(0F)), PartPose.offset(5F, 2F, 0F));
        leftArm.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(84, 51).addBox(5.5F, -2F, -1F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0F))
                .texOffs(90, 51).addBox(4F, 6F, -1F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition advancedLeggings() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 30).addBox(-3F, 0.5F, -3F, 6.0F, 5.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(90, 45).addBox(-2.5F, -0.5F, -2.5F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(0, 49).addBox(-2.5F, 4F, -4F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(50, 35).addBox(-2.5F, 4.5F, -2.5F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(118, 16).addBox(-4F, 2F, -1F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0F)), PartPose.offset(-1.9F, 12F, 0F));
        rightLeg.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(94, 51).addBox(-0.5F, 0.5F, -4F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(90, 34).addBox(-3F, 0.45F, -3.05F, 6.0F, 5.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(22, 48).addBox(-2.5F, -0.55F, -2.55F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(110, 49).addBox(-2.5F, 3.95F, -4.05F, 5.0F, 3.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(70, 35).addBox(-2.5F, 4.45F, -2.55F, 5.0F, 5.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(122, 16).addBox(3F, 1.95F, -1.05F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0F)), PartPose.offset(1.9F, 12F, 0F));
        leftLeg.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(98, 51).addBox(-0.5F, 0.45F, -4.05F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition advancedBoots() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(0, 19).addBox(-3F, 8.5F, -4F, 6.0F, 4.0F, 7.0F, new CubeDeformation(0F))
                .texOffs(42, 53).addBox(-2.5F, 10.5F, -5F, 5.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(54, 53).addBox(-1.5F, 9.5F, 3F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0F)), PartPose.offset(-1.9F, 12F, 0F));
        rightLeg.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(62, 55).addBox(-1F, 10.5F, 4F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(12, 49).addBox(-4F, 9.5F, -2F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(92, 23).addBox(-3F, 8.45F, -4.05F, 6.0F, 4.0F, 7.0F, new CubeDeformation(0F))
                .texOffs(102, 53).addBox(-2.5F, 10.45F, -5.05F, 5.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(114, 53).addBox(-1.5F, 9.45F, 2.95F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0F)), PartPose.offset(1.9F, 12F, 0F));
        leftLeg.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(68, 55).addBox(-1F, 10.45F, 3.95F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(64, 51).addBox(3F, 9.45F, -2.05F, 1.0F, 1.0F, 3.0F, new CubeDeformation(0F)), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition exoticHelmet() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-5F, -9.5F, -5F, 10.0F, 9.0F, 10.0F, new CubeDeformation(0F))
                .texOffs(0, 49).addBox(-4F, -5.5F, -6F, 8.0F, 5.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(18, 19).addBox(-5F, -7F, -5F, 10.0F, 1.0F, 10.0F, new CubeDeformation(0.25F))
                .texOffs(114, 8).addBox(-7F, -6.5F, -1.5F, 2.0F, 4.0F, 3.0F, new CubeDeformation(0F))
                .texOffs(114, 15).addBox(5F, -6.5F, -1.5F, 2.0F, 4.0F, 3.0F, new CubeDeformation(0F)), PartPose.ZERO);
        head.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(48, 54).addBox(-3F, -4.5F, -7F, 6.0F, 1.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(18, 49).addBox(-1F, -13.5F, -1F, 2.0F, 4.0F, 2.0F, new CubeDeformation(0F))
                .texOffs(68, 49).addBox(-4F, -11.5F, -1F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0F))
                .texOffs(76, 49).addBox(2F, -11.5F, -1F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0F))
                .texOffs(84, 49).addBox(-1F, -11.5F, 2F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition exoticChestplate() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create()
                .texOffs(40, 0).addBox(-5F, -0.5F, -3F, 10.0F, 13.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(42, 49).addBox(-2F, 3F, -4F, 4.0F, 4.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(44, 42).addBox(-5F, 10.5F, -3F, 10.0F, 1.0F, 6.0F, new CubeDeformation(0.25F))
                .texOffs(76, 42).addBox(-5F, 8.5F, -3F, 10.0F, 1.0F, 6.0F, new CubeDeformation(0.25F))
                .texOffs(0, 19).addBox(-4F, 0F, 3F, 8.0F, 13.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        body.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(96, 53).addBox(-1F, 4F, -5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create()
                .texOffs(72, 0).addBox(-3.5F, -2.5F, -2.5F, 5.0F, 12.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(0, 40).addBox(-4.5F, -3F, -3F, 5.0F, 3.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(18, 30).addBox(-4F, 6F, -3F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0F)), PartPose.offset(-5F, 2F, 0F));
        rightArm.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(52, 49).addBox(-3F, -6F, -1F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0F))
                .texOffs(92, 49).addBox(-5F, 7F, -1F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(92, 0).addBox(-1.5F, -2.5F, -2.5F, 5.0F, 12.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(22, 40).addBox(-0.5F, -3F, -3F, 5.0F, 3.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(42, 30).addBox(-2F, 6F, -3F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0F)), PartPose.offset(5F, 2F, 0F));
        leftArm.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(60, 49).addBox(1F, -6F, -1F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0F))
                .texOffs(98, 49).addBox(4F, 7F, -1F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create(), PartPose.offset(-1.9F, 12F, 0F));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create(), PartPose.offset(1.9F, 12F, 0F));
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition exoticLeggings() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(72, 17).addBox(-2.5F, -0.5F, -2.5F, 5.0F, 10.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(108, 42).addBox(-2.5F, 1.5F, -2.5F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0.25F)), PartPose.offset(-1.9F, 12F, 0F));
        rightLeg.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(114, 0).addBox(-0.5F, 1.5F, -3.5F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(102, 53).addBox(-1F, 4F, -4.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(92, 17).addBox(-2.5F, -0.55F, -2.55F, 5.0F, 10.0F, 5.0F, new CubeDeformation(0F))
                .texOffs(108, 48).addBox(-2.5F, 1.45F, -2.55F, 5.0F, 1.0F, 5.0F, new CubeDeformation(0.25F)), PartPose.offset(1.9F, 12F, 0F));
        leftLeg.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(118, 0).addBox(-0.5F, 1.45F, -3.55F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(42, 54).addBox(-1F, 3.95F, -4.55F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    public static LayerDefinition exoticBoots() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition head = root.addOrReplaceChild("head", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition body = root.addOrReplaceChild("body", CubeListBuilder.create(), PartPose.ZERO);
        PartDefinition rightArm = root.addOrReplaceChild("right_arm", CubeListBuilder.create(), PartPose.offset(-5F, 2F, 0F));
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create(), PartPose.offset(5F, 2F, 0F));
        PartDefinition rightLeg = root.addOrReplaceChild("right_leg", CubeListBuilder.create()
                .texOffs(66, 32).addBox(-3F, 8.5F, -3F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(72, 53).addBox(-2.5F, 10.5F, -4F, 5.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(26, 49).addBox(-4F, 6.5F, -1F, 1.0F, 3.0F, 3.0F, new CubeDeformation(0F)), PartPose.offset(-1.9F, 12F, 0F));
        rightLeg.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(104, 49).addBox(-0.5F, 9.5F, 3F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(90, 32).addBox(-3F, 8.45F, -3.05F, 6.0F, 4.0F, 6.0F, new CubeDeformation(0F))
                .texOffs(84, 53).addBox(-2.5F, 10.45F, -4.05F, 5.0F, 2.0F, 1.0F, new CubeDeformation(0F))
                .texOffs(34, 49).addBox(3F, 6.45F, -1.05F, 1.0F, 3.0F, 3.0F, new CubeDeformation(0F)), PartPose.offset(1.9F, 12F, 0F));
        leftLeg.addOrReplaceChild("glow", CubeListBuilder.create()
                .texOffs(68, 53).addBox(-0.5F, 9.45F, 2.95F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0F)), PartPose.ZERO);
        root.addOrReplaceChild("hat", CubeListBuilder.create(), PartPose.ZERO);
        return LayerDefinition.create(mesh, 128, 64);
    }

    private ArmorSetLayers() {}
}
