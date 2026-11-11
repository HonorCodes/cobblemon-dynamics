# Art review record

This record holds the approver's verdict on each asset of the first release (CD-ART-10). `py scripts/art.py review`
writes the review sheet `build/art-review/spritesheet.png` and the rows below: one row for each asset, with the SHA-256
of its source. A row whose source changed goes back to `pending`. The approver gives a verdict for each row: `approved`,
or `changes` with a note. `py scripts/art.py check --release` passes only when every row is `approved` at its current
SHA-256. The release workflow runs it for every public tag (CD-ART-12).

<!-- art-review rows: written by scripts/art.py; the verdict, date and note columns are kept -->

| Asset | Source | SHA-256 | Verdict | Date | Note |
| --- | --- | --- | --- | --- | --- |
| models/block/charge_cell | `art/models/block/charge_cell.json` | `0cf5c2f9bf8f19d44511326af8aa044f44f51fd8f6f4e870d3da6e58d1f2ef1c` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| models/block/hay_bed | `art/models/block/hay_bed.json` | `c92e7e1623ecb89889ebe853eccd097ef5a5c320044f32b9c0cef3d28292cca7` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| models/block/plush_bed | `art/models/block/plush_bed.json` | `6f347adc712a41bdb0bf0d5adf2173600ced775a7b5fbe135c1e32a286def3d3` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| models/block/starlit_bed | `art/models/block/starlit_bed.json` | `ff3e5f2d9477e7e3c9d9c7576c1606198aaa99380b8516a448d061a20187858b` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| models/block/template_bed | `art/models/block/template_bed.json` | `12025460cd818f2d48a627bef5941d95f09960da074a35c8de62f2bf5496d0d8` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| models/block/wool_bed | `art/models/block/wool_bed.json` | `2c50ee61e269db763862f899588407e720923ddc70a8c3accd94b5e882e1a1b3` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| models/item/charge_cell | `art/models/item/charge_cell.json` | `fd19145273d585195b415aca9b8d6cebc372f8fd13f1aff7df1c48919c3dcb07` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| models/item/hay_bed | `art/models/item/hay_bed.json` | `a6a8137189f190b83c9c9e12d01924bc4e482793816e2b3e5d83a479533b2fe3` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| models/item/plush_bed | `art/models/item/plush_bed.json` | `aec5771fc8d2072c975debd127585cc9f39a15ae89e3e83cbfd7869965424aea` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| models/item/starlit_bed | `art/models/item/starlit_bed.json` | `06e0affd2ae27a2edc133e38bb3e823fb6905b6c0f1c410f4c5b57416042b6ea` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| models/item/wool_bed | `art/models/item/wool_bed.json` | `ed527bbd630fd466ac4877dcc3f9bfc032edbd664d161b8024a345892c51e0e2` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| models/item/work_leash | `art/models/item/work_leash.json` | `e6cee18aba44ec6ad03bdcc3f71f4a6aa8f0a8afb00444662630796f9ad02882` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/block/charge_cell | `art/textures/block/charge_cell.pixels` | `fb7fa463a06a7c9c266c8c5845e2a2bb15fbce606bfd4fef6c38f68ed38cfcba` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/block/hay_bed_side | `art/textures/block/hay_bed_side.pixels` | `4b90893b01cad3fca4c14b0916a8f849f030a2cd0bc437a08bde56d9c2984146` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/block/hay_bed_top | `art/textures/block/hay_bed_top.pixels` | `8cba0614b227ddd3e83e3b52e8105459fc7bdfb83e5d684815bd4047552c8aac` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/block/plush_bed_side | `art/textures/block/plush_bed_side.pixels` | `40078539c10ba82affa51b69d5899eac86da9c3e6931cebdbdc0bca3d0140770` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/block/plush_bed_top | `art/textures/block/plush_bed_top.pixels` | `e6f061b94602064e65ffabe1a9b13908a57413b79f75aec9d05e7049e6a7f8e7` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/block/starlit_bed_side | `art/textures/block/starlit_bed_side.pixels` | `23c52844526ee105a0069e52c2c4146cd3b6a910135b44193b6abdb8c8da905e` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/block/starlit_bed_top | `art/textures/block/starlit_bed_top.pixels` | `3e0cd93986e6fd2eaa5b2d1058b34baf1938fbb21d1d4365616a8ddee66c164c` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/block/wool_bed_side | `art/textures/block/wool_bed_side.pixels` | `5ef0b30959f87269234640e609d203cf684593cb3e411a7f6c570adf32b6d979` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/block/wool_bed_top | `art/textures/block/wool_bed_top.pixels` | `343ef73b00fc8425dccae229b61057306445cbcbab40d100b6b8ead986f69967` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/sprites/work_mode/disabled | `art/textures/gui/sprites/work_mode/disabled.pixels` | `4e4b21cef35ebf1c6091d7db5abdee631d0c4627f20c6a2a25bd114d43a09c2f` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/sprites/work_mode/disabled.mcmeta | `art/textures/gui/sprites/work_mode/disabled.pixels.mcmeta` | `d143fef018955c0412cfcdee4ff6666a90c9f7192263c781e0368d858edf3f19` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/sprites/work_mode/hover | `art/textures/gui/sprites/work_mode/hover.pixels` | `ee695638c95f309c393562c293267d1a8f20c15dcbc90168bc5a7be5ab9e470e` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/sprites/work_mode/hover.mcmeta | `art/textures/gui/sprites/work_mode/hover.pixels.mcmeta` | `d143fef018955c0412cfcdee4ff6666a90c9f7192263c781e0368d858edf3f19` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/sprites/work_mode/off | `art/textures/gui/sprites/work_mode/off.pixels` | `792cdc6f9af5067348537114958275ddec8f1026ec764eacd0bac2f4b42d9109` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/sprites/work_mode/off.mcmeta | `art/textures/gui/sprites/work_mode/off.pixels.mcmeta` | `d143fef018955c0412cfcdee4ff6666a90c9f7192263c781e0368d858edf3f19` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/sprites/work_mode/on | `art/textures/gui/sprites/work_mode/on.pixels` | `b091c74ca6fedbb04637931ecac75a71a630ca1ad23d097451e1ef0ba63a4c84` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/sprites/work_mode/on.mcmeta | `art/textures/gui/sprites/work_mode/on.pixels.mcmeta` | `d143fef018955c0412cfcdee4ff6666a90c9f7192263c781e0368d858edf3f19` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/stamina_bar/head_bar | `art/textures/gui/stamina_bar/head_bar.pixels` | `d4fff6297a030d30dc4c92f5d1e97e08fed838e5f4793df66a786cbcb57d393a` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/stamina_bar/head_fill | `art/textures/gui/stamina_bar/head_fill.pixels` | `00ede1e5f01b8d003d7e297babeb7b575ab328073f9ddfcf750a194a6b30318b` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/stamina_bar/panel_bar | `art/textures/gui/stamina_bar/panel_bar.pixels` | `ee8b65e09ac4b74e58de201baf195b00725810ab89b8c6ccd40a36c2e02e4041` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/stamina_bar/panel_fill | `art/textures/gui/stamina_bar/panel_fill.pixels` | `bdd0c6569e80b617c345d973314b67956dd0359daba89f654e456e5fef71137a` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/state/healing | `art/textures/gui/state/healing.pixels` | `85f5e88033134eb2835122029d2781d2999ca4f3c76d14e49664de5268420305` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/state/idle | `art/textures/gui/state/idle.pixels` | `e2ad24bbd53678112af37a37f1b9c7b770ea99bda137da8f19dbdf6e0d4548ed` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/state/sleeping | `art/textures/gui/state/sleeping.pixels` | `92034d0b0dd973bb789538a88c3273599a0542cefc5cde84e9ce5b4df21719fe` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/state/standby | `art/textures/gui/state/standby.pixels` | `b3898bb55dc17ec5b81264fb2a605d992e289698e711065aeaa664ab330798d2` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/state/tired | `art/textures/gui/state/tired.pixels` | `3ce6d863773cf2c93dea3b1d180113b26a08d5beaa2d23bb9fbe240ae34a5840` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/state/traveling | `art/textures/gui/state/traveling.pixels` | `6954892fb07a33bf7d221419959337ece9c5660346d75f1321fd457e9d9597b4` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/gui/state/working | `art/textures/gui/state/working.pixels` | `70c447f804945e3f23ef0e88ad22552b2d4379254d7d5b02d0721e7365c2ce9f` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| textures/item/work_leash | `art/textures/item/work_leash.pixels` | `e30a61f1b631620cd72bb99951d4a0370e22925213a23814845f81c536931217` | approved | 2026-11-11 | The approver: "all approved on the spritesheet". |
| logo/logo-256 | `art/logo/logo-256.png` | `313191dad27841882de547803012154d0e7edbafa1511465b2af4e7a476e1802` | approved | 2026-11-11 | The approver: "yes", when asked to approve the logo. |
| logo/icon-512 | `art/logo/icon-512.png` | `b1f36c30e201e69a0e7a51ed121890bda793ece5f115ff55e87526bdea6e6007` | approved | 2026-11-11 | The approver: "yes", when asked to approve the logo. |

<!-- art-review rows end -->
