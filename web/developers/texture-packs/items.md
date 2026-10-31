# Editing item models

Allows you to replace the models of items in a couple of ways.

## By SkyBlock ID

Allows you to entirely replace the item model of a SkyBlock item based on its SkyBlock ID. To do this, you can place a model at `firmskyblock:<itemid>`{model=item}.

On older versions (before 1.21) Minecraft had system for specifying custom predicates inside of models:

```json
{
    "parent": "minecraft:item/handheld",
    "textures": {
        "layer0": "firmskyblock:item/bat_wand"
    },
    "overrides": [
        {
            "predicate": {
                "apex:display_name": {
                    "regex": ".*§d.*",
                    "color": "preserve"
                }
            },
            "model": "firmskyblock:item/recombobulated_bat_wand"
        }
    ]
}
```

Nowadays, these types of `overrides` / `predicate` combos are instead seperated out into a client item. The client item is located at `<namespace>:items/<itemid>.json`{fqfi}, which then references the actual rendered item model in its definition. Supports exclusively for the `firmskyblock` namespace an automatic conversion from the old format to the new format. Users are nevertheless encouraged to instead of just creating a renderable item model (at `firmskyblock:<skyblockid>`{model=item}) to also create a client item definition (at `firmskyblock:items/<skyblockid>.json`{fqfi}).

That item definition would then reference the actual model file (at any location you want, but i for now have just chosen to reference the default legacy location):

```json{4}
{
    "model": {
        "type": "minecraft:model",
        "model": "firmskyblock:item/<skyblockid>"
    }
}
```

Note that the inner `model` refers to any model, so you need to explicitly specify the `item/` prefix, which is implicit in the legacy item model system.
