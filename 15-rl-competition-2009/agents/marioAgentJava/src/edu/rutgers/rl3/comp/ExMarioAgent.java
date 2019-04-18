/**
 * Copyright John Asmuth and Rutgers University 2009, all rights reserved.
 */

package edu.rutgers.rl3.comp;

import org.rlcommunity.rlglue.codec.AgentInterface;
import org.rlcommunity.rlglue.codec.types.Action;
import org.rlcommunity.rlglue.codec.types.Observation;
import org.rlcommunity.rlglue.codec.util.AgentLoader;

import java.util.Date;
import java.util.Vector;
import java.util.Random;
import java.io.*;
import java.util.*;
import java.util.Hashtable;

/**
 * A simple agent that:
 * - never goes to the left
 * - mostly goes to the right
 * - tends to jump when under coins
 * - jumps if it cannot walk to the right due to a block
 * - tends to jump when there is a monster nearby
 * - tends to jump when there is a pit nearby
 * - tends to run when there is nothing nearby
 *
 * Also, it will remember the last trial, and repeat it exactly except
 * for the last 7 steps (assuming there are at least 7 steps).
 *
 * @author jasmuth
 *
 */
public class ExMarioAgent implements AgentInterface {

	private int NUMBER_OF_STATES = 8;
	private int NUMBER_OF_ACTIONS = 10;
/*
	States, Actions, Rewards
*/
	private ArrayList<Integer> state_vector;
	private ArrayList<Integer> action_vector;
	private ArrayList<Double> reward_vector;
	private int[][] policy_table;
	private double total_reward;
	private double episode_reward;
	private int episode_num;

	Map<Integer, Integer> findActionCol;

	private PrintWriter debug_out;

	private Boolean[] cur_state;

	private int getIndexOfReward(boolean[] states){
        String gen_string = "";
        for (boolean s : states){
            gen_string += s ? "1" : "0";
        }
//        System.out.println("[DEBUG]: gen_string: " + gen_string);
        return Integer.parseInt(gen_string, 2);
	}

	private void initializeStateRewards(){
	    int rows = (int) Math.pow(2, NUMBER_OF_STATES);
	    int cols = NUMBER_OF_ACTIONS;
	    policy_table = new int[rows][NUMBER_OF_ACTIONS];
	    for (int i = 0; i < rows; i++){
	        for (int j = 0; j < cols; j++){
	            policy_table[i][j] = 0;
	        }
	    }
	}

	/*
	 * Returns the char representing the tile at the given location.
	 * If unknown, returns '\0'.
	 *
	 * Valid tiles:
	 * M - the tile mario is currently on. there is no tile for a monster.
	 * $ - a coin
	 * b - a smashable brick
	 * ? - a question block
	 * | - a pipe. gets its own tile because often there are pirahna plants
	 *     in them
	 * ! - the finish line
	 * And an integer in [1,7] is a 3 bit binary flag
	 *  first bit is "cannot go through this tile from above"
	 *  second bit is "cannot go through this tile from below"
	 *  third bit is "cannot go through this tile from either side"
	 *
	 * @param x
	 * @param y
	 * @param obs
	 * @return
	 */
	public static char getTileAt(double xf, double yf, Observation obs) {
		int x = (int)xf;
		if (x<0)
			return '7';
		int y = 16-(int)yf;
		x -= obs.intArray[0];
		if (x<0 || x>21 || y<0 || y>15)
			return '\0';
		int index = y*22+x;
		return obs.charArray[index];
	}

	/**
	 * All you need to know about a monster.
	 *
	 * @author jasmuth
	 *
	 */
	static class Monster {
		double x;
		double y;
		/**
		 * The instantaneous change in x per step
		 */
		double sx;
		/**
		 * The instantaneous change in y per step
		 */
		double sy;
		/**
		 * The monster type
		 * 0 - Mario
		 * 1 - Red Koopa
		 * 2 - Green Koopa
		 * 3 - Goomba
		 * 4 - Spikey
		 * 5 - Pirahna Plant
		 * 6 - Mushroom
		 * 7 - Fire Flower
		 * 8 - Fireball
		 * 9 - Shell
		 * 10 - Big Mario
		 * 11 - Fiery Mario
		 */
		int type;
		/**
		 * A human recognizable title for the monster
		 */
		String typeName;
		/**
		 * Winged monsters bounce up and down
		 */
		boolean winged;
	}

