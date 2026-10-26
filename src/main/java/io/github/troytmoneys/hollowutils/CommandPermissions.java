package io.github.troytmoneys.hollowutils;

/** One canonical permission per command family; aliases cannot become permission bypasses. */
final class CommandPermissions {
    private CommandPermissions() { }
    static String node(String command) {
        String canonical=switch(command) {
            case "gmc","gms","gmsp","gma","gm" -> "gamemode";
            case "bc" -> "broadcast";
            case "ci","clearinventory" -> "clear";
            case "pm","message","whisper","privatemanager" -> "msg";
            default -> command;
        };
        return "hollowutils."+canonical;
    }
}
