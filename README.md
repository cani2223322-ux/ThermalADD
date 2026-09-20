# ThermalADD

🇷🇺 **Русский** | 🇬🇧 [English](#english)

Аддон для [Thermal Expansion 4](https://www.curseforge.com/minecraft/mc-mods/thermal-expansion)
под Minecraft **1.7.10** (Forge). Добавляет улучшенные, многослотовые версии машин TE4 тира
`UltimateResonant`, работающие на настоящих списках рецептов Thermal Expansion, плюс пару
собственных "запредельных" расширений и энергоячейку сверхбольшой ёмкости:

- **Улучшенный Измельчитель (Advanced Pulverizer)** — 3 параллельных входных слота (вместо 1),
  9 слотов расширений, собственный энергетический тир.
- **Улучшенный циклический сборщик (Improved Cyclic Assembler)** — 6 параллельных слотов
  схем (вместо 1), принимает настоящие схемы TE.
- **Улучшенная Печь (Advanced Furnace)** — 3 параллельных входных слота (вместо 1), 9 слотов
  расширений.
- **Сингулярная ячейка (Singularity Energy Cell)** — единый симметричный блок хранения
  энергии, за основу взята настоящая Resonant Energy Cell из Thermal Expansion, но доведённая
  до предела, который стандартный RF-API вообще способен выразить. Истинная внутренняя
  ёмкость — до **1 000 000 000 000 RF** (1 триллион) — GUI показывает реальное число, а не
  потолок ~2.15 млрд, который лежащий в основе `int`-based RF-API может сообщить другим модам.
  Каждая грань принимает или отдаёт столько RF, сколько позволяет один вызов передачи —
  фактически без ограничений. Имеет собственную настоящую TE-стиля вкладку "Конфигурация"
  (Откл/Выход/Вход на каждую грань, логика полностью скопирована с настоящей Energy Cell) и
  перекрашенную в фиолетово-пурпурно-золотисто-голубой градиент текстуру настоящей ячейки TE.
  Крафтится из 3 настоящих Резонирующих энергетических ячеек, слитков Эндерия/Сигнала,
  Резонирующего Конденсатора и Золотой катушки — каждый ингредиент кроме самих ячеек сам
  является многоступенчато скрафченным предметом, ничего "сырого".

Все три машины поддерживают настоящие предметы-расширения Thermal Expansion (Автовход/выход,
Реконфигурируемые стороны, Красный контроль, Скорость механизма, Вторичный выход, Хранилище
энергии, ...), CoFH-стиля вкладку "Конфигурация" с индикаторами подключения прямо на блоке
(синий/оранжевый/красный), показывающими текущий режим каждой грани, вкладку "Красный
контроль" (Отключено/Низкий/Высокий сигнал, дизайн полностью скопирован с настоящей TE), и
поддержку Серповидного гаечного ключа (Crescent Hammer) для поворота блока/сторон.
Свежепоставленный блок сразу несёт в себе те же 3 стандартных расширения, что и настоящие
машины Thermal Expansion (Автовыход, Красный контроль, Реконфигурируемые стороны).

### Собственные расширения мода

Два "запредельных" расширения, которых нет в настоящем Thermal Expansion, крафтятся путём
"прокачки" максимального тира соответствующего расширения настоящего TE:

- **Расширение: Скорость Уровень 4** — 4-й тир расширения скорости, на шаг выше максимального
  тира настоящего TE. x10 скорость обработки за +200% к расходу RF/т сверх и без того дорогого
  3-го тира. Работает на Улучшенном Измельчителе и Улучшенной Печи.
- **Расширение: Вторичное сито уровень 4** — 4-й тир расширения вторичного выхода, только для
  Улучшенного Измельчителя. +200% к шансу вторичного выхода (фактически гарантирует
  срабатывание для любого рецепта с ненулевым шансом) за +25% к расходу RF/т.

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
- Waila 1.5.10 *(опционально)* — если установлена, подсказка Сингулярной ячейки покажет
  реальную (за пределами диапазона `int`) вместимость вместо урезанного отображения самой
  WAILA. Без неё мод собирается и работает без проблем.

## Благодарности

Построено на основе CoFHCore / Thermal Expansion от Team CoFH. Не аффилировано с Team CoFH и
не одобрено ими.

---

<a name="english"></a>
## 🇬🇧 English

A [Thermal Expansion 4](https://www.curseforge.com/minecraft/mc-mods/thermal-expansion) addon
for Minecraft **1.7.10** (Forge). Adds upgraded, multi-slot, `UltimateResonant`-tier versions
of TE4 machines, each running on Thermal Expansion's own real recipe lists, plus a couple of
mod-original "beyond spec" augments and a single-tier ultra-capacity energy cell:

- **Advanced Pulverizer** — 3 parallel input slots (instead of 1), 9 augment slots, its own
  RF tier.
- **Improved Cyclic Assembler** — 6 parallel schematic slots (instead of 1), accepts genuine
  TE schematics.
- **Advanced Furnace** — 3 parallel input slots (instead of 1), 9 augment slots.
- **Singularity Energy Cell** — a single, symmetric energy storage block modeled on Thermal
  Expansion's own Resonant Energy Cell, pushed past what the standard RF API can normally
  express. Its true internal storage holds up to **1,000,000,000,000 RF** (the GUI displays the
  real number, not the ~2.15 billion ceiling the underlying `int`-based RF API can report to
  other mods), and every side accepts or gives as much RF as a single transfer call allows -
  effectively unlimited throughput. Comes with its own real Thermal-Expansion-style
  Configuration tab (Disabled/Output/Input per face, matching TE's own Energy Cell logic
  exactly) and a violet-magenta-gold-cyan gradient reskin of TE's own cell texture. Crafted from
  3 real Resonant Energy Cells plus Enderium/Signalum ingots, a Resonant Capacitor and a Gold
  Power Coil - every non-cell ingredient is itself a multi-step crafted item, nothing raw.

All three machines support real Thermal Expansion augment items (Auto Input/Output,
Reconfigurable Sides, Redstone Control, Machine Speed, Machine Secondary, Energy Storage, ...),
a CoFH-style Configuration side tab with in-world connection badges (blue/orange/red) showing
each face's current mode, a Redstone Control tab (Disabled/Low/High, matching real TE's own
tab design), and Crescent Hammer (wrench) support for facing/side rotation. A freshly placed
machine already carries the same 3 default augments real Thermal Expansion machines do (Auto
Output, Redstone Control, Reconfigurable Sides).

### Mod-original augments

Two "beyond spec" augments that don't exist in real Thermal Expansion, crafted by upgrading
TE's own top-tier augment items:

- **Augment: Speed Level 4** — a 4th Machine Speed tier, one step past TE's own top tier.
  x10 processing speed for +200% RF/t over Level 3's already-steep cost. Works on the Advanced
  Pulverizer and Advanced Furnace.
- **Augment: Secondary Sieve Level 4** — a 4th Machine Secondary tier for the Advanced
  Pulverizer only. +200% secondary output chance (effectively guarantees any recipe with a
  nonzero secondary chance) for +25% RF/t.

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
- Waila 1.5.10 *(optional)* — if present, the Singularity Cell's tooltip shows its real
  (beyond `int`-range) capacity instead of Waila's own generic, capped RF display. The mod
  builds and runs fine without it.

## Credits

Built on top of CoFHCore / Thermal Expansion by Team CoFH. Not affiliated with or endorsed by
Team CoFH.
