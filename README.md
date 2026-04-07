# Firefighting Drone Simulation

## Overview
This project simulates a **firefighting system using autonomous drones** coordinated by a central scheduler. Fire incidents occur over a defined time from an events sheet, drones are dispatched based on priority, and fires may require multiple drones to fully extinguish.

## Main Systems

### FireIncidentSubsystem
- Listens on UDP port **6001** for a `loadFile|<path>` command at runtime
- Reads fire events from the specified CSV and dispatches them to the Scheduler at the correct simulation time
- Supports two CSV column layouts (auto-detected): `Time, EventType, ZoneID, Severity` and `Time, ZoneID, EventType, Severity, FaultType`

### Scheduler
- Central coordinator; maintains 3 **priority queues** (`HIGH`, `MODERATE`, `LOW`)
- State machine: IDLE → DISPATCHING → MONITORING → REFILLING / FAULT_HANDLING as needed
- Assigns fire missions to the closest idle drone with sufficient water
- Reschedules partially extinguished fires and tracks per-zone water remaining
- Loads zone definitions from a CSV file at runtime; defaults to a 4-zone 2×2 grid

### DroneSubsystem / DroneMachine
- Each drone runs in its own thread with an explicit state machine: IDLE, ONROUTE, EXTINGUISHING, RETURNING, REFILLING_AND_RECHARGING, FAULTED, DECOMMISSIONED
- Travels cell-by-cell across the grid; battery drains 1% per step
- Extinguishes fire with nozzle open/close delays and configurable flow rate; water depletes
- Returns to base and refills (15 L water + 100% battery) when tank is empty
- Handles soft faults (DRONE_STUCK — 10 s pause, 5% battery drain) and hard faults (NOZZLE_FAULT — permanent decommission)
- Decommissions automatically if battery reaches 0%

### SimulationClock
- Singleton providing shared simulation time across all subsystems
- Configurable speed multiplier (e.g. ×60 = 1 real second per simulated minute)

### FireEvent
- Represents a fire incident; tracks severity, required water, and remaining water needed
- Severity levels: LOW (5 L), MODERATE (10 L), HIGH (15 L)

### Zone
- Defines a rectangular zone by (xMin, xMax, yMin, yMax)
- Must be at least 3×3 cells; provides getCenterX() / getCenterY() for drone dispatch


## Key Features

- Priority-based fire scheduling (HIGH before MODERATE before LOW)
- Multi-drone swarm coordination with closest-drone dispatch
- Partial fire extinguishing — remainder re-queued if one drone cannot finish
- Fires held in a per-zone pending queue while a zone is already being serviced
- Water capacity enforcement — empty-tank drones return to a refill station before re-dispatch
- Battery drain during movement and during soft-fault pauses; auto-decommission at 0%
- Fault injection via CSV: soft faults (DRONE_STUCK) and hard faults (NOZZLE_FAULT)
- Dynamic zone loading from CSV at runtime (any number of zones, any rectangular shape)
- GUI with live drone positions, fire map, status panel, and file-upload buttons


## Project Structure

```
src/
  FireIncidentMain.java       Entry point for the FireIncidentSubsystem process
  FireIncidentSubsystem.java  Reads CSV, sends fire/fault events to Scheduler over UDP
  SchedulerMain.java          Entry point for the Scheduler + GUI process
  Scheduler.java              Central coordinator and state machine
  DroneMain.java              Entry point for the DroneSubsystem process
  DroneSubsystem.java         UDP layer — bridges Scheduler messages to DroneMachine
  DroneMachine.java           Pure drone state machine (no networking)
  DroneCallback.java          Interface DroneSubsystem implements for DroneMachine callbacks
  DroneInfo.java              Lightweight drone record held by Scheduler
  SimulationClock.java        Singleton simulation clock
  FireEvent.java              Fire incident data object
  FaultType.java              Enum: NONE, DRONE_STUCK, NOZZLE_FAULT
  Zone.java                   Rectangular zone with coordinate helpers
  DroneSwarmFrame.java        Main GUI window
  MapPanel.java               Live grid map panel
  StatusPanel.java            System log panel
  DroneStatusPanel.java       Per-drone status table
  fire_events.csv             Sample fire/fault event schedule
  zones.csv                   Default 4-zone layout
  zones_demo.csv              Alternative 5-zone demo layout

tests/
  Iteration2/                 Scheduler and FireEvent unit tests
  Iteration3/                 UDP integration tests
  Iteration4/                 Fault handling tests
  Iteration5/
    BatteryTest.java          Battery drain and decommission tests (DroneMachine)
    VariableZonesTest.java    Zone CSV loading and coordinate tests (Scheduler + Zone)
    AgentCapacityTest.java    Water capacity and refill-station enforcement tests (Scheduler)
```


