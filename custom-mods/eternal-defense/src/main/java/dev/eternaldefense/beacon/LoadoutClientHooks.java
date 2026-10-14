package dev.eternaldefense.beacon;

import java.util.function.Consumer;

/** What the client adds to the Loadout Station's block entity tick (nothing on a dedicated server). */
final class LoadoutClientHooks {
    private LoadoutClientHooks(){}
    static Consumer<LoadoutStation.StationEntity> tick=be->{};
    static void tick(LoadoutStation.StationEntity be){tick.accept(be);}
}
