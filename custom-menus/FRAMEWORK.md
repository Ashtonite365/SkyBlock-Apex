# Custom Menus framework (notes)

Code: `src/main/kotlin/features/menus/` (MenuRegistry, MenuHost, MenuSettings, MenuClicks, MenuOptions).

## Hosting and the vanilla fallback
- Register a `CustomMenu`; leave `selfHosted = false` and return a view from `create(screen)`.
  `MenuRegistry` attaches it. Only the bazaar is self-hosted.
- There is ONE vanilla fallback: the framework's. `create` returning null or throwing, or any view call
  throwing, shows the vanilla chest for that session. A view that cannot read its page calls
  `session.fallback(reason)` (it receives the session through `MenuView.attached`), normally after
  `ContainerGate.GRACE_MS`. Do not add per-menu fallback timers.

## Clicks
- Every container click goes through `MenuClicks.click` / `clickLater` (packet button 0 = left, 1 = right;
  26.3 `InputConstants.MOUSE_BUTTON_*` are 1-based input codes and must never go into the packet).
  Automated clicks use `clickLater` (random 0-50 ms `MenuClickDelay`).
- Consume rule (Ashton, 25 Oct 2026): a press inside an Apex menu belongs to the menu ONLY. `MenuSession.install`
  routes press, release, drag and wheel through `MenuInputRouter` (the consuming base; never register your own
  `ScreenMouseEvents` on a menu screen). A press in the view starts an Apex gesture (`MenuInputGuard`): its drags
  never reach any screen, its release is consumed even on the page the press opened, and while it is held vanilla's
  `slotClicked` is refused for every slot. With an Apex menu in front, vanilla may only click REAL cells
  (`MenuFront.isRealSlot`: panel inventory slots, registered real chest cells), so double-click "pick up all",
  shift quick-move, number-key swaps and other mods' slot clicks never hit a chest slot behind the menu. The only
  click a tile sends is its own `MenuClicks` / `SilentClick` click. Refusals log `[ApexMenu] blocked background click`.

## Auto-click / chained-click options
- Declare every auto-click or chained-click option of a custom menu with `MenuOptions.autoClick(key)`
  (`import dev.ashtonite365.apex.features.menus.MenuOptions.autoClick`, then `val x by autoClick("key")`).
- Inside Custom Menus these default to **ON**. They keep the red ⚠ label, the ban-risk description, their own
  per-option toggle and the one-time risk disclaimer (asked when one is switched back on by hand).
- Do not write "⚠" in the translation label: the framework adds it.
- Outside Custom Menus, rule-breaking features use `riskyToggle` and stay **OFF** by default.
- Current options: `custom-menus.automation` (also gates MenuChain auto steps),
  `custom-menu-bank.custom-amount`, `custom-menu-bank.return-to-main`, `custom-menu-museum.chain-tabs`. `MenuOptionsDefaultsTest` checks them. (The bazaar has no auto-click option: it is permanently click-for-click, one real click per Apex click; the old `bz-menu.in-apex-orders` key is dropped on load.)
- Saved configs keep the user's saved value; only new installs or unset keys get the new default.
- The old `custom-menus.silent-auto-click` option was removed (25 Oct 2026): every MenuChain keeps the Apex screen
  in front and Apex button clicks are packet-only, always. `AutoClickMigration` v2 drops the saved key.
- Click sounds: one user press in an Apex menu = exactly one click sound (`MenuPress`); Hypixel's click sounds
  caused by that press (every step of a chain it starts) are muted. Wrap every new menu entry point in `MenuPress.press {}`.
- MenuChain steps take the PACKET button (0 = left, 1 = right); anything else is rejected.
- Exception (Ashton): `custom-menu-bank.chain-presets` (one-press deposit/withdraw) is a plain default-ON
  `toggle`: no ⚠, no disclaimer flag, not declared through `autoClick`. Its chain still needs
  `custom-menus.automation`. `custom-menu-bank.presets` (show the preset buttons) is a plain default-ON toggle too.

## Section master switches
- Central: `MenuSections` (feat-section-master-switch), one "<Section> Apex Menus" toggle per Custom Menus group
  (`custom-menu-sections.<group>`); a menu's section is the group of its settings holder. `CustomMenu.enabled` defaults to
  `MenuSections.allows(this) && settings.apex`; `MenuRegistry.isOn` applies the section switch even to menus that
  override `enabled`. OFF = vanilla; each menu's own Apex/Normal mode still works under an ON section.
- The earlier per-menu stand-ins ("Anvil / Brewing Stand Apex Menus", "City Projects / Community Shop Apex Menus") are
  removed; a saved OFF is kept as that menu's mode = NORMAL (`LegacyMenuSwitches`, via `MenuSettings.migrate`).
- `MenuView.mouseReleased` (optional, default no-op): the release still never reaches the chest; the workbench menus use
  it for press-and-release item drags.
- No-navigation fallback (now every menu, fix-no-autoback-on-failure; `ApexFallback.navigates` = false): never Go-Back-click, close or
  navigate when they fail to read/render; the chest stays open (vanilla) and the failure is logged.
