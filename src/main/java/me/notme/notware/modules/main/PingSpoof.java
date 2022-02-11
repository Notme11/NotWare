package me.notme.notware.modules.main;

import me.notme.notware.NotWare;
import me.notme.notware.utils.misc.SystemTimer;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.c2s.play.KeepAliveC2SPacket;

public class PingSpoof extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> ping = sgGeneral.add(new IntSetting.Builder()
            .name("ping")
            .description("The Ping to set.")
            .defaultValue(200)
            .min(0)
            .sliderMin(0)
            .sliderMax(1000)
            .build()
    );

    private final Setting<Boolean> negative = sgGeneral.add(new BoolSetting.Builder()
            .name("negative")
            .description("Makes you ping go down.")
            .defaultValue(false)
            .build()
    );

    public PingSpoof() {
        super(NotWare.nwmisc, "ping-spoof", "pov cant buy better internet");
    }

    private SystemTimer timer;
    private KeepAliveC2SPacket packet;

    @Override
    public void onActivate() {
        timer = new SystemTimer();
    }

    @EventHandler
    public void onPacketSend(PacketEvent.Send event) {
        if (event.packet instanceof KeepAliveC2SPacket && packet != event.packet && ping.get() != 0) {
            packet = (KeepAliveC2SPacket) event.packet;
            event.cancel();
            timer.reset();
        }
    }

    @EventHandler
    public void onUpdate(Render3DEvent event) {
        if (timer.hasPassed(negative.get() ? -ping.get() : ping.get()) && packet != null) {
            mc.getNetworkHandler().sendPacket(packet);
            packet = null;
        }
    }

    @Override
    public String getInfoString() {
        return ping.get() + "ms";
    }
}
