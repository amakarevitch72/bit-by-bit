# Bit by Bit — Product spec

## 1. Concept

A single-player mobile game that teaches how a computer works by having the player build one.
Each level adds one idea. Each finished level becomes a **chip**: a single block with a symbol,
which the player uses in later levels. This is the core lesson of the game: complex things are made of
simple things, packed into blocks.

Target: phone, portrait, designed at 390×844. Offline. One local save.

## 2. Core loop

```
Journey map → (Lesson intro) → Workbench: build → test → Level complete → chip added to Toolbox → next level
```

- A level is **locked** until the one before it is complete. Only the next level is playable ("current").
- A finished level can be replayed.
- Milestones (★) are levels where the player *uses* what they built (calculator, mini computer).

## 3. Screens

### 3.1 Journey map — `design/Main.dc.html`
- Header: app name, title, progress "N of 24 levels", next milestone, progress bar.
- Chapters as sections. Unlocked chapters show a vertical rail of level nodes:
  done = green circle with a check, current = amber circle with the level number plus a large card
  (title, one-line goal, "uses:" chips, **Start level** button), locked = gray circle with a lock.
- Fully locked chapters are collapsed into a card listing their level names; milestones are marked ★ in amber.
- Bottom tabs: **Journey**, **Chips** (Toolbox), **Sandbox** (a free-build workbench; not designed yet).

### 3.2 Lesson intro — `design/Lesson.dc.html`
- 1–3 cards that explain the level's idea, with a small diagram and a tiny truth table.
- A goal box ("Your goal: …") and the button **Start building**. Footer: "Unlocks: X chip".
- Step dots show lesson progress (1 of 3).

### 3.3 Workbench (the generic pattern) — Workbench/HalfAdder/FullAdder/Adder4/Subtractor/SRLatch
From top to bottom:
1. **Top bar:** back, "LEVEL N · WORKBENCH", level title, hint button (amber light-bulb).
2. **Readout** (from Level 8 on): the numbers in decimal with binary underneath; the result is green when right, red when wrong, "?" when the circuit is incomplete.
3. **Board:** a dark panel with a dotted grid. Inputs come in on the left, outputs (LED lights) are on the right. Contains:
   - **Sockets:** empty = dashed gray outline with "+"; turns amber-dashed while a chip is selected; filled = green outline with the chip symbol. Tapping a filled socket removes its chip.
   - **Links** (carry links, feedback loops): small toggle buttons. Off = amber dashed; on = green solid.
   - **Wires** take the color of their signal: green = 1, gray = 0, dashed and faded = unpowered (something upstream is missing).
4. **Chip tray:** chips the player owns, including some wrong ones (distractors). **Tap a chip, then tap a socket.** The hint line changes to "NOW TAP A SOCKET".
5. **Inputs:** large toggle buttons (A, B, Cin…) showing 0/1; green when 1. Sequential levels use **pulse** buttons instead (about 450 ms on, then off).
6. **Test panel:** see §5.
7. **Footer:** a disabled button that explains what is missing ("Fill both sockets…", "Run the test suite…", "A row is wrong — swap a chip"); it becomes a green success button once all tests pass.

### 3.4 Level complete — `design/Complete.dc.html`
- Badge "LEVEL N COMPLETE", a large drawing of the new chip with its pins, "X chip unlocked".
- Abstraction strip: `2 × transistor → 1 × AND`.
- Stats (rows passed, parts used); a teaser card for the next level; buttons **Next level** and **See it in your toolbox**.

### 3.5 Chip toolbox — `design/Toolbox.dc.html`
- 2-column grid of chip cards: symbol, name, "built from …". Locked chips are dashed with a lock.