## Setup

### Prerequisites
- **Java 11+** (tested with Java 17)
- **IntelliJ IDEA** (recommended) or any IDE with JUnit 4 support
- **JUnit 4** JAR on the test classpath (`junit-4.x.jar` + `hamcrest-core.jar`)

### IntelliJ Setup
1. Open the project root folder in IntelliJ (`File → Open`).
2. Mark `src/` as the **Sources Root** (right-click → Mark Directory as → Sources Root).
3. Mark `tests/` as the **Test Sources Root** (right-click → Mark Directory as → Test Sources Root).
4. Add JUnit 4 to the project:
   - `File → Project Structure → Modules → Dependencies → + → Library → From Maven`
   - Search for `junit:junit:4.13.2` and add it.
5. Set the output directory to `out/` (`File → Project Structure → Project → Project compiler output`).
6. Build the project: `Build → Build Project` (or `Ctrl+F9`).


## How to Run the Simulation

The simulation runs as **three separate processes**. Start them in order, each in its own terminal from the project root.

### Terminal 1 — Scheduler and GUI
```bash
java -cp out/production/Project SchedulerMain
```
The GUI window opens. You can load zone and fire event files from the GUI, or use the terminal flags below.

### Terminal 2 — Drones
```bash
java -cp out/production/Project DroneMain 1 2 3 4
```
Registers four drones (IDs 1–4). Add or remove IDs to change swarm size.

### Terminal 3 — Fire Incident Subsystem
```bash
# Wait for input via GUI, or pass files directly:
java -cp out/production/Project FireIncidentMain

# Load both files from the terminal:
java -cp out/production/Project FireIncidentMain --file src/fire_events.csv --zones src/zones.csv
```

### Available flags for FireIncidentMain
| Flag | Description |
|------|-------------|
| `--file <path>` | Path to the fire/fault events CSV |
| `--zones <path>` | Path to the zone definitions CSV |
| `--host <ip>` | Scheduler host if not localhost |

### CSV File Formats

**Fire events CSV** (`src/fire_events.csv`):
```
Time, ZoneID, EventType, Severity, FaultType
00:00:00, 1, FIRE, HIGH, NONE
00:01:00, 2, FIRE, MODERATE, NONE
00:02:00, 1, DRONE_STUCK, 1, NONE
```

**Zone definitions CSV** (`src/zones.csv`):
```
ZoneID, xMin, xMax, yMin, yMax
1, 0, 14, 0, 14
2, 15, 29, 0, 14
3, 0, 14, 15, 29
4, 15, 29, 15, 29
```
Zone constraints: each zone must be a rectangle at least 3 cells wide and 3 cells tall.


## How to Run Tests

All tests use **JUnit 4**. They can be run from IntelliJ or the command line.

### IntelliJ (recommended)

#### Run all Iteration 5 tests at once
1. In the **Project** tool window, navigate to `tests/Iteration5/`.
2. Right-click the folder and select **Run 'All Tests'**.

#### Run a single test class
1. Open the desired test file (e.g. `BatteryTest.java`).
2. Click the green triangle in the gutter next to the class declaration.
3. Select **Run 'BatteryTest'**.

#### Run a single test method
- Click the green triangle next to the individual `@Test` method and select **Run**.

### Command Line
```bash
# Compile source and tests
javac -cp .;lib/junit-4.13.2.jar -d out/test src/*.java tests/Iteration5/*.java

# Run all Iteration 5 tests
java -cp out/test;lib/junit-4.13.2.jar;lib/hamcrest-core-1.3.jar \
     org.junit.runner.JUnitCore BatteryTest VariableZonesTest AgentCapacityTest
```
*(Use `:` instead of `;` as the classpath separator on macOS/Linux.)*

---

### Test Files — Iteration 5

#### `BatteryTest.java`
Tests battery drain and decommission behaviour in `DroneMachine`. Uses a stub `DroneCallback` and the package-private `DroneMachine(id, callback, initialBattery)` constructor to control starting charge.

