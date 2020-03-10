package soc.robot.general;

import soc.game.*;

import java.util.Arrays;
import java.util.List;
import java.util.Vector;

public class OldUtils {

	private SOCPlayer playerData;
	private SOCGame game;

	private boolean cachedStatic = false;
	private int[] staticFaces = null;
	private int[] staticVertices = null;

	public final Format format;

	public OldUtils(Format format, SOCPlayer playerData, SOCGame game) {
		this.playerData = playerData;
		this.game = game;
		this.format = format;
	}
	
	private int[] currentState;
	private int currentVP;
	
	private boolean[] possibleActions;
	
	public boolean[] getActions() {
		return possibleActions;
	}
	
	public int[] getState() {
		return currentState;
	}
	
	private final static int WINNING_REWARD = 10;
	
	public void newState(boolean[] newPossibleActions) {
		int[] newState = encodeState(game, playerData);
		int newVP = playerData.getTotalVP();

		int reward = newVP - currentVP +
				(newVP >= 10 ? WINNING_REWARD : 0); //The winning player is always the only one with 10 points or more

		currentState = newState;
		possibleActions = newPossibleActions;
		currentVP = newVP;
	}
	
	public void endState() {
		if(game.getCurrentPlayerNumber() == playerData.getPlayerNumber()) { // HACK
			// If it ended while it was my turn, it means I am supposed to have won
			if(playerData.getTotalVP() < game.vp_winner) { // If it wasn't registered yet
				game.setGameState(SOCGame.OVER);
				playerData.forceFinalVP(game.vp_winner); // Force it
			}
		}
		newState(emptyActions());
	}
	
	/********************************************************************************************************/

	private static String compress(int[] array) {
		return Arrays.toString(array);
	}

	private static int[] uncompressint(String s) {
		String[] values = s.split(",");
		int[] result = new int[values.length];
		for (int i = 0; i < values.length; i++){
			result[i] = Integer.parseInt(values[i]);
		}
		return result;
	}

	private static String compress(boolean[] array) {
		StringBuffer stringbuffer = new StringBuffer(array.length);
		for(boolean b : array) {
			stringbuffer.append(b ? "1" : "0");
		}
		return new String(stringbuffer);
	}
	
	private static boolean[] uncompressBool(String s) {
		boolean[] bools = new boolean[s.length()];
		for(int i = 0; i < s.length(); i++) {
			bools[i] = (s.charAt(i)!='0');
		}
		return bools;
	}

	private static int[] combine(int[][] arrays) {
		int totalSize = 0;
		for(int[] a : arrays) {
			totalSize += a.length;
		}

		int[] result = new int[totalSize];

		int index = 0;
		for(int[] a : arrays) {
			for(int e : a) {
				result[index] = e;
				index++;
			}
		}
		return result;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////
	// STATE                                                                                            //
	//////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public int[] encodeState(SOCGame _game, SOCPlayer player){
		if(!cachedStatic) {
			if (format.includeStatic) {
				cacheStaticBoard(_game.getBoard());
			} else {
				staticFaces = new int[0];
				cachedStatic = true;
			}
		}

		int[] faces = encodeFaces(_game.getBoard(), player, staticFaces);
		int[] edges = encodeEdges(_game.getBoard(), player);
		int[] vertices = encodeVertices(_game.getBoard(), player, staticVertices);

		int[] extra = encodeDynamicExtra(_game, player);
		return combine(new int[][]{faces, edges, vertices, extra});
	}

	public static int find(int[] array, int element) throws Exception {
		for (int i = 0; i < array.length; i++) {
			if(element == array[i]) {
				return i;
			}
		}
		throw new Exception(element + " not in " + Arrays.toString(array));
	}

	public void cacheStaticBoard(SOCBoard board){

		// FACES
		staticFaces = new int[2*format.faceCount];
		int[] hexes = board.getHexLayout();
		int[] numbers = board.getNumberLayout();
		int index = 0;
		for(int face : format.faces) {
			staticFaces[index] = hexes[face];  // Type : Water 0 / Resources 1-5 / Desert 6
			staticFaces[index + 1] = numbers[face];  // Dice roll, or 0
			index += 2;
		}

		// VERTICES
		staticVertices = new int[format.vertexCount];

		for(int portType = 0; portType < 6; portType++) {
			List<Integer> portVertices = board.getPortCoordinates(0);
			int value = portType;
			if (portType == 0) {
				value = 6;  // So MISC_PORT is 6 instead of 0, the empty value
			}
			for (int vertex : portVertices) {
				int id = 0;
				try {
					id = find(format.vertices, vertex);
				} catch (Exception e) {
					e.printStackTrace();
				}
				staticVertices[id] = value;
			}
		}
		cachedStatic = true;
	}