	/**
	 * Gets all the monsters from the observation. Mario is included in this list.
	 *
	 * @param obs
	 * @return
	 */
	public static Monster[] getMonsters(Observation obs) {
		Vector<Monster> monster_vec = new Vector<Monster>();
		for (int i=0; 1+2*i<obs.intArray.length; i++) {
			Monster m = new Monster();
			m.type = obs.intArray[1+2*i];
			m.winged = obs.intArray[2+2*i]!=0;
			switch (m.type) {
			case 0:
				m.typeName = "Mario";
				break;
			case 1:
				m.typeName = "Red Koopa";
				break;
			case 2:
				m.typeName = "Green Koopa";
				break;
			case 3:
				m.typeName = "Goomba";
				break;
			case 4:
				m.typeName = "Spikey";
				break;
			case 5:
				m.typeName = "Piranha Plant";
				break;
			case 6:
				m.typeName = "Mushroom";
				break;
			case 7:
				m.typeName = "Fire Flower";
				break;
			case 8:
				m.typeName = "Fireball";
				break;
			case 9:
				m.typeName = "Shell";
				break;
			case 10:
				m.typeName = "Big Mario";
				break;
			case 11:
				m.typeName = "Fiery Mario";
				break;
			}
			m.x = obs.doubleArray[4*i];
			m.y = obs.doubleArray[4*i+1];
			m.sx = obs.doubleArray[4*i+2];
			m.sy = obs.doubleArray[4*i+3];
			monster_vec.add(m);
		}
		return monster_vec.toArray(new Monster[0]);
	}
	/**
	 * Gets just mario's information.
	 *
	 * @param obs
	 * @return
	 */
	public static Monster getMario(Observation obs) {
		Monster[] monsters = getMonsters(obs);
		for (Monster m : monsters) {
			if (m.type == 0 || m.type == 10 || m.type == 11)
				return m;
		}
		return null;
	}

	Random rand;
	/**
	 * When this is true, Mario is pausing for some number of steps
	 */
	boolean walk_hesitating;
	/**
	 * How many steps since the beginning of this trial
	 */
	int step_number;
	/**
	 * How many steps since the beginning of this run
	 */
	int total_steps;
	/**
	 * The time that the current trial began
	 */
	long trial_start;

	/**
	 * The sequence of actions taken during the last trial
	 */
	Vector<Action> last_actions;
	/**
	 * The sequence of actions taken so far during the current trial
	 */
	Vector<Action> this_actions;

	int actionNum;

	int convertForFindActionCol(int direction, int jump, int speed){
		 int firstDigit = (direction + 1) * 100;
		 int secondDigit = jump * 10;
		 int thirdDigit = speed;
		 return firstDigit + secondDigit + thirdDigit;
	}

