# Forward Operating Bases, slice 3 (0.0.25): testing

## How it was run

* **Unit tests** (`:arsenal-beacon:test`), JDK 17.
* **FOB GameTests** (`FobGameTests`, batches `fob_01` to `fob_16`, opt-in `-Darsenal.fobTests=true` or `/fob-test`), run on the real client of each edition at the start of `tools/qa/QaFob.java.txt`, on the slice 1 test village (villagers without AI, the online player).
* **Real client** of each edition (Forge 47.4.20 development runtime, `xvfb`, 1280 x 720), `QaFob` on a fresh NORMAL world (seed 20201010, Normal difficulty, creative): slices 1 and 2 as before, then slice 3 in the generated village and at home: an SOS called with `/arsenal fob sos` while the player is at home (chat, the map, the status card), the player going back to the village (the raid starts on arrival), one villager killed during that raid (named and made a level 2 librarian first), the raid won with `/arsenal fob complete`; a second SOS left to run out (`/arsenal fob sos expire`): DOWN, the gate at home red with its screen saying why, no raid at the DOWN FOB, the repair list on the FOB screen, the repair paid with the screen's own button, the villager back.

## Results

### Unit tests: 262 pass, 0 fail

New `SosTest` (8): the SOS comes 10 to 12 hours of play apart (both ends, never outside, the whole range used, evenly spread over 20,000 draws); the clock pauses with no ACTIVE FOB, an open SOS, a Home raid or the Home raid's warning day, and counts ticks of play only; the call picks each ACTIVE FOB equally often and never out of range; 7 days to answer, the timer waiting during a Home raid and the FOB's own raid, arrival starts the raid or is refused (Home raid, raid running, raids off), Bad Omen I and a carried Bad Omen replaced; the state machine PLANTED / ACTIVE / DOWN through the activation raid, the SOS and the repair (a FOB that never won is PLANTED again and its raid called), and what DOWN stops (the shop, tribute, the light red, the inspection paused); the repair is the base plus each villager by level (and only the villagers while ACTIVE), the total the sum for 0 to 20 villagers, both editions' numbers, at most 64 records; lost villagers have stable names, every name used, none twice; the SOS and the lost villagers save and load, an old save has none, an SOS of a FOB that is gone is dropped. `FobTest` and `RaidRoundTest` check protocol 33.

### FOB GameTests: 16 pass, 0 fail, in both editions, in every run

`fob_01` to `fob_12` as in slices 1 and 2, and:

| Test | What it shows |
| --- | --- |
| `fob_13` an SOS won | `/arsenal fob sos` calls the ACTIVE FOB, 7 days to answer; the SOS map handed out; every status card names the FOB; a second SOS refused; nothing happens while nobody is there and the timer runs down by the second; the player in the village starts a vanilla raid at Bad Omen I with the 3 villagers counted, the Bad Omen used up; won: the SOS is over, the FOB ACTIVE, the clock set to 10 to 12 hours, the map burnt |
| `fob_14` an SOS left alone | the route opened; `/arsenal fob sos expire`: DOWN, the SOS over and the clock set again, both gates red; the inspection clock paused while DOWN; no raid at a DOWN FOB; the shop sells nothing and takes nothing; the screen offers the repair at its base price; paid in survival: ACTIVE again; the route as it was (green) and its clock running again |
| `fob_15` lost villagers | a level 3 farmer named Bertha with experience and a custom trade, and two others, killed during the activation raid: DOWN at 1 of 4; three records with name, profession and level (one without a name tag gets a name); the screen lists 3 with the total = base + each; paid: Bertha back as a level 3 farmer with her experience and her trade, full health, the FOB's again; the records used up; it had never won, so PLANTED with its raid running again |
| `fob_16` edge cases | the shovel during an SOS: called off, the clock set again, the map burnt; planted again and ACTIVE; in the Home raid's warning day the SOS clock waits, and runs again after it |

### Real client, final runs (`checks-standalone.txt`, `checks-pack.txt`): 59 pass, 0 fail, each edition

Slices 1 and 2 as before (38 checks), then in the generated village and at home: `/arsenal fob sos` at home calls the FOB, everyone reads it, the SOS map is in the inventory, the status card's first line is the SOS ("SOS North Gate | 6 d 23 h left | go there"); back in the village the raid starts on arrival and everyone reads who answered; a villager (named Ottilie, made a level 2 librarian) killed in that raid is recorded; `/arsenal fob complete` wins it: the SOS is over, the FOB ACTIVE, the clock set again, the map burnt, the chat line; a second SOS left alone: DOWN, the chat line, the gate at home red and its screen saying "The FOB is DOWN: repair it at its FOB Beacon."; no raid at the DOWN FOB; the FOB screen lists Ottilie and the base (pack 1 + 3, standalone 2 + 6); the screen's Repair button: ACTIVE again, Ottilie back as a level 2 librarian, the chat line.

Screenshots (`standalone-f19` to `f23`, two of the pack): the SOS on the status card with the chat line, the SOS map in hand, the gate screen of a DOWN FOB, the repair list, the FOB screen after the repair.

### Found by the real-client runs and fixed

* `checks-pack-first-run.txt` (58 of 59): the status card was checked 5 ticks after the SOS command, before its once-a-second update (harness timing; the screenshot showed it a moment later).
* From the screenshots: "1 villagers back" in the repair's chat line (now "Villagers back: 1").
* Two clients at once ran out of memory twice (exit 137, the machine's limit): the final runs were made one edition after the other.

## Not tested

* Nobody fought an SOS raid by hand: the GameTests and the QA end it with `/arsenal fob complete`; the SOS raid's loss by villager deaths is the slice 1 rule (`fob_02`), and `fob_15` loses the activation raid that way to make the records.
* A real 10 to 12-hour SOS clock or a 7-day answer: the clock's range and pause rule are unit-tested, `fob_16` checks the pause in the Home raid's warning day, and the QA uses `sos` and `sos expire`.
* A Home raid running while a player arrives at an SOS village (`SosRules.arrival`, unit-tested only).
* A villager turned into a zombie villager during an SOS raid; a dedicated server; two players; a player logging in during an SOS (the login handout is the same code as at the call).
