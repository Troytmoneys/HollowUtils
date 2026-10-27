package io.github.troytmoneys.hollowutils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatFormatTest {
    @Test void usesLuckPermsPrefixAndChosenNameWithTheRequestedSeparatorsWithoutParsingMessageMarkup() {
        var identity=ChatFormat.identity("&c[Admin] ","Alias");
        var text=ChatFormat.render(identity,Component.text("<click:run_command:'/op me'>Hello"));
        assertEquals("[Admin] - Alias: <click:run_command:'/op me'>Hello",PlainTextComponentSerializer.plainText().serialize(text));
        assertEquals(net.kyori.adventure.text.format.NamedTextColor.RED,identity.prefix().color());
    }
    @Test void emptyPrefixHasAReadableFallbackAndDisguiseNamesRejectFormattingOrInjection() {
        assertEquals("[Player] - Alex: Hi",PlainTextComponentSerializer.plainText().serialize(ChatFormat.render(ChatFormat.identity(null,"Alex"),Component.text("Hi"))));
        assertEquals("Alex_123",ChatFormat.name("Alex_123"));
        for(String name:new String[]{"&cAdmin","Two Names","a","a".repeat(17),"<red>Admin"})assertThrows(IllegalArgumentException.class,()->ChatFormat.name(name));
    }
}