	ExMarioAgent() {
		state_vector = new ArrayList<Integer>();
		action_vector = new ArrayList<Integer>();
		reward_vector = new ArrayList<Double>();
		cur_state = new Boolean[8];
		rand = new Random(new java.util.Date().getTime());
		last_actions = new Vector<Action>();
		this_actions = new Vector<Action>();

		findActionCol = new Hashtable<Integer, Integer>();

		int counter = 0;
		for (int dir = -1; dir < 2; dir++){
			 for (int jum = 0; jum < 2; jum++){
				 	for (int spe = 0; spe < 2; spe++){
						findActionCol.put(convertForFindActionCol(dir, jum, spe), counter);
						counter++;
					}
			 }
		}

		//
		// findActionCol.put(convertForFindActionCol(1, 0, 1), 1);
		// findActionCol.put(convertForFindActionCol(-1, 0, 0), 2);
		// findActionCol.put(convertForFindActionCol(-1, 0, 1), 3);
		// findActionCol.put(convertForFindActionCol(1, 1, 0), 4);
		// findActionCol.put(convertForFindActionCol(-1, 1, 0), 5);
		// findActionCol.put(convertForFindActionCol(0, 1, 0), 6);
		// findActionCol.put(convertForFindActionCol(0, 0, 0), 7);
		// findActionCol.put(convertForFindActionCol(1, 1, 1), 8);
		// findActionCol.put(convertForFindActionCol(-1, 1, 1), 9);

		initializeStateRewards();

//        boolean[] t = new boolean[]{true, false, true, false, true, false};
//		System.out.println("Index of reward test: " + getIndexOfReward(t));

	    actionNum = 0;
        total_reward = 0;
        episode_num = 0;

        try {
            debug_out = new PrintWriter("debug_log.json", "UTF-8");
        } catch (Exception e){
            System.out.println("File not found.");
            return;
        }
        debug_out.println("{\n\t\"episodes\":[");
	}

	public void agent_init(String task) {
		total_steps = 0;
	}

	public void agent_cleanup() {
	    debug_out.println("\n]}");
        debug_out.close();
        System.out.println("Check debug_log.json for output.");
	}

	public Action agent_start(Observation o) {
		ArrayList episode = new ArrayList();

		episode_reward = 0;

		trial_start = new Date().getTime();
		step_number = 0;
		if (episode_num != 1){
		debug_out.println(",");
		}


		debug_out.println("\t{");
		debug_out.println("\t\t\"episode\": " + episode_num + ",");
		debug_out.println("\t\t\"steps\":[");

		debug_out.println("\t\t{");
        debug_out.println("\t\t\t\"step_number\": " + step_number + ",");
		debug_out.println("\t\t\t\"total_steps\": " + total_steps+ ",");
		debug_out.println("\t\t\t\"total_reward\": " + total_reward + ",");
		debug_out.println("\t\t\t\"actions_to_perform\": {");
		Action a = getAction(o);
		debug_out.println("\t\t\t}");
        debug_out.print("\t\t}");
		episode_num++;
		return a;
	}

	public Action agent_step(double r, Observation o) {
        debug_out.println(",");

		step_number++;
		total_steps++;
		// transition function
		total_reward += r;
		episode_reward += r;


		debug_out.println("\t\t{");

		debug_out.println("\t\t\t\"step_number\": " + step_number + ",");
		debug_out.println("\t\t\t\"delta_reward\": " + r + ",");
		debug_out.println("\t\t\t\"episode_reward\": " + episode_reward + ",");
		debug_out.println("\t\t\t\"total_reward\": " + total_reward + ",");
		debug_out.println("\t\t\t\"actions_to_perform\": {");

    Action a = getAction(o);
		action_vector.add(findActionCol.get(convertForFindActionCol(a.intArray[0], a.intArray[1], a.intArray[2])));

    debug_out.println("\t\t\t},");
		//debug_out.println("\t\t\t\"a.intArray\":" + a.intArray.toString());
		debug_out.println("\t\t\t\"action_vector_num\": " + findActionCol.get(convertForFindActionCol(a.intArray[0], a.intArray[1], a.intArray[2])));
    debug_out.print("\t\t}");

		return a;
	}

