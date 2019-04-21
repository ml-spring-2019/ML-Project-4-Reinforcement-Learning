# Project 4 - Mario

This program utilizes Monte Carlo Reinforcement Learning to allow the agent (Mario) to learn how to reach the goal in a platformer level.

# Quick Start

Navigate to `15-rl-competition-2009`. 
Ensure everything is installed by running `./install.bash`. 

Once everything is installed, let's run the program. Open 2 terminal windows.

#### In terminal 1 (agent terminal):
1. Navigate to `15-rl-competition-2009/agents/marioAgentJava`.
2. Run `make`.
3. Run `./run.bash`.

#### In terminal 2 (trainer terminal):
1. Navigate to `15-rl-competition-2009/trainers/consoleTrainerJava`.
2. Run `make`.
3. Run `./run.bash`.

The agent will begin training with the following configurations:
- `episodes: 0`
- `level seed: 0`
- `level type: 0`
- `level difficulty: 0`

Note: if the action `idle` is being preferred very often, I would recommend retrying. Otherwise it may take longer for the agent to converge towards `moving forward` actions.

Once the program finishes running, two files will be generated:
* `ref_export.json` - This file contains the following exported variables: 
    - `total_reward`
    - `policy_table`
    - `reward_table`
    - `policy_itr_table`
* `debug_log.json` - Contains variables and states used for debugging.

# Transfer Learning

When running **terminal 1 (agent terminal)** `./run.bash` again, the program will automatically read `ref_export.json` and carry on the knowledge from the previous run.  This is not dependent on the level seed, type, and difficulty; therefore, knowledge from a previous level can be tested on a new level. The file *must* be named `ref_export.json` in order for transfer learning to work.

To start from scratch, in **terminal 1 (agent terminal)** either
* rename `ref_export.json` or
* run `make clean`, then `make` and `./run.bash`

# States

The following states are implemented and utilized in the agent:
- `MONSTER_NEAR` - checks if an enemy is within 5 tiles in front and 1 tile behind Mario.
- `MONSTER_FAR` - checks if an enemy is within 3 - 11 tiles in front.
  * Note: the ranges of `MONSTER_NEAR` and `MONSTER_FAR` overlap. This creates a scenario where the monster is _moderately close_ to Mario without the need for another separate state.
- `MONSTER_ABOVE` - checks if an enemy has higher ground than Mario.
- `MONSTER_BELOW` - checks if an enemy has lower ground than Mario.
- `PIT_FAR` - checks if a pit is within 3 - 5 tiles in front from Mario.
- `PIT_NEAR` - checks if a pit is within 0 - 2 tiles in front from Mario.
- `QUESTION_BLOCK` - checks for an item block in an area 7 tiles in front and 5 tiles above Mario.
- `BONUS_ITEM_FAR` - checks if a Mushroom or Fire Flower is greater than 3 tiles in front or behind Mario.
- `BONUS_ITEM_NEAR` - checks if a Mushroom or Fire Flower is less than or equal to 3 tiles in front or behind Mario
- `COINS` - checks for coins in an area 7 tiles in front and 5 tiles above Mario.
- `BREAKABLE_BLOCK` - checks for a breakable block in an area 7 tiles in front and 5 tiles above Mario.
- `HIGHER_GROUND_NEAR` - checks if there is an impassible tile 3 - 6 tiles in front of Mario.
- `HIGHER_GROUND_FAR` - checks if there is an impassible tile 0 - 2 tiles in front of Mario.

# Policy

Mario will have a `25%` chance to explore. He will randomly choose actions in these three categories:
- `Moving`- backwards, forwards, or idle
- `Jumping` - to jump or not to jump...
- `Speed` - walking or running.
    * If idle is selected from `moving`, Mario will still be idle.

Otherwise, based on the states (his surroundings), Mario will choose and execute the combination of actions in the policy table that contains the highest reward.

# Modifying Configurations

In **terminal 2 (trainer terminal)**, open `15-rl-competition-2009/trainers/consoleTrainerJava/src/consoleTrainer.java`. The following may be changed to alter the level:
- `episodeCount: 0`
- In `consoleTrainerHelper.loadMario()`:
  - `level seed`
  - `level type`
  - `level difficulty`
  - `level instance`
  
# Agent Code

In **terminal 1 (agent terminal)**, the agent code can be found at `15-rl-competition-2009/agents/marioAgentJava/src/edu/rutgers/rl3/comp/ExMarioAgent.java`

# References

The Mario Project: https://eecs.wsu.edu/~taylorm/11EAAI/

