package io.github.troytmoneys.hollowutils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

/** Shared styling: user text stays literal, with consistent headings and status colors. */
final class Responses {
    private Responses() { }
    static Component line(String title,String body,NamedTextColor accent) {
        return Component.text("▎ ",accent).append(Component.text(title,accent).decorate(TextDecoration.BOLD))
                .append(Component.text("  ›  ",NamedTextColor.DARK_GRAY)).append(Component.text(body,NamedTextColor.GRAY));
    }
    static void info(CommandSender sender,String title,String body) { sender.sendMessage(line(title,body,NamedTextColor.AQUA)); }
    static void success(CommandSender sender,String title,String body) { sender.sendMessage(line(title,body,NamedTextColor.GREEN)); }
    static void error(CommandSender sender,String body) { sender.sendMessage(line("Unable to complete",body,NamedTextColor.RED)); }
    static void usage(CommandSender sender,String syntax) { sender.sendMessage(line("Usage",syntax,NamedTextColor.YELLOW)); }
}
