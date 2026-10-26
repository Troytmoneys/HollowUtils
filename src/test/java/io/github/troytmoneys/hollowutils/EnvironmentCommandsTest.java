package io.github.troytmoneys.hollowutils;

import org.bukkit.World;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EnvironmentCommandsTest {
    private static final class Sky {
        boolean rain,thunder,cancelThunder;int clear,duration,thunderDuration;final World world;
        Sky(World.Environment environment) {
            world=(World)Proxy.newProxyInstance(World.class.getClassLoader(),new Class<?>[]{World.class},(proxy,method,args)->switch(method.getName()) {
                case "getEnvironment" -> environment;
                // Match CraftWorld: both weather setters reset the clear-weather timer.
                case "setStorm" -> { rain=(boolean)args[0];clear=0;duration=0;yield null; }
                case "setThundering" -> { if(!cancelThunder)thunder=(boolean)args[0];clear=0;thunderDuration=0;yield null; }
                case "setClearWeatherDuration" -> { clear=(int)args[0];yield null; }
                case "setWeatherDuration" -> { duration=(int)args[0];yield null; }
                case "setThunderDuration" -> { thunderDuration=(int)args[0];yield null; }
                case "hasStorm" -> rain;
                case "isThundering" -> thunder;
                default -> throw new UnsupportedOperationException(method.getName());
            });
        }
    }
    @Test void sunClearsBothStormsAndThunderAndKeepsTheFullClearTimer() {
        var sky=new Sky(World.Environment.NORMAL);sky.rain=true;sky.thunder=true;
        EnvironmentCommands.weather(List.of(sky.world),"sun");
        assertFalse(sky.rain);assertFalse(sky.thunder);assertEquals(6000,sky.clear);assertEquals(6000,sky.duration);
    }
    @Test void rainAndThunderSetCompleteWeatherStatesWithoutAffectingNetherOrEnd() {
        var sky=new Sky(World.Environment.NORMAL);var nether=new Sky(World.Environment.NETHER);var end=new Sky(World.Environment.THE_END);
        EnvironmentCommands.weather(List.of(sky.world,nether.world,end.world),"thunder");
        assertTrue(sky.rain);assertTrue(sky.thunder);assertEquals(0,sky.clear);assertFalse(nether.rain);assertFalse(end.rain);
        EnvironmentCommands.weather(List.of(sky.world),"rain");assertTrue(sky.rain);assertFalse(sky.thunder);
    }
    @Test void cancelledWeatherIsReportedAsFailureAndMissingOverworldIsRejected() {
        var sky=new Sky(World.Environment.NORMAL);sky.cancelThunder=true;
        assertThrows(IllegalArgumentException.class,()->EnvironmentCommands.weather(List.of(sky.world),"thunder"));
        assertThrows(IllegalArgumentException.class,()->EnvironmentCommands.weather(List.of(),"sun"));
    }
}
