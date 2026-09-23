# ThermalADD

🇷🇺 **Русский** | 🇬🇧 [English](#english)

Аддон для [Thermal Expansion 4](https://www.curseforge.com/minecraft/mc-mods/thermal-expansion)
под Minecraft **1.7.10** (Forge). Добавляет улучшенные, многолинейные версии машин TE4 тира
`UltimateResonant`, работающие на настоящих списках рецептов Thermal Expansion, хранилища
сверхбольшой ёмкости, набор для улучшения уже стоящих машин TE и пару собственных
«запредельных» расширений.

## Машины

Все машины обрабатывают по несколько линий параллельно из общего буфера RF, имеют 9 слотов
расширений и собственный энергетический тир.

- **Сингулярный измельчитель** — 3 линии, первичный и вторичный выход.
- **Сингулярная красная печь** — 3 линии.
- **Сингулярная лесопилка** — 3 линии, первичный и вторичный выход.
- **Сингулярная индукционная печь** — 3 линии по два входа, рецепты Индукционного
  плавильщика TE. Второй вход линии принимает только то, что образует рецепт с первым, поэтому
  трубы и автовход сами собирают правильные пары. Все 8 режимов сторон оригинала, включая
  отдельные входы для первого и второго ингредиента.
- **Сингулярный магматический тигель** — 3 линии, плавящие в общий бак на **100 000 mB**.
  Прогресс показан «жидкой» стрелкой, как в TE.
- **Сингулярный жидкостный транспозер** — 3 линии вокруг общего бака на **100 000 mB**;
  кнопка переключает всю машину между наполнением и извлечением. Переносные баки и другие
  жидкостные контейнеры наполняются и опустошаются постепенно, по одному на линию.
- **Сингулярный зарядник** — 9 линий. Каждая либо заряжает вставленный предмет с RF-буфером,
  либо выполняет рецепт Заряжателя.
- **Сингулярный циклический сборщик** — 6 параллельных слотов схем, настоящие схемы TE, буфер
  на 18 слотов и бак на **100 000 mB** (жидкость подменяет предмет-контейнер в рецепте, как у
  настоящего Циклического сборщика).

## Хранилища

- **Сингулярная ячейка** — до **1 000 000 000 000 RF** (1 триллион). GUI и Waila показывают
  настоящее число, а не потолок ~2,15 млрд, который может сообщить `int`-based RF-API.
- **Сингулярный бак** — **4 096 000 mB**, в 8 раз больше резонансного бака TE. Жидкость видна
  за стеклом; ключ включает слив вниз (оранжевая рамка), лишнее переливается в бак сверху,
  вёдра работают правым кликом, компаратор показывает уровень.
- **Сингулярный сейф** — **120 слотов** (у резонансного сейфа TE не больше 104). Модель сейфа
  TE с открывающейся крышкой.

Бак и сейф при любом разрушении сохраняют содержимое в выпавшем предмете. Машины с баком
(тигель, транспозер) при разборке ключом тоже уносят жидкость с собой.

## Набор улучшения

ПКМ **Набором улучшения** по уже стоящей машине TE (Измельчитель, Печь, Лесопилка,
Индукционный плавильщик, Магматический тигель, Жидкостный транспозер, Заряжатель, Циклический
сборщик), по переносному баку или сейфу превращает её в сингулярную версию прямо на месте.
Переносятся предметы, расширения, RF, жидкость, направление, настройка сторон и имя; то, чему
не нашлось места, отдаётся игроку. Защищённый чужой блок улучшить нельзя.

Также в моде есть **Сингулярная шестерня** и **Рамка механизма (сингулярная)** — общая
крафтовая база для всех машин. Все рецепты собираются только из крафтовых материалов.

## Совместимость с Thermal Expansion

Мод намеренно повторяет поведение оригинала:

- **Настоящие расширения TE** — Автовход/выход, Реконфигурируемые стороны, Красный контроль,
  Скорость механизма, Вторичный выход, Хранилище энергии. В слоте расширения лежит ровно один
  предмет, и один тип нельзя поставить дважды.
- **Вкладка «Конфигурация»** в стиле CoFH с индикаторами прямо на блоке. ЛКМ — вперёд, ПКМ —
  назад, Shift — сброс. Автоматика, как и в TE, работает только через стороны «Вход» и
  «Выход»; сторона «Все» — для труб.
- **Вкладка «Красный контроль»** (Отключено / Низкий / Высокий сигнал).
- **Гаечный ключ**: клик поворачивает машину вместе с настройкой сторон, **Shift+ПКМ
  разбирает** её в предмет с расширениями, настройками и энергией. Реализован
  `IDismantleable`, так что подходят и ключи других модов. Ломать можно любым инструментом —
  содержимое не теряется.
- **Redprint из TE** копирует настройку сторон и красного камня между машинами одного типа.
- **Компаратор**: машины — число занятых линий, Сборщик — заполненность буфера, Ячейка —
  заряд, Бак — уровень жидкости.
- **Звуки работы** — настоящие звуковые события TE.
- **Waila** (опционально) — запас RF, расход, активные линии и содержимое бака.
- **NEI** (опционально) — клик по стрелке прогресса открывает рецепты этой машины.

Свежепоставленная машина несёт те же 3 стандартных расширения, что и машины TE. Машину можно
переименовать в наковальне — имя сохраняется.

## Интерфейс

- Прогресс — настоящая заполняющаяся стрелка TE и значок машины; подсказка показывает
  вложенный и требуемый RF.
- Слоты подсвечиваются цветом роли по текущей настройке сторон; подсказка на пустом слоте
  говорит, что в него класть.
- **Закрепление линий**: Shift+ПКМ по занятому входному слоту закрепляет линию за этим
  предметом — туда не попадёт ничего другого, ни руками, ни трубами, ни автовходом.
- **Вкладка «Информация»** с описанием машины и подсказками (прокрутка колёсиком).
- Открытая вкладка запоминается между машинами, клики озвучены тонами оригинала, баки
  нарисованы текстурой самой жидкости.

## Собственные расширения мода

- **Расширение: Скорость Уровень 4** — x10 скорость обработки за +200% к расходу RF/т сверх
  3-го уровня. Работает на всех машинах мода, кроме Сборщика.
- **Расширение: Вторичное сито уровень 4** — +200% к шансу вторичного выхода за +25% к
  расходу RF/т. Работает на Измельчителе, Лесопилке, Индукционной печи и Транспозере. Как и в
  TE, Индукционная печь (любой побочный продукт) и Лесопилка (опилки) при сите могут выдать
  второй экземпляр.

## Настройка

При первом запуске создаётся `config/ThermalADD.cfg`:

- у каждой машины — ёмкость буфера, скорость приёма RF и базовый расход RF/т;
- ёмкость Сингулярной ячейки и вместимость Сингулярного бака;
- отключение любого рецепта по отдельности (блок остаётся зарегистрированным, миры не
  ломаются);
- `gui.colorBlindPalette` — палитра рамок слотов, безопасная для дальтоников.

Значения ограничиваются безопасным диапазоном (пределы описаны в самом конфиге). На сервере
его значения передаются клиентам при входе, так что GUI показывает настоящие цифры сервера.

## Сборка

Нужны jar-файлы CoFHCore, ThermalExpansion и ThermalFoundation (и NotEnoughItems — только для
компиляции интеграции). Они не входят в репозиторий — см. [`libs/README.txt`](libs/README.txt).
Положите их в `libs/` и выполните:

```powershell
gradle build
```

Собранный jar появится в `build/libs/`.

## Зависимости (для запуска)

- Minecraft Forge для 1.7.10 (`10.13.4.1614` или совместимая)
- CoFHCore 3.1.4+
- ThermalExpansion 4.1.5+
- ThermalFoundation 1.2.6+
- Waila 1.5.10 *(опционально)*
- NotEnoughItems *(опционально)*

## Благодарности

Построено на основе CoFHCore / Thermal Expansion от Team CoFH. Не аффилировано с Team CoFH и
не одобрено ими.

---

<a name="english"></a>
## 🇬🇧 English

A [Thermal Expansion 4](https://www.curseforge.com/minecraft/mc-mods/thermal-expansion) addon
for Minecraft **1.7.10** (Forge). Adds upgraded, multi-line, `UltimateResonant`-tier versions of
TE4 machines running on Thermal Expansion's own recipe lists, ultra-capacity storage, a kit that
upgrades machines already in the world, and a couple of mod-original "beyond spec" augments.

## Machines

Every machine processes several lines in parallel from one shared RF buffer, has 9 augment
slots and its own RF tier.

- **Singularity Pulverizer** — 3 lines, primary and secondary output.
- **Singularity Redstone Furnace** — 3 lines.
- **Singularity Sawmill** — 3 lines, primary and secondary output.
- **Singularity Induction Smelter** — 3 lines with two inputs each, on TE's Induction Smelter
  recipes. A line's second input only takes what forms a recipe with its first, so pipes and
  auto input build valid pairs on their own. All 8 of the original's side modes, including
  separate inputs for the first and second ingredient.
- **Singularity Magma Crucible** — 3 lines melting into a shared **100,000 mB** tank, with TE's
  fluid-filled progress arrow.
- **Singularity Fluid Transposer** — 3 lines around a shared **100,000 mB** tank; a button
  switches the whole machine between filling and extracting. Portable tanks and other fluid
  containers are filled or emptied gradually, one per line.
- **Singularity Charger** — 9 lines. Each either charges an inserted RF-holding item or runs a
  Charger recipe.
- **Singularity Cyclic Assembler** — 6 parallel schematic slots, genuine TE schematics, an
  18-slot buffer and a **100,000 mB** tank (fluid stands in for filled containers in a recipe,
  as on the real Cyclic Assembler).

## Storage

- **Singularity Energy Cell** — up to **1,000,000,000,000 RF** (one trillion). The GUI and
  Waila show the real number, not the ~2.15 billion ceiling the `int`-based RF API can report.
- **Singularity Tank** — **4,096,000 mB**, eight times TE's Resonant tank. The fluid shows
  through the glass; a wrench toggles pour-down mode (orange frame), overflow goes into the tank
  above, buckets work on right-click, and a comparator reads the level.
- **Singularity Strongbox** — **120 slots** (TE's Resonant Strongbox tops out at 104), with TE's
  own strongbox model and opening lid.

The tank and the strongbox keep their contents inside the dropped item however they are broken.
Machines with a tank (Crucible, Transposer) carry their fluid along when dismantled too.

## Upgrade Kit

Right-click a placed TE machine (Pulverizer, Furnace, Sawmill, Induction Smelter, Magma
Crucible, Fluid Transposer, Charger, Cyclic Assembler), portable tank or strongbox with the
**Singularity Upgrade Kit** to turn it into its singularity version in place. Items, augments,
RF, fluid, facing, side configuration and name carry over; anything with no place is handed to
the player. Someone else's secured block cannot be upgraded.

The mod also adds a **Singularity Gear** and a **Machine Frame (Singularity)** — the shared
crafting base for every machine. Every recipe uses crafted materials only.

## Thermal Expansion parity

The mod deliberately mirrors the original's behaviour:

- **Real TE augments** — Auto Input/Output, Reconfigurable Sides, Redstone Control, Machine
  Speed, Machine Secondary, Energy Storage. An augment slot holds exactly one item, and a type
  cannot be installed twice.
- **CoFH-style Configuration tab** with in-world badges. Left-click cycles forward, right-click
  back, shift resets. As in TE, automation only moves through Input and Output sides; an "All"
  side is for pipes.
- **Redstone Control tab** (Disabled / Low / High).
- **Wrench**: a click rotates the machine along with its side setup, **sneak + right-click
  dismantles** it into an item keeping its augments, settings and energy. `IDismantleable` is
  implemented, so other mods' wrenches work too. Any tool breaks the blocks without losing
  their contents.
- **TE's Redprint** copies side and redstone settings between machines of the same type.
- **Comparator output**: machines report busy lines, the Assembler its buffer, the Cell its
  charge, the Tank its fluid level.
- **Working sounds** use Thermal Expansion's own sound events.
- **Waila** (optional) — stored RF, draw, active lines and tank contents.
- **NEI** (optional) — clicking a progress arrow opens that machine's recipes.

A freshly placed machine carries the same 3 default augments TE machines do. Machines can be
renamed in an anvil; the name is kept.

## Interface

- Progress uses TE's filling arrow and machine glyph; a tooltip shows RF invested and required.
- Slots get a role-coloured ring from the live side setup; hovering an empty slot says what goes
  in it.
- **Line locks**: shift + right-click a filled input slot to lock that line to its item —
  nothing else gets in, by hand, pipe or auto input.
- **Information tab** with the machine's description and tips (mouse-wheel scrolling).
- The open tab is remembered between machines, clicks use the original's pitches, and tanks are
  drawn with the fluid's own texture.

## Mod-original augments

- **Augment: Speed Level 4** — x10 processing speed for +200% RF/t over Level 3. Works on every
  machine in the mod except the Assembler.
- **Augment: Secondary Sieve Level 4** — +200% secondary output chance for +25% RF/t. Works on
  the Pulverizer, Sawmill, Induction Smelter and Transposer. As in TE, the Induction Smelter
  (any byproduct) and the Sawmill (sawdust) can roll a second copy with a sieve installed.

## Configuration

`config/ThermalADD.cfg` is created on first run:

- per machine: energy buffer, RF intake rate and base RF/t cost;
- the Singularity Cell's capacity and the Singularity Tank's volume;
- an on/off switch for every recipe (the block stays registered, worlds are unaffected);
- `gui.colorBlindPalette` — a colour-blind-safe palette for the slot rings.

Values are clamped to a safe range (the limits are documented in the file). A server sends its
values to clients on login, so the GUI shows the server's real numbers.

## Building

Requires the CoFHCore, ThermalExpansion and ThermalFoundation jars (and NotEnoughItems, only to
compile the integration). They are not included — see [`libs/README.txt`](libs/README.txt). Put
them in `libs/` and run:

```powershell
gradle build
```

The mod jar is produced under `build/libs/`.

## Dependencies (runtime)

- Minecraft Forge for 1.7.10 (`10.13.4.1614` or compatible)
- CoFHCore 3.1.4+
- ThermalExpansion 4.1.5+
- ThermalFoundation 1.2.6+
- Waila 1.5.10 *(optional)*
- NotEnoughItems *(optional)*

## Credits

Built on top of CoFHCore / Thermal Expansion by Team CoFH. Not affiliated with or endorsed by
Team CoFH.