	protected int encodeOwner(SOCPlayingPiece piece, SOCPlayer player) {
		if(piece == null) {
			return 0;
		}
		if (player == piece.getPlayer()) {
			return 1;
		}
		return -1; //Swynfel TODO: Different negative id based on player's position (depending on ours) 
	}

	public int[] encodeFaces(SOCBoard board, SOCPlayer player, int[] staticFaces) {
		if(staticFaces.length == format.faceStates) {
			return staticFaces;
		}

		int[] faces = new int[format.faceStates];

		int thiefHex = format.fThief ? board.getRobberHex() : 0;

		int index = 0;
		for(int node : format.faces) {
			int outIndex = format.faceStateMultiplicity * index;
			if(format.includeStatic) {
				faces[outIndex] = staticFaces[2 * index];
				faces[outIndex + 1] = staticFaces[2 * index + 1];
				outIndex += 2;
			}
			if(format.fThief) {
				faces[outIndex] = node == thiefHex ? 1 : 0;
				//outIndex ++;
			}
			index++;
		}
		return faces;
	}

	public int[] encodeEdges(SOCBoard board, SOCPlayer player){
		int[] edges = new int[format.edgeStates]; // = 72

		int index = 0;
		for(int road : format.edges) {
			SOCRoutePiece piece = board.roadOrShipAtEdge(road);
			int value = encodeOwner(piece, player);
			edges[index] = value;
			index++;
		}

		return edges;
	}

	public int[] encodeVertices(SOCBoard board, SOCPlayer player, int[] staticVertices){
		int[] vertices = new int[format.vertexStates]; // = 2 * 54

		int index = 0;
		for(int node : format.vertices) {
			SOCPlayingPiece piece = board.settlementAtNode(node);
			int value = encodeOwner(piece, player);
			if ( piece != null && piece.getType() != SOCPlayingPiece.SETTLEMENT) {
				value *= 2;
			}
			if(format.includeStatic) {
				vertices[2 * index] = value;
				vertices[2 * index + 1] = staticVertices[index];
			} else {
				vertices[index] = value;
			}
			index++;
		}
		return vertices;
	}
	
