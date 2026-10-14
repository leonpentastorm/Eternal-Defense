// Eternal Defense owns these items.
StartupEvents.registry('item', event => {
  event.create("pressed_receiver").displayName("Pressed Receiver").texture('eternal_defense:item/industrial_atlas').parentModel("eternal_defense:item/pressed_receiver");
  event.create("brass_receiver").displayName("Brass Receiver").texture('eternal_defense:item/industrial_atlas').parentModel("eternal_defense:item/brass_receiver");
  event.create("hardened_receiver").displayName("Hardened Receiver").texture('eternal_defense:item/industrial_atlas').parentModel("eternal_defense:item/hardened_receiver");
  event.create("exotic_receiver").displayName("Exotic Receiver").texture('eternal_defense:item/industrial_atlas').parentModel("eternal_defense:item/exotic_receiver");
  event.create("incomplete_brass_receiver").displayName("Unfinished Brass Receiver").texture('eternal_defense:item/industrial_atlas').parentModel("eternal_defense:item/incomplete_brass_receiver");
  event.create("incomplete_hardened_receiver").displayName("Unfinished Hardened Receiver").texture('eternal_defense:item/industrial_atlas').parentModel("eternal_defense:item/incomplete_hardened_receiver");
  event.create("incomplete_exotic_receiver").displayName("Unfinished Exotic Receiver").texture('eternal_defense:item/industrial_atlas').parentModel("eternal_defense:item/incomplete_exotic_receiver");
  event.create("hardened_alloy").displayName("Hardened Alloy").texture('eternal_defense:item/industrial_atlas').parentModel("eternal_defense:item/hardened_alloy");
  event.create("cartridge_case").displayName("Cartridge Case").texture('eternal_defense:item/industrial_atlas').parentModel("eternal_defense:item/cartridge_case");
  event.create("propellant").displayName("Factory Propellant").texture('eternal_defense:item/industrial_atlas').parentModel("eternal_defense:item/propellant");
});
