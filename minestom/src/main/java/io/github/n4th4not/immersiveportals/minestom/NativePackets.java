package io.github.n4th4not.immersiveportals.minestom;

import net.minestom.server.MinecraftServer;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.PacketVanilla;
import net.minestom.server.network.packet.server.ServerPacket;

final class NativePackets {
    private NativePackets() {}

    static byte[] encode(ServerPacket packet) {
        var info = PacketVanilla.SERVER_PACKET_PARSER.play().packetInfo(packet.getClass());
        return NetworkBuffer.makeArray(
                buffer -> {
                    buffer.write(NetworkBuffer.VAR_INT, info.id());
                    info.serializer().write(buffer, (ServerPacket.Play) packet);
                },
                MinecraftServer.getRegistries());
    }
}
