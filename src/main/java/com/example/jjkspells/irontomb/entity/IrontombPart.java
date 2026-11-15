package com.example.jjkspells.irontomb.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.entity.PartEntity;

/** One invisible hit box of Irontomb's body (head, shoulder, a slice of an arm...). Damage dealt to it goes to the boss. */
public class IrontombPart extends PartEntity<IrontombEntity> {
    private final EntityDimensions size;
    public final float ox, oy, oz;

    public IrontombPart(IrontombEntity parent, float ox, float oy, float oz, float width, float height) {
        super(parent);
        this.ox = ox;
        this.oy = oy;
        this.oz = oz;
        this.size = EntityDimensions.scalable(width, height);
        this.refreshDimensions();
    }

    @Override protected void defineSynchedData() { }
    @Override protected void readAdditionalSaveData(CompoundTag t) { }
    @Override protected void addAdditionalSaveData(CompoundTag t) { }
    @Override public boolean isPickable() { return getParent().isAlive(); }
    @Override public boolean canBeCollidedWith() { return false; }
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean is(Entity e) { return this == e || getParent() == e; }
    @Override public EntityDimensions getDimensions(Pose p) { return size; }

    @Override
    public boolean hurt(DamageSource s, float amt) {
        return !isInvulnerableTo(s) && getParent().hurt(s, amt);
    }
}
