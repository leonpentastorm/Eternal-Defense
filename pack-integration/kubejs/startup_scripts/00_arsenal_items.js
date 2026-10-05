// Create Arsenal owns these items. Restart after changing registrations.
StartupEvents.registry('item', event => {
  event.create("pressed_receiver").displayName("Pressed Receiver").texture('arsenal_beacon:item/industrial_atlas').parentModel("arsenal_beacon:item/pressed_receiver");
  event.create("brass_receiver").displayName("Brass Receiver").texture('arsenal_beacon:item/industrial_atlas').parentModel("arsenal_beacon:item/brass_receiver");
  event.create("hardened_receiver").displayName("Hardened Receiver").texture('arsenal_beacon:item/industrial_atlas').parentModel("arsenal_beacon:item/hardened_receiver");
  event.create("exotic_receiver").displayName("Exotic Receiver").texture('arsenal_beacon:item/industrial_atlas').parentModel("arsenal_beacon:item/exotic_receiver");
  event.create("incomplete_brass_receiver").displayName("Unfinished Brass Receiver").texture('arsenal_beacon:item/industrial_atlas').parentModel("arsenal_beacon:item/incomplete_brass_receiver");
  event.create("incomplete_hardened_receiver").displayName("Unfinished Hardened Receiver").texture('arsenal_beacon:item/industrial_atlas').parentModel("arsenal_beacon:item/incomplete_hardened_receiver");
  event.create("incomplete_exotic_receiver").displayName("Unfinished Exotic Receiver").texture('arsenal_beacon:item/industrial_atlas').parentModel("arsenal_beacon:item/incomplete_exotic_receiver");
  event.create("hardened_alloy").displayName("Hardened Arsenal Alloy").texture('arsenal_beacon:item/industrial_atlas').parentModel("arsenal_beacon:item/hardened_alloy");
  event.create("cartridge_case").displayName("Cartridge Case").texture('arsenal_beacon:item/industrial_atlas').parentModel("arsenal_beacon:item/cartridge_case");
  event.create("propellant").displayName("Factory Propellant").texture('arsenal_beacon:item/industrial_atlas').parentModel("arsenal_beacon:item/propellant");
});
