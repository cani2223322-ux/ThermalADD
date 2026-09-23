# ThermalADD

🇷🇺 **Русский** | 🇬🇧 [English](#english)

Аддон для [Thermal Expansion 4](https://www.curseforge.com/minecraft/mc-mods/thermal-expansion)
под Minecraft **1.7.10** (Forge). Добавляет улучшенные, многослотовые версии машин TE4 тира
`UltimateResonant`, работающие на настоящих списках рецептов Thermal Expansion, плюс пару
собственных «запредельных» расширений и энергоячейку сверхбольшой ёмкости.

## Машины

- **Сингулярный измельчитель** — 3 параллельные линии обработки вместо одной, 9 слотов
  расширений, собственный энергетический тир.
- **Сингулярная красная печь** — 3 параллельные линии вместо одной, 9 слотов расширений.
- **Сингулярная лесопилка** — 3 параллельные линии, первичный и вторичный выход, 9 слотов
  расширений.
- **Сингулярный зарядник** — 9 линий одновременной зарядки вместо одного слота настоящего
  Заряжателя. Каждая линия независимо поддерживает оба режима оригинала: либо заряжает
  вставленный предмет с RF-буфером (конденсаторы, инструменты, брони), либо выполняет обычный
  рецепт Заряжателя с фиксированной стоимостью.
- **Сингулярный циклический сборщик** — 6 параллельных слотов схем вместо одного, принимает
  настоящие схемы TE, 9 слотов расширений и собственный резервуар на **100 000 mB** (та же
  механика подмены предмета-контейнера жидкостью из бака, что и у настоящего Циклического
  сборщика).
- **Сингулярная ячейка** — единый симметричный блок хранения энергии на основе настоящей
  Resonant Energy Cell, доведённый до предела, который RF-API вообще способен выразить.
  Истинная ёмкость — до **1 000 000 000 000 RF** (1 триллион); GUI показывает реальное число,
  а не потолок ~2,15 млрд, который `int`-based RF-API может сообщить другим модам. Каждая
  грань принимает или отдаёт столько RF, сколько позволяет один вызов передачи.

Ещё в моде есть **Сингулярная шестерня** и **Рамка механизма (сингулярная)** — общая
крафтовая база для всех машин.

## Совместимость с Thermal Expansion

Мод намеренно повторяет поведение оригинала, а не изобретает своё:

- **Настоящие расширения TE** — Автовход/выход, Реконфигурируемые стороны, Красный контроль,
  Скорость механизма, Вторичный выход, Хранилище энергии. Одно и то же расширение нельзя
  поставить в два слота: блок отказывает в дубликате, а не игнорирует его молча, как TE.
- **Вкладка «Конфигурация»** в стиле CoFH с индикаторами подключения прямо на блоке
  (синий/красный/жёлтый/оранжевый), показывающими режим каждой грани. Жесты те же:
  ЛКМ — вперёд, ПКМ — назад, Shift — сброс.
- **Вкладка «Красный контроль»** (Отключено / Низкий / Высокий сигнал).
- **Серповидный гаечный ключ**: обычный клик поворачивает машину, **Shift+ПКМ разбирает** её в
  предмет, который сохраняет расширения, конфигурацию сторон и накопленную энергию. Реализован
  `IDismantleable`, поэтому подходят и инструменты других модов.
- **Redprint из TE копирует настройки** между машинами — реализован `IPortableData`. Копируется
  конфигурация сторон и режим красного камня, только между машинами одного типа.
- **Выход на компаратор** у всех блоков: машины показывают, сколько линий занято, Сборщик —
  заполненность буфера, Ячейка — заряд.
- **Звук работы** у Измельчителя, Печи и Лесопилки — настоящие звуковые события Thermal
  Expansion.
- **Waila** (если установлена) показывает у каждой машины запас RF, текущий расход и число
  активных линий, а у Ячейки — её настоящую ёмкость за пределами `int`.

Свежепоставленный блок сразу несёт те же 3 стандартных расширения, что и настоящие машины TE
(Автовыход, Красный контроль, Реконфигурируемые стороны). Машину можно переименовать в
наковальне — имя сохраняется при установке и возвращается на выпавший предмет.

## Интерфейс

Слоты подсвечиваются цветной рамкой по роли текущей конфигурации грани — рамка появляется
только после того, как игрок сам настроил хотя бы одну грань (при установке все грани
начинают как «Отключено»). Прогресс обработки показан настоящей заполняющейся стрелкой
Thermal Expansion рядом с фирменным значком машины. Столбик RF отрисован настоящей текстурой
шкалы TE, показывает точный запас при наведении и имеет отдельный слот под Конденсаторы —
вставленный предмет разряжается прямо в буфер машины.

Клики по кнопкам конфигурации и красного камня озвучены теми же тонами, что и в оригинале.
Открытая вкладка запоминается и раскрывается сама в следующей машине. Подсказки прячутся,
пока на курсоре предмет.

## Собственные расширения мода

Два «запредельных» расширения, которых нет в настоящем Thermal Expansion; крафтятся
«прокачкой» максимального тира соответствующего расширения TE:

- **Расширение: Скорость Уровень 4** — x10 скорость обработки за +200% к расходу RF/т сверх
  и без того дорогого 3-го тира. Работает на Измельчителе, Печи, Лесопилке и Заряднике.
- **Расширение: Вторичное сито уровень 4** — +200% к шансу вторичного выхода (фактически
  гарантирует срабатывание для любого рецепта с ненулевым шансом) за +25% к расходу RF/т.
  Работает на Измельчителе и Лесопилке.

## Настройка

При первом запуске создаётся `config/ThermalADD.cfg`. В нём можно задать:

- у каждой машины — ёмкость буфера, скорость приёма RF и базовый расход RF/т;
- ёмкость Сингулярной ячейки;
- включение/отключение любого рецепта крафта по отдельности (сам блок остаётся
  зарегистрированным, так что существующие миры не ломаются);
- `gui.colorBlindPalette` — палитра рамок слотов, безопасная для дальтоников.

Значения ограничиваются безопасным диапазоном: часть показаний GUI передаётся ванильным
16-битным протоколом, и выход за его пределы приводил бы к мусору на экране. Пределы указаны
в комментариях самого конфига.

## Сборка

Требуются jar-файлы CoFHCore, ThermalExpansion и ThermalFoundation (не входят в репозиторий —
см. [`libs/README.txt`](libs/README.txt)), их нужно положить в `libs/`. Затем из этой папки:

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

## Благодарности

Построено на основе CoFHCore / Thermal Expansion от Team CoFH. Не аффилировано с Team CoFH и
не одобрено ими.

---

<a name="english"></a>
## 🇬🇧 English

A [Thermal Expansion 4](https://www.curseforge.com/minecraft/mc-mods/thermal-expansion) addon
for Minecraft **1.7.10** (Forge). Adds upgraded, multi-slot, `UltimateResonant`-tier versions
of TE4 machines, each running on Thermal Expansion's own real recipe lists, plus a couple of
mod-original "beyond spec" augments and an ultra-capacity energy cell.

## Machines

- **Singularity Pulverizer** — 3 parallel processing lines instead of one, 9 augment slots, its
  own RF tier.
- **Singularity Redstone Furnace** — 3 parallel lines instead of one, 9 augment slots.
- **Singularity Sawmill** — 3 parallel lines, primary and secondary output, 9 augment slots.
- **Singularity Charger** — 9 simultaneous charging lines instead of the real Charger's single
  slot. Each line independently supports both of the original's modes: it either charges an
  inserted RF-holding item (capacitors, tools, armor) in place, or runs a normal flat-cost
  Charger recipe.
- **Singularity Cyclic Assembler** — 6 parallel schematic slots instead of one, accepts genuine
  TE schematics, 9 augment slots, and its own **100,000 mB** fluid tank (the same
  filled-container-substitution mechanic real TE's Cyclic Assembler uses).
- **Singularity Energy Cell** — a single, symmetric energy storage block modeled on TE's own
  Resonant Energy Cell, pushed past what the standard RF API can normally express. Its true
  storage holds up to **1,000,000,000,000 RF**; the GUI displays the real number, not the
  ~2.15 billion ceiling the `int`-based RF API can report to other mods. Every side accepts or
  gives as much RF as a single transfer call allows.

The mod also adds a **Singularity Gear** and a **Machine Frame (Singularity)** — the shared
crafting base for every machine.

## Thermal Expansion parity

The mod deliberately mirrors the original's behaviour rather than inventing its own:

- **Real TE augments** — Auto Input/Output, Reconfigurable Sides, Redstone Control, Machine
  Speed, Machine Secondary, Energy Storage. The same augment can never occupy two slots: the
  block refuses the duplicate outright rather than silently ignoring it the way real TE does.
- **CoFH-style Configuration tab** with in-world connection badges (blue/red/yellow/orange)
  showing each face's mode. Same gestures: left-click cycles forward, right-click back, shift
  resets.
- **Redstone Control tab** (Disabled / Low / High).
- **Crescent Hammer**: a plain click rotates the machine, **sneak + right-click dismantles** it
  into an item that keeps its augments, side configuration and stored energy. `IDismantleable`
  is implemented, so other mods' wrenches work too.
- **TE's Redprint copies settings** between machines via `IPortableData` — side configuration
  and redstone mode, only between machines of the same type.
- **Comparator output** on every block: machines report how many lines are busy, the Assembler
  its ingredient buffer, the Cell its charge.
- **Working sound** on the Pulverizer, Furnace and Sawmill, using Thermal Expansion's own sound
  events.
- **Waila** (if installed) shows each machine's stored RF, current draw and active line count,
  and the Cell's real beyond-`int` capacity.

A freshly placed block already carries the same 3 default augments real TE machines do (Auto
Output, Redstone Control, Reconfigurable Sides). Machines can be renamed in an anvil; the name
survives placement and comes back on the dropped item.

## Interface

Slots get a role-coloured highlight ring that tracks the matching side's live configuration -
the ring only appears once the player has actually configured a face (a freshly placed block
starts with every face Disabled). Processing progress uses Thermal Expansion's own filling
arrow next to the machine's signature glyph. The RF bar is drawn with TE's own gauge texture,
shows the exact stored amount on hover, and has a dedicated slot beneath it for Capacitors -
whatever is dropped in there drains straight into the machine's buffer.

Configuration and redstone buttons click with the same pitches as the original. The tab you had
open is remembered and re-opens in the next machine. Tooltips are suppressed while a stack is
on the cursor.

## Mod-original augments

Two "beyond spec" augments that don't exist in real Thermal Expansion, crafted by upgrading
TE's own top-tier augment items:

- **Augment: Speed Level 4** — x10 processing speed for +200% RF/t over Level 3's already-steep
  cost. Works on the Pulverizer, Furnace, Sawmill and Charger.
- **Augment: Secondary Sieve Level 4** — +200% secondary output chance (effectively guarantees
  any recipe with a nonzero secondary chance) for +25% RF/t. Works on the Pulverizer and
  Sawmill.

## Configuration

`config/ThermalADD.cfg` is created on first run. It lets you set:

- per machine: energy buffer, RF intake rate and base RF/t processing cost;
- the Singularity Cell's capacity;
- an individual on/off switch for every crafting recipe (the block itself stays registered, so
  existing worlds are unaffected);
- `gui.colorBlindPalette` — a colour-blind-safe palette for the slot role rings.

Values are clamped to a safe range: some GUI readouts travel over vanilla's 16-bit window
property protocol, and exceeding it would show garbage on screen. The limits are documented in
the config file itself.

## Building

Requires the CoFHCore, ThermalExpansion and ThermalFoundation jars (not included - see
[`libs/README.txt`](libs/README.txt)) placed in `libs/`. Then, from this directory:

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

## Credits

Built on top of CoFHCore / Thermal Expansion by Team CoFH. Not affiliated with or endorsed by
Team CoFH.