	public int[] encodeDynamicExtra(SOCGame game, SOCPlayer player){

		int[] resources = player.getResources().getAmounts(false);
		
		int[] extra = new int[format.extraStates];

		int index;

		for(index = 0; index < resources.length; index ++) {
			extra[index] = resources[index];
		}

		if(format.xMyVp){
			extra[index] = player.getTotalVP();
			index ++;
		}

		if(format.xGblDvp){
			extra[index] = game.getNumDevCards();
			index ++;
		}

		if(format.xMyPieces){
			extra[index] = player.getNumPieces(SOCPlayingPiece.ROAD);
			extra[index + 1] = player.getNumPieces(SOCPlayingPiece.SETTLEMENT);
			extra[index + 2] = player.getNumPieces(SOCPlayingPiece.CITY);
			index += 3;
		}

		if(format.xWhoLongestRoad){
			SOCPlayer p = game.getPlayerWithLongestRoad();
			extra[index] = p == null ? 0 : p.getPlayerNumber() == player.getPlayerNumber() ? 1 : -1;
			index ++;
		}

		if(format.xWhoLargestArmy){
			SOCPlayer p = game.getPlayerWithLargestArmy();
			extra[index] = p == null ? 0 : p.getPlayerNumber() == player.getPlayerNumber() ? 1 : -1;
			index ++;
		}

		assert index == format.extraStates;

		return extra;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////
	// ACTIONS                                                                                          //
	//////////////////////////////////////////////////////////////////////////////////////////////////////

	public boolean[] encodePossibleActions(SOCGame game, SOCPlayer player){
		
		boolean[] actions = new boolean[format.totalActions];
		
		SOCBoard board = game.getBoard();
		
		//Pass Turn
		
		actions[0] = true;
		int index = format.passActions;

		//Possible Roads

		boolean canPlaceRoad = player.getNumPieces(SOCPlayingPiece.ROAD) > 0 &&
				player.getResources().contains(SOCPlayingPiece.getResourcesToBuild(SOCPlayingPiece.ROAD));

		if(canPlaceRoad) {
			for(int road : format.edges) {
				actions[index] = player.isPotentialRoad(road);
				index++;
			}
		} else {
			index += format.edgeActions;
		}

		//Possible Settlements + Cities
		
		boolean canPlaceSettlement = player.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0 &&
				player.getResources().contains(SOCPlayingPiece.getResourcesToBuild(SOCPlayingPiece.SETTLEMENT));
		boolean canPlaceCity = player.getNumPieces(SOCPlayingPiece.CITY) > 0 &&
				player.getResources().contains(SOCPlayingPiece.getResourcesToBuild(SOCPlayingPiece.CITY));
		
		if(canPlaceCity || canPlaceSettlement) {
			
			Vector<Integer> currentSettlements = new Vector<Integer>();;
			
			if(canPlaceCity) {
				for (SOCSettlement settlement : player.getSettlements()) {
					currentSettlements.add(settlement.getCoordinates());
				}
			}
			for(int node : format.vertices) {
				// Settlement
				actions[index] = canPlaceSettlement && player.canPlaceSettlement(node);
				index++;
				// City
				actions[index] = canPlaceCity && currentSettlements.contains(node);
				index++;
			}
		} else {
			index += format.vertexActions;
		}
		
		//Possible Development Card

		assert format.extraActions == 21;

		boolean canBuyDevCard = game.couldBuyDevCard(player.getPlayerNumber());
		
		if(canBuyDevCard) {
			actions[index] = true;
		}
		index += 1;

		//Possible Bank Trades

		SOCResourceSet ourResources = player.getResources();

		for (int resourceType = SOCResourceConstants.CLAY; resourceType < SOCResourceConstants.UNKNOWN; resourceType++) {
			int needed = 4;
			if (player.getPortFlag(resourceType)) {
				needed = 2;
			} else if (player.getPortFlag(SOCBoard.MISC_PORT)) {
				needed = 3;
			}

			if (ourResources.getAmount(resourceType) >= needed) {
				for(int i = 0; i < 4 ; i ++) {
					actions[index] = true;
					index++;
				}
			} else {
				index += 4;
			}
		} // index += 5x4 = 20

		return actions;
	}

	public boolean[] encodePossibleRoadOpeningActions(SOCGame game, SOCPlayer player) {
		boolean[] actions = new boolean[format.totalActions];

		final int settlementNode = player.getLastSettlementCoord();
		Vector<Integer> possibleRoads = new Vector<Integer>();
		for (int edge : game.getBoard().getAdjacentEdgesToNode_arr(settlementNode)) {
			if (player.isLegalRoad(edge)) {
				possibleRoads.add(edge);
			}
		}
		int index = format.passActions;
		for(int road : format.edges) {
			if(possibleRoads.contains(road)) {
				actions[index] = true;
			}
			index++;
		}
		return actions;
	}

	public boolean[] encodePossibleSettlementOpeningActions(SOCGame game, SOCPlayer player) {
		boolean[] actions = new boolean[format.totalActions];

		int index = format.passActions + format.edgeActions;
		final int[] ourPotentialSettlements = player.getPotentialSettlements_arr();
		for(int node : format.vertices) {
			boolean canPlace = Arrays.stream(ourPotentialSettlements).anyMatch(x -> x == node);
			// Settlement
			actions[index] = canPlace;
			index++;
			// City
			actions[index] = false;
			index++;
		}
		return actions;
	}

	public boolean[] emptyActions() {
		return new boolean[format.totalActions];
	}

	public static class SOCAction {
		public static final int PASS = -1; // =SOCPlayingPiece.MIN - 1;

		public static final int BUILD_ROAD = 1; // =SOCPlayingPiece.ROAD;

		public static final int BUILD_SETTLEMENT = 2; // =SOCPlayingPiece.SETTLEMENT;

		public static final int BUILD_CITY = 3; // =SOCPlayingPiece.CITY;

		public static final int BUY_DEVELOPMENT_CARD = 4;

		public static final int USE_DEVELOPMENT_CARD = 5;

		public static final int BANK_TRADE = 6;

		public Format format;
		public int id;
		public int type;
		public int[] parameters; //Not always used

		public SOCAction(Format format, SOCBoard board, int id) {
			this.format = format;
			this.id = id;

			//If pass turn action
			if(id == 0) {
				type = PASS;
				parameters = null;
				return;
			}
			id -= format.passActions;

			//If Road action
			if(id < format.edgeActions) {
				int i = 0;
				for(int road : format.edges) {
					if(i == id) {
						type = BUILD_ROAD;
						parameters = new int[] { road }; // Road position
						return;
					}
					i++;
				}
			}
			id -= format.edgeActions;

			//If Settlement + City action
			if(id < format.vertexActions) {
				int position = format.vertices[id / 2];
				parameters = new int[] { position }; // Settlement/City position
				type = (id % 2 == 0) ? BUILD_SETTLEMENT : BUILD_CITY;
				return;
			}
			id -= format.vertexActions;

			assert format.extraActions == 21;

			//If Buy development card Action
			if(id < 1) {
				type = BUY_DEVELOPMENT_CARD;
				parameters = null;
				return;
			}
			id -= 1;

			//Else if Buy development card Action
			//if(id < 20) {
			type = BANK_TRADE;
			int from = (id / 4) + 1;
			int to = (id % 4) + 1;
			if(to >= from) {
				to++;
			}
			parameters = new int[] { from, to }; // Resource trade
			//}
		}
	}
}
