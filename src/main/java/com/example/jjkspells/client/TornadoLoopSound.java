package com.example.jjkspells.client;

import com.example.jjkspells.entity.StormTornadoEntity;
import com.example.jjkspells.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Howling wind that follows one tornado. */
public class TornadoLoopSound extends AbstractTickableSoundInstance {
    private final StormTornadoEntity tornado;

    public TornadoLoopSound(StormTornadoEntity tornado) {
        super(ModSounds.STORM_LOOP.get(), SoundSource.PLAYERS, RandomSource.create());
        this.tornado = tornado;
        this.looping = true;
        this.delay = 0;
        this.volume = 0.01f;
        this.x = tornado.getX();
        this.y = tornado.getY() + 6;
        this.z = tornado.getZ();
    }

    @Override
    public void tick() {
        if (tornado.isRemoved() || !tornado.isAlive()) {
            this.stop();
            return;
        }
        this.x = tornado.getX();
        this.y = tornado.getY() + 6;
        this.z = tornado.getZ();
        int life = tornado.getLifetime();
        float fade = Math.min(1f, tornado.tickCount / 24f) * Math.min(1f, (life - tornado.tickCount) / 24f);
        this.volume = Math.max(0.01f, 3.5f * fade);
    }
}
