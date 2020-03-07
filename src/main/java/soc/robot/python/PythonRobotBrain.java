package soc.robot.python;

import soc.game.SOCGame;
import soc.message.SOCMessage;
import soc.robot.*;
import soc.robot.general.*;
import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;

public class PythonRobotBrain extends SwynfelRobotBrainInterface {

	protected String modelname;
	protected Socket socket;
	protected DataOutputStream input;
	protected BufferedReader output;
	private boolean pythonLoaded = false;
	
	public PythonRobotBrain(SwynfelRobotClientInterface rc, SOCRobotParameters params, SOCGame ga,
							CappedQueue<SOCMessage> mq, String m) {
		super(rc, params, ga, mq);
		modelname = m;
		loadPython();
	}
	
	private int loadPython() {
		try {
			socket = new Socket("localhost", 4040);
			output = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			input = new DataOutputStream(socket.getOutputStream());
			pythonLoaded = true;
			System.out.println("[Python Interface] Python loaded");
			return 0;
		} catch (IOException e) {
			System.out.println(e);
			pythonLoaded = false;
		}
		return -1;
	}

	protected void close() {
		try {
			input.close();
			output.close();
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	@Override
	protected void setStrategyFields() {
		decisionMaker = new VoidRobotDM(this);
		negotiator = new VoidRobotNegotiator(this);
		discardStrategy = new RandomDiscardStrategy(game, ourPlayerData, this, rand);
		monopolyStrategy = new VoidMonopolyStrategy(game, ourPlayerData);
		openingBuildStrategy = new PythonOpeningBuildStrategy(game, ourPlayerData, this, utils);
		robberStrategy = new RandomRobberStrategy(game, ourPlayerData, this, rand);
	}

	@Override
	protected void clean() {
		super.clean();
		close();
	}

	@Override
	protected int chooseAction(boolean[] possibleActions) {
		return Integer.parseInt(
				ask("play",
						stateToString(utils.getState()) + ";"
								+ actionsToString(possibleActions) + ";"
								+ ourPlayerData.getTotalVP()));
	}

	@Override
	protected boolean playKightBeforeDie() {
		return false;
	}

	@Override
	protected void finished() {
		super.finished();
		ask("finish",stateToString(utils.getState()) + ";" + ourPlayerData.getTotalVP());
	}

	public static String stateToString(int[] state) {
		StringBuilder result = new StringBuilder();
		boolean first = true;
		for(int b : state){
			if(first){
				first = false;
			} else {
				result.append(',');
			}
			result.append(b);
		}
		return result.toString();
	}

	public static String actionsToString(boolean[] actions) {
		StringBuilder result = new StringBuilder();
		for(boolean b : actions){
			result.append(b ? 'T' : 'F');
		}
		return result.toString();
	}
	
	protected String ask(String function, String arguments) {
		if(!pythonLoaded) {
			int exit=loadPython();
			if(exit < 0) {
				return "";
			}
		}
		try{
			 input.writeBytes(function + ":" + arguments + "|");
			 input.flush();
			 return output.readLine();
		} catch(Exception e) {
			System.out.println(e);
		}
		return "";
	}
}
