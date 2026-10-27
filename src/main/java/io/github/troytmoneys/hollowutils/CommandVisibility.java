package io.github.troytmoneys.hollowutils;

import java.util.*;
import java.util.function.Predicate;

final class CommandVisibility {
    private CommandVisibility() { }
    static String label(String value) {
        return (value.startsWith("/")?value.substring(1):value).toLowerCase(Locale.ROOT);
    }
    static boolean visible(String label,Predicate<String> owned,Predicate<String> permitted) {
        String root=label(label);
        return !root.contains(":")&&(!owned.test(root)||permitted.test(root));
    }
    static String root(String buffer) {
        String value=label(buffer);int end=0;
        while(end<value.length()&&!Character.isWhitespace(value.charAt(end)))end++;
        return value.substring(0,end);
    }
    static boolean rootCompletion(String buffer) {
        return label(buffer).chars().noneMatch(Character::isWhitespace);
    }
    static boolean completionAllowed(String buffer,Set<String> allowed) {
        String root=root(buffer);
        if(root.contains(":"))return false;
        return rootCompletion(buffer)?allowed.stream().anyMatch(label->label.startsWith(root)):allowed.contains(root);
    }
}
