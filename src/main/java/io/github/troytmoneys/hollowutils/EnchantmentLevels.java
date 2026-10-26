package io.github.troytmoneys.hollowutils;

final class EnchantmentLevels {
    private EnchantmentLevels() { }
    static int parse(String input) {
        final int level;
        try { level=Integer.parseInt(input); }
        catch(NumberFormatException e) { throw new IllegalArgumentException("Enchantment level must be an integer from 0 to 255 (0 removes it)."); }
        if(level<0||level>255)throw new IllegalArgumentException("Paper 1.21.11 stores native enchantment levels only up to 255. Levels such as 32768 cannot be applied natively; your held item was not changed.");
        return level;
    }
}
