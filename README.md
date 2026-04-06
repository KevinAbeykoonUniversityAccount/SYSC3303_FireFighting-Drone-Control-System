# Firefighting Drone Simulation

## Overview
This project simulates a **firefighting system using autonomous drones** coordinated by a central scheduler. Fire incidents occur over a defined time from an events sheet, drones are dispatched based on priority, and fires may require multiple drones to fully extinguish.



## Main Systems

### FireIncidentSubsystem
- Listens on UDP port 6001 for a loadFile|<path> command at runtime
- Reads fire events from the specified CSV and dispatches them to the Scheduler at the correct simulation time
- Sends events to the `Scheduler`
  
### Scheduler
- Central coordinator; maintains 3 **priority queues** (`HIGH`, `MODERATE`, `LOW`)
- State machine: IDLE (no incidents) → DISPATCHING (assigning missions) → MONITORING (tracking active drones) → REFILLING / FAULT_HANDLING as needed.
- Assigns fire missions to the closest idle drone with sufficient water
- Reschedules partially extinguished fires and tracks per‑zone water remaining.
- Loads zone definitions from a CSV file at runtime; defaults to a 4-zone 2×2 grid

### DroneSubsystem / DroneMachine
- Drone subsystem relays requests from scheduler to specified or available drones.
- Each drone runs in its own thread with an explicit state machine: IDLE, ONROUTE, EXTINGUISHING, RETURNING, REFILLING_AND_RECHARGING, FAULTED, DECOMMISSIONED
- Travels cell-by-cell across the grid; battery drains 1% per step
- Extinguishes fire with nozzle open/close delays and configurable flow rate; water depletes
- Returns to base and refills (15 L water + 100% battery) when tank is empty
- Handles soft faults (DRONE_STUCK — 10 s pause, 5% battery drain) and hard faults (NOZZLE_FAULT — permanent decommission)
- Drones decommissions automatically if battery reaches 0%

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

- Priority-based fire scheduling
- Multi-drone coordination
- Partial fire extinguishing with rescheduling
- Centralized thread synchronization
- Simulated time progression
- Water capacity constraints and refilling


## How to Run Application

1. Run the Scheduler main class, which will open the GUI window. You can load zone
   and fire event files from the GUI, or use the terminal flags below.
      java -cp out/production/Project SchedulerMain

2. Registers drones (e.g. four with IDs 1–4) with the Drone Subsystem. Add or remove IDs to change swarm size.
      java -cp out/production/Project DroneMain 1 2 3 4

3. Start the Fire Incident Subsystem and either wait for input via GUI, or pass files directly.
      java -cp out/production/Project FireIncidentMain

      java -cp out/production/Project FireIncidentMain --file src/fire_events.csv --zones src/zones.csv

#### NOTE: Ensure the csv event file is in the src folder.



## How To Run Tests
All unit tests relevant to iteration 5 are located in the `test/Iteration 5/` directory. The tests verify the behavior of the Scheduler, DroneSubsystem, and FireEvent classes, including priority handling, partial mission assignment, and state transitions.

#### Run all tests in the `Iteration 5` package:
1. In the **Project** tool window, navigate to `test/Iteration 5`.
2. Right‑click on the package (or on any test class) and select **Run 'Tests in 'Iteration 2''** (or **Run All Tests**).

#### Run a single test class:
- Open the desired test class (e.g., `BatteryTest.java`).
- Click the green triangle in the gutter next to the class declaration or any individual test method, then select **Run 'SchedulerTest'**.
  

## Authors
### 1. Aryan Kumar Singh (101299776)
  #### Iteration 1
  - Worked on the implementation of all 9 classes including the GUI
  - Worked on the Javadoc documentation
  #### Iteration 2
  - Improved GUI for clearly tracking of the fire and drone states with colour, animation, and a table.
  - Integrated a state machine design for scheduler and drones (i.e. classic switch case's implementation)
  - Worked on some Javadoc documentation
  #### Iteration 3
  - Helped implement UDP communication between all three subsystems.
  - UML Class Diagram
  #### Iteration 4
  - Visualized hard and soft fault states and events on the GUI
  - Seperated drone system into a subsystem that receives UDP messages and relays its contents and individual
    drone machines that are receive orders from the subsystem and not the Scheduler directly.
  - UML Class Diagram
  #### Iteration 5
  - Added variable zone sizing but enabling user to input a file specifying rectangular zones of any number
    if they fulfilled the zone requirements.
  - Did tests on the dynamic zones and drone agent capacity
    
    
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
  
