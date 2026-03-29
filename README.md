# Gemstone Trainer

A RuneLite plugin that overlays boss models and attack patterns on Gemstone Crabs for PvM training.

## Supported Bosses

### Verzik P2 (Theatre of Blood)
- Full attack cycle: ranged (urn bombs), melee (bounce), and lightning (zap)
- Projectiles and explosion effects
- Configurable sound effects

### Sol Heredit (Colosseum)
- Spear and shield attack patterns with ground slam hazard tiles
- Correct attack sequencing: same style twice triggers the alternate pattern
- Configurable sound effects

## Features

- **Boss selection** via config dropdown (Verzik P2 / Sol Heredit)
- **Hit detection** with "Ouch!" overhead text when standing on hazard tiles
- **Ghostly transparent models** with pale blue recolor
- **Boss faces the player** on each attack
- **Configurable sound IDs** for all attack types
- **Debug overlay** showing attack phase, tick counter, and melee range

## Installation

### Plugin Hub
Search for "Gemstone Trainer" in the RuneLite Plugin Hub.

### Manual / Development
1. Build: `./gradlew clean build`
2. Copy the JAR from `build/libs/` to `~/.runelite/sideloaded-plugins/`
3. Launch RuneLite with `--developer-mode`

## Usage

1. Enable the plugin in RuneLite settings
2. Select a boss type in the config
3. Go to the Gemstone Crab in the Tlati Rainforest
4. The boss overlay will appear and cycle through attacks
5. Practice dodging the attack patterns

## Configuration

| Setting | Description |
|---------|-------------|
| Boss Type | Choose between Verzik P2 and Sol Heredit |
| Enabled | Master toggle |
| Debug Overlay | Show attack phase and tick info |
| Sound IDs | Customize sound effects for each attack (set to -1 to disable) |

## License

BSD 2-Clause License