	public void agent_end(double r) {
		long time_passed = new Date().getTime()-trial_start;
		if (this_actions.size() > 7) {
			last_actions = this_actions;
			last_actions.setSize(last_actions.size()-7);
		}
		else
			last_actions = new Vector<Action>();
		this_actions = new Vector<Action>();

		if (cur_state[WIN] == false)
			cur_state[DEAD] = true;
//		Enumeration e = last_actions.elements();

//		while (last_actions.hasMoreElements()){
//		    System.out.println("last_actions: " + last_actions.nextElement());
//		}


//		System.out.println("ended after "+total_steps+" total steps");
//		System.out.println("average "+1000.0*step_number/time_passed+" steps per second");

        debug_out.println("\n\t\t],");
				debug_out.println("\t\t\t\t\"cur_state[WIN]\": " + cur_state[WIN] + ",");
				debug_out.println("\t\t\t\t\"cur_state[DEAD]\": " + cur_state[DEAD] + ",");
        debug_out.println("\t\t\"episode_reward\": " + episode_reward + ",");
        debug_out.println("\t\t\"total_reward\": " + total_reward+ ",");
        debug_out.println("\t\t\"episode_steps\": " + step_number + ",");
        debug_out.println("\t\t\"total_steps\": " + total_steps + ",");
        debug_out.println("\t\t\"steps_per_second\": " + (1000.0*step_number/time_passed));
        debug_out.print("\t}");


	}

	public String agent_message(String msg) {
		System.out.println("message asked:"+msg);
		return null;
	}