### 3.6 Calculator milestone — `design/Calculator.dc.html`
- Two tabs: **Use it** and **X-ray: see your chips**.
- Use it: a 4-place 7-segment display (sign + 3 digits), a Cout lamp, a bus line in binary, the last key's code, a keypad, and a "Try it" checklist of 3 sums.
- X-ray: the chain of chip cards (Keypad encoder → Latch A → 8-bit ALU → Comparator → 7-segment decoder), each showing live values and the level that built it. Latch A is a dashed **"Ch 4 preview"** card.

### 3.7 Timeline workbench (sequential logic) — `design/SRLatch.dc.html`
- Same as the workbench, but the truth table is replaced by a **timeline**: one row per signal (S, R, Q), 14 time steps, a raised green bar for 1 and a flat line for 0.
- The test draws "Q want" (gray) under "Q got"; wrong steps are red.

## 4. Building the undesigned levels

| Levels | Base it on | Notes |
|---|---|---|
| 2 OR, 3 NOT, 4 NAND/NOR | `Workbench.dc.html` (transistor level) | OR = 2 transistors in parallel; NOT = 1 transistor with a pull-up; NAND/NOR = the gate followed by NOT. |
| 5 XOR | `HalfAdder.dc.html` (chip sockets) | 3–4 sockets; reference solution `(A OR B) AND (A NAND B)`. |
| 10 Comparator, 12 4-bit ALU | `Adder4.dc.html` / `Subtractor.dc.html` | 4 columns, readout, test across all 256 input pairs. ALU: an op switch picks ADD/SUB via XOR on B plus Cin = SUB. |
| 11 7-segment decoder | new: a digit input and 7 segment outputs | Test 0–9; show the segments live. |
| 13 Keypad encoder | new: 10 keys → 4-bit code | Test every key. |
| 16 D flip-flop, 17 Register, 18 Counter & clock | `SRLatch.dc.html` (timeline) | Add a CLK row to the timeline; data is captured only on the clock's rising edge. |
| 19 RAM | timeline + an address readout | |
| 20–23 CPU parts, 24 milestone | `Calculator.dc.html` (Use / X-ray tabs) | Milestone: run a tiny program step by step and watch the registers. |

## 5. Testing rules

- **Combinational levels:** exhaustive. 1–2 inputs: a per-row truth table the player fills in by flipping inputs; the current input row is outlined in amber.
  3 inputs: 8 tiles plus a **Run all** button. Multi-bit: **Test all 256** gives a pass count plus *one* failing example as text ("5 + 3 gave 0, want 8").
- Changing any chip or link **clears** the test results.
- **Sequential levels:** a fixed pulse sequence; check Q after every step; draw the result as a timeline.
- **Hints in failure messages:** add one where a classic mistake has a recognisable signature (e.g. "off by one?" when the subtractor's Cin = 0).
- **Bonus lines** on success when the player found a valid alternative answer (XOR instead of OR for the full adder's carry, HA in bit 0, XOR as a controllable inverter).

## 6. Simulation rules

- Signals are boolean. A socket with no chip outputs `null`; `null` spreads downstream (shown dashed and faded).
- Gate functions: AND, OR, XOR, NOT, NAND, NOR.
- Chip with 2 outputs (HA): `top = x XOR y`, `bot = x AND y`. A plain gate placed in a 2-output socket drives `top`; `bot = 0`.
- FA: `s = a ^ b ^ cin`, `co = ab + cin(a ^ b)`.
- **Feedback (latches):** repeat the gate updates from the previous state until nothing changes (max 8 passes).
  If it hasn't settled after 8 passes, mark it **oscillating** (a race) and say so in the UI.
- Two's complement: `A − B = A + ~B + 1` (mod 2ⁿ). Subtraction with Cout = 0 means a borrow (A < B).
- Calculator: 8-bit. `+` with Cout → display `OF`. `−` with Cout = 0 → display `−(256 − r)`. Chaining from a negative result uses its 8-bit pattern.

## 7. Copy and tone

Short, direct and encouraging. Explain *why* in one line. Every failure names the next action.
Success lines are celebratory but specific ("It remembers! Unlock the LATCH chip").
