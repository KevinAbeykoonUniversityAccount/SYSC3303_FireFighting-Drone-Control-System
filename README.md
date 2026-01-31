# Firefighting Drone Simulation

## Overview
This project simulates a **firefighting system using autonomous drones** coordinated by a central scheduler. Fire incidents occur over a defined time from an events sheet, drones are dispatched based on priority, and fires may require multiple drones to fully extinguish.

## Main Systems

### FireIncidentSubsystem
- Reads fire events from an input file
- Triggers fire incidents at the correct simulation time
- Sends events to the `Scheduler`

### Scheduler
- Central coordinator of the system
- Has 3 **priority queues** (`HIGH`, `MODERATE`, `LOW`)
- Assigns fire missions to drones
- Reschedules fires if not fully extinguished
- Handles thread synchronization 

### DroneSubsystem
- Each drone runs in its own thread
- Requests missions from the scheduler
- Travels to fire zones
- Extinguishes fires using limited water (15L)
- Refills when empty
- Can partially extinguish fires

### SimulationClock
- Provides a shared notion of simulation time
- Keeps all subsystems synchronized

### FireEvent
- Represents a fire incident
- Tracks severity, required water, and remaining fire


## Key Features

- Priority-based fire scheduling
- Multi-drone coordination
- Partial fire extinguishing with rescheduling
- Centralized thread synchronization
- Simulated time progression
- Water capacity constraints and refilling


## How to Run

1. Run the Main class
2. Observe simulation output in the console
3. Kill the threads manually after it has finished


## Authors
1. Aryan Kumar Singh (101299776)
- Worked on the implementation of all 9 classes including the GUI

2. Kevin Abeykoon (101301971)
- Worked on the implementation of Drone State, DroneSubsystem, FireEvent, and Scheduler
- Worked on Javadoc documentation for all classes and README
   
3. Rayyan Kashif (101274266)
- Worked on the implementation of FireEventSubsystem
- Worked on UML Class Diagrams
  
4. Abdullah Khan (101305235)
- Worked on the implementation of DroneSubsystem
- Worked on UML Sequence Diagrams