	private static final int MONSTER = 0;
	private static final int PIT = 1;
	private static final int PIPE = 2;
	private static final int BREAKABLE_BLOCK = 3;
	private static final int QUESTION_BLOCK = 4;
	private static final int BONUS_ITEM = 5;
	private static final int WIN = 6;
	private static final int DEAD = 7;
// States: tiles
// states: monster, pit, pipe, regular block, unbreakable block, bonus items
	Action getAction(Observation o) {
//	The current state
		for (int state = 0; state < cur_state.length; state++)
			cur_state[state] = false;

		if (last_actions.size() > step_number) {
			Action act = last_actions.get(step_number);
			this_actions.add(act);

			debug_out.println("\t\t\t\t\"direction_looking\": " + act.intArray[0] + ",");
			debug_out.println("\t\t\t\t\"speed\": " + act.intArray[2] + ",");
			debug_out.println("\t\t\t\t\"will_jump\": " + (act.intArray[1] == 1 ? true : false));
			return act;
		}

	    actionNum++;
		Monster mario = ExMarioAgent.getMario(o);
		Monster[] monsters = ExMarioAgent.getMonsters(o);

		/*
		 * sometimes jump for no reason at all. at the end of this function,
		 * the value of this variable will be compared against a random number
		 * to see if mario jumps
		 */
		double jump_hesitation = .95;

		// if mario finishes the level
		if (ExMarioAgent.getTileAt(mario.x, mario.y, o) == '!')
			cur_state[WIN] = true;

		/*
		 * Check the blocks in the area to mario's upper right
		 */
		for (int up=0; up<5; up++) {
			for (int right = 0; right<7; right++) {
				char tile = ExMarioAgent.getTileAt(mario.x+right, mario.y+up, o);

				if (tile == 'b') {
					cur_state[BREAKABLE_BLOCK] = true;
					// tend to jump more if there is a block closer
					jump_hesitation *= 1.0*right/7;
				}
				else if (tile == '?') {
					cur_state[QUESTION_BLOCK] = true;
					// tend to jump more if there is a block closer
					jump_hesitation *= 1.0*right/7;
				}
				else if (tile == '$') {
					cur_state[BONUS_ITEM] = true;
					// jump often if there is a coin
					jump_hesitation *= .7;
				}
			}
		}

		/*
		 * Search for a pit in front of mario.
		 */
		boolean is_pit = false;
		for (int right = 0; !is_pit && right<3; right++) {
			boolean pit_col = true;
			for (int down=0; pit_col && mario.y-down>=0; down++) {
				char tile = ExMarioAgent.getTileAt(mario.x+right, mario.y-down, o);
				if (tile != ' ' && tile != 'M' && tile != '\0')
					pit_col = false;
			}
			if (pit_col)
				is_pit = true;
		}

		if (is_pit) {
			// always jump if there is a pit
			cur_state[PIT] = true;
			jump_hesitation = 0;
		}

		/*
		 * Search for a pit in front of mario.
		 */
		boolean is_pipe = false;
		for (int right = 0; !is_pipe && right<3; right++) {
			char tile = ExMarioAgent.getTileAt(mario.x+right, mario.y, o);
			if (tile == '|')
				cur_state[PIPE] = true;
				// always jump if there is a pit
				jump_hesitation = 0;
				break;
		}

		/*
		 * Look for nearby monsters by checking the positions against mario's
		 */
		boolean monster_near = false;
		for (Monster m : monsters) {
			if (m.type == 0 || m.type == 10 || m.type == 11) {
				// m is mario
				continue;
			}
			double dx = m.x-mario.x;
			double dy = m.y-mario.y;
			if (dx > -1 && dx < 10 && dy > -4 && dy < 4) {
				/* the more monsters and the closer they are, the more likely
				 * mario is to jump.
				 */
				jump_hesitation *= (dx+2)/12;
				monster_near = true;
				cur_state[MONSTER] = true;
			}
		}

		// hold down the jump button while in the air sometimes, to jump higher
		if (mario.sy > .1)
			jump_hesitation *= .5;

		// Sometimes hesitate if there is a monster near.
		if (walk_hesitating) {
			if (!monster_near || rand.nextDouble() > .8)
				walk_hesitating = false;
			else if (rand.nextDouble() > .9)
				walk_hesitating = false;
		}
		else if (monster_near && rand.nextDouble() > .8) {
			walk_hesitating = true;
		}
		// sometimes hesitate even if there isn't one
		else if (rand.nextDouble() > .9)
			walk_hesitating = true;

		Action act = new Action(8, 0);


		// -1, 0, 1 for direction, 1 is to the right
		act.intArray[0] = walk_hesitating?0:1;

		// 0, 1 for jump
		double jump_rng = rand.nextDouble();
		act.intArray[1] = jump_rng>jump_hesitation?1:0;

		// 0, 1 for speed
		act.intArray[2] = (is_pit||!monster_near)?1:0;//rand.nextBoolean()?1:0;

/*
//	print out the current state
		for (Boolean b : cur_state) {
			if (b == true)
				debug_out.println("t");
			else
				debug_out.println("f");
		}
*/

		//add the action to the trajectory being recorded, so it can be reused next trial
		this_actions.add(act);

		debug_out.println("\t\t\t\t\"direction_looking\": " + act.intArray[0] + ",");

		debug_out.println("\t\t\t\t\"will_jump\": " + act.intArray[1] + ",");
debug_out.println("\t\t\t\t\"speed\": " + act.intArray[2] + ",");
		debug_out.println("\t\t\t\t\"walk_hesitating\": " + walk_hesitating + ",");
		debug_out.println("\t\t\t\t\"monster_near\": " + (monster_near ? true : false) + ",");
		debug_out.println("\t\t\t\t\"is_pit\": " + (is_pit ? true : false) + ",");
		debug_out.println("\t\t\t\t\"jump_rng\": " + jump_rng + ",");
		debug_out.println("\t\t\t\t\"jump_hesitation\": " + jump_hesitation + ",");
		debug_out.println("\t\t\t\t\"cur_state[MONSTER]\": " + cur_state[MONSTER] + ",");
		debug_out.println("\t\t\t\t\"cur_state[PIT]\": " + cur_state[PIT] + ",");
		debug_out.println("\t\t\t\t\"cur_state[PIPE]\": " + cur_state[PIPE] + ",");
		debug_out.println("\t\t\t\t\"cur_state[BREAKABLE_BLOCK]\": " + cur_state[BREAKABLE_BLOCK] + ",");
		debug_out.println("\t\t\t\t\"cur_state[QUESTION_BLOCK]\": " + cur_state[QUESTION_BLOCK] + ",");
		debug_out.println("\t\t\t\t\"cur_state[BONUS_ITEM]\": " + cur_state[BONUS_ITEM]);

		return act;
	}

	public static void main(String[] args) {
		new AgentLoader(new ExMarioAgent()).run();
	}
}
