package io.github.troytmoneys.hollowutils;

/** Plans a grant without touching the player's items; overflow rejects the entire grant. */
final class ItemAllocation {
    static final int MAX_AMOUNT=32768;
    private ItemAllocation() { }
    static int amount(String input) {
        final int value;
        try { value=Integer.parseInt(input); } catch(NumberFormatException e) { throw new IllegalArgumentException("Amount must be an integer from 1 to "+MAX_AMOUNT+"."); }
        if(value<1||value>MAX_AMOUNT)throw new IllegalArgumentException("Amount must be an integer from 1 to "+MAX_AMOUNT+".");
        return value;
    }
    static int[] plan(int[] counts,boolean[] compatible,int maxStack,int amount) {
        if(counts.length!=compatible.length||maxStack<1||amount<1||amount>MAX_AMOUNT)throw new IllegalArgumentException("Invalid item grant.");
        int[] add=new int[counts.length];int remaining=amount;
        for(int count:counts)if(count<0)throw new IllegalArgumentException("Invalid inventory count.");
        for(int i=0;i<counts.length&&remaining>0;i++)if(counts[i]>0&&compatible[i]) {
            add[i]=Math.min(remaining,Math.max(0,maxStack-counts[i]));remaining-=add[i];
        }
        for(int i=0;i<counts.length&&remaining>0;i++)if(counts[i]==0) {
            add[i]=Math.min(remaining,maxStack);remaining-=add[i];
        }
        if(remaining>0)throw new IllegalArgumentException("Not enough inventory space. Nothing was given; clear space or request fewer items.");
        return add;
    }
}
