package io.github.troytmoneys.hollowutils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

final class ChatFormat {
    record Identity(Component prefix,Component name) { }
    private ChatFormat() { }
    static String name(String value) {
        if(value==null||!value.matches("[A-Za-z0-9_]{3,16}"))throw new IllegalArgumentException("Choose a Minecraft-style name: 3–16 letters, digits or underscores.");
        return value;
    }
    static Identity identity(String prefix,String name) {
        String value=prefix==null||prefix.isBlank()?"[Player]":prefix.strip();
        return new Identity(LegacyComponentSerializer.legacyAmpersand().deserialize(value.replace('§','&')),Component.text(name,NamedTextColor.WHITE));
    }
    static Component render(Identity identity,Component message) {
        return Component.empty().append(identity.prefix()).append(Component.text(" - ",NamedTextColor.DARK_GRAY))
                .append(identity.name()).append(Component.text(": ",NamedTextColor.GRAY)).append(message.colorIfAbsent(NamedTextColor.WHITE));
    }
}