| Test | What it checks |
|------|----------------|
| `initialBatteryIs100` | Fresh drone starts at 100% |
| `batteryDecreasesOnePerMovementStep` | Battery drops by 1 per grid cell moved |
| `batteryUpdateCallbackFiredDuringMovement` | `onBatteryUpdate` fires at least once per move |
| `batteryRestoredToFullAfterRefill` | Refill cycle restores 50% → 100% |
| `batteryNeverDropsBelowZero` | Floor is clamped at 0 |
| `droneDecommissionedWhenBatteryReachesZero` | State becomes DECOMMISSIONED; `onHardFault` fires |
| `softFaultDrainsFivePercentBattery` | DRONE_STUCK pause drains 4–6% over 10 s |

> **Note:** Tests involving movement take ~1 s per grid step. The soft-fault test waits ~12 s total. JUnit timeouts are set accordingly.

---

#### `VariableZonesTest.java`
Tests dynamic zone loading (`Scheduler.loadZonesFromFile`) and `Zone` coordinate helpers. File-based tests write temporary CSV files using JUnit's `TemporaryFolder` rule.

| Test | What it checks |
|------|----------------|
| `zoneCenterXCalculatedCorrectly` | `getCenterX()` returns the x midpoint |
| `zoneCenterYCalculatedCorrectly` | `getCenterY()` returns the y midpoint |
| `zoneBoundGettersReturnCorrectValues` | All four bound getters are accurate |
| `validZoneFileLoadsWithNoErrors` | 4-zone CSV loads with 0 errors and 4 zones registered |
| `zoneSmallerThan3x3IsRejected` | A 2-cell-wide zone produces a validation error |
| `duplicateZoneIdIsRejected` | Same zone ID used twice produces an error |
| `emptyZoneFileReturnsError` | Header-only file produces an error |

---

#### `AgentCapacityTest.java`
Tests water capacity limits and the refill-station mechanic in `Scheduler`. Drones are registered directly via the package-private `registerDroneForTest` helper; no UDP loop is started.

| Test | What it checks |
|------|----------------|
| `fullTankDroneDispatchedOnFireEvent` | Drone with 15 L goes ONROUTE immediately |
| `emptyTankDroneNotDispatched` | Drone with 0 L stays IDLE; fire stays queued |
| `secondFireForActiveZoneHeldInPendingQueue` | Second fire on same zone goes to pending queue |
| `droneRefillCompleteRestoresWaterAndSetsIdle` | Refill resets water to 15 L and state to IDLE |
| `pendingFireDispatchedAfterDroneRefill` | Queued fire is picked up when a drone refills |
| `zoneBecomesInactiveAfterMissionCompleted` | Zone is no longer blocked after `missionCompleted` |
| `partialMissionRequeuesRemainder` | 10 L drone on a 15 L fire re-queues the 5 L remainder |
  

## Authors
### 1. Aryan Kumar Singh (101299776)
  #### Iteration 1
  - Worked on the implementation of all 9 classes including the GUI
  - Worked on the Javadoc documentation
  #### Iteration 2
  - Improved GUI for clearly tracking of the fire and drone states with colour, animation, and a table.
  - Integrated a state machine design for scheduler and drones (i.e. classic switch case's implementation)
  - Worked on some Javadoc documentation
    
### 2. Kevin Abeykoon (101301971)
  #### Iteration 1
- Worked on the implementation of Drone State, DroneSubsystem, FireEvent, and Scheduler
- Worked on Javadoc documentation for all classes and README
#### Iteration 2
  - Wrote the Scheduler tests
  - Created the Sequence diagram depicting how the GUI retrieves data and redisplays, aswell as the general flow of the simulation
  - Since I did most of the Scheduler work for this iteration during the last iteration, there was not much more to do
  - In the last iteration, I created a multiple drone scheduling queue with "logic" as per the project specifications
  - Worked on some Javadoc documentation
#### Iteration 3
  - Made some Scheduler changes
  - Wrote the UDP testing
#### Iteration 4
  - Implemented the fault handling system in DroneSubsystem, FireIncidentSubsystem, and Scheduler
  - I majorily did this by adding to the state based system: adding states for hard fault, entering a soft fault, and recovering from a soft fault

### 3. Abdullah Khan (101305235)
  #### Iteration 1
  - Worked on the implementation of DroneSubsystem
  - Worked on UML Sequence Diagrams
  #### Iteration 2
  - Worked on DroneSubsystem Tests (for both iterations)
  - Made State Machine Diagram for DroneSubSystem
  #### Iteration 3
  - Reworked DroneSubsystem and Scheduler
  - Updated DroneSubsystem state machine diagram

### 4. Rayyan Kashif (101274266)
  #### Iteration 1
  - Worked on the implementation of FireEventSubsystem
  - Worked on UML Class Diagrams
  #### Iteration 2, 3, & 4
  Rest in Piece
  
