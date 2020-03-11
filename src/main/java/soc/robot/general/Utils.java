package soc.robot.general;

import soc.game.*;
import soc.message.SOCBankResources;
import soc.message.SOCPlayerElement;

import java.io.FileReader;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;

import static soc.game.SOCResourceConstants.*;

public class Utils {

	private boolean started = false;
	private SwynfelRobotBrainInterface brain;
	public final Format format;
	private SOCPlayer playerData;
	private SOCGame game;

	public int[][][] board_state;
	public int[] flat_state;
	public boolean[] action_choices;

	private int[] bank_resources;


	public Utils(SwynfelRobotBrainInterface brain, Format format, SOCPlayer playerData, SOCGame game) {
		this.brain = brain;
		this.playerData = playerData;
		this.game = game;
		this.format = format;

		board_state = new int[format.width][format.height][13 + 2 * format.players];
		flat_state = new int[37 + 8 * (format.players-1)];
		bank_resources = new int[5];
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////
	// UTILS                                                                                            //
	//////////////////////////////////////////////////////////////////////////////////////////////////////

	private final static int WINNING_REWARD = 10;

    // CLAY = 1;
	// ORE = 2;
	// SHEEP = 3;
	// WHEAT = 4;
	// WOOD = 5;
	public static int rustResource(int jsettlersResource) {
		switch (jsettlersResource) {
			case CLAY:
				return 0;
			case WOOD:
				return 1;
			case ORE:
				return 2;
			case SHEEP:
				return 3;
			case WHEAT:
				return 4;
		}
		return 5;
	}
	public static int[] jsettlersResource = new int[] { CLAY, WOOD, ORE, SHEEP, WHEAT };


	public int jsettlersHarbor(int rustHarbor) {
		switch (rustHarbor) {
			case 0 : return  SOCBoard.CLAY_PORT;
			case 1 : return  SOCBoard.WOOD_PORT;
			case 2 : return  SOCBoard.ORE_PORT;
			case 3 : return  SOCBoard.WHEAT_PORT;
			case 4 : return  SOCBoard.SHEEP_PORT;
			case 5 : return  SOCBoard.MISC_PORT;
		}
		return 0;
	}

	public int getNumCardsFromInventory(SOCInventory inventory, int age, int rustDvp) {
		switch (rustDvp) {
			case 0:
				return inventory.getAmount(age, SOCDevCardConstants.KNIGHT);
			case 1:
				return inventory.getAmount(age, SOCDevCardConstants.ROADS);
			case 2:
				return inventory.getAmount(age, SOCDevCardConstants.DISC);
			case 3:
				return inventory.getAmount(age, SOCDevCardConstants.MONO);
			case 4:
				// TODO: Keep track of new vp-development cards so state matches rust environment perfectly
				return age==SOCInventory.NEW ? 0 : inventory.getNumVPItems();
		}
		assert false;
		return 0;
	}

	// none = -1;
	// me = 0;
	// next player = 1;
	// (next)^n player = n;
	protected int encodeOwner(SOCPlayingPiece piece, SOCPlayer player) {
		if(piece == null) {
			return -1;
		}
		int delta = piece.getPlayer().getPlayerNumber() - player.getPlayerNumber();
		if (delta < 0) {
			delta += format.players;
		}
		return delta;
	}

	public void handleBankResources(SOCBankResources msg) {
		for(int res = 0; res < 5; res++) {
			bank_resources[res] =  msg.rs.getAmount(jsettlersResource[res]);
		}
		/*System.out.println("[BANK RESOURCES] "
				+bank_resources[0]+" "
				+bank_resources[1]+" "
				+bank_resources[2]+" "
				+bank_resources[3]+" "
				+bank_resources[4]+" from "+msg);*/
	}
	
	public void newState() {
		if (!started) {
			started = true;
			fillStaticBoardState();
		}
		fillDynamicBoardState();
		fillFlatState();
	}
	
	public void endState() {
		if(game.getCurrentPlayerNumber() == playerData.getPlayerNumber()) { // HACK
			// If it ended while it was my turn, it means I am supposed to have won
			if(playerData.getTotalVP() < game.vp_winner) { // If it wasn't registered yet
				game.setGameState(SOCGame.OVER);
				playerData.forceFinalVP(game.vp_winner); // Force it
			}
		}
		newState();
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////
	// STATE                                                                                            //
	//////////////////////////////////////////////////////////////////////////////////////////////////////

	public void fillStaticBoardState() {
		SOCBoard board = game.getBoard();

		// --- hexes --- //
		int[] hexes = board.getHexLayout();
		int[] numbers = board.getNumberLayout();
		for(int i = 0; i < Format.HEXES.length; i++) {
			int face = format.faces[i];
			Format.Coord hex = Format.HEXES[i].with(format);
			board_state[hex.x][hex.y][rustResource(hexes[face])] = Integer.max(1, numbers[face]);
		}

		// --- intersections --- //
		for(int portType = 0; portType < 6; portType++) {
			List<Integer> portVertices = board.getPortCoordinates(0);
			int value = rustResource(portType);
			for (int vertex : portVertices) {
				int index = 0;
				try {
					index = find(format.vertices, vertex);
				} catch (Exception e) {
					e.printStackTrace();
				}
				Format.Coord intersection = Format.INTERSECTIONS[index].with(format);
				board_state[intersection.x][intersection.y][7+format.players+value] = 1;
			}
		}
	}

	// Only board value can be reset to zero, so we need to keep track of the previous one to clear it
	private Format.Coord lastThiefPosition = null;

	public void fillDynamicBoardState() {
		SOCBoard board = game.getBoard();

		// --- hexes --- //
		try {
			Format.Coord newThiefPosition = Format.HEXES[find(format.facesCoords, board.getRobberHex())].with(format);
			if (newThiefPosition != lastThiefPosition) {
				if (lastThiefPosition != null) {
					board_state[lastThiefPosition.x][lastThiefPosition.y][6] = 0;
				}
				board_state[newThiefPosition.x][newThiefPosition.y][6] = 1;
				lastThiefPosition = newThiefPosition;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		// --- paths --- //
		for(int i = 0; i < Format.PATHS.length; i++) {
			int road = format.edges[i];
			SOCRoutePiece piece = board.roadOrShipAtEdge(road);
			if (piece != null) {
				int owner = encodeOwner(piece, playerData);
				Format.Coord path = Format.PATHS[i].with(format);
				board_state[path.x][path.y][7 + owner] = 1;
			}
		}

		// --- intersections --- //
		for(int i = 0; i < Format.INTERSECTIONS.length; i++) {
			int node = format.vertices[i];
			SOCPlayingPiece piece = board.settlementAtNode(node);
			if (piece != null) {
				int owner = encodeOwner(piece, playerData);
				Format.Coord intersection = Format.INTERSECTIONS[i].with(format);
				int value = (piece.getType() == SOCPlayingPiece.SETTLEMENT) ? 1 : 2;
				board_state[intersection.x][intersection.y][13 + format.players + owner] = value;
			}
		}
	}

	public static int find(int[] array, int element) throws Exception {
		for (int i = 0; i < array.length; i++) {
			if(element == array[i]) {
				return i;
			}
		}
		throw new Exception(element + " not in " + Arrays.toString(array));
	}

	public void fillFlatState(){
		SOCPlayer longestRoad = game.getPlayerWithLongestRoad();
		SOCPlayer largestArmy = game.getPlayerWithLargestArmy();

		// --- player --- //

		int[] resources = playerData.getResources().getAmounts(false);
		SOCInventory inventory = playerData.getInventory();
		for(int r = 0; r < 5; r++) {
			flat_state[rustResource(r)] = resources[r];
		}
		flat_state[5] = playerData.getNumPieces(SOCPlayingPiece.ROAD);
		flat_state[6] = playerData.getNumPieces(SOCPlayingPiece.SETTLEMENT);
		flat_state[7] = playerData.getNumPieces(SOCPlayingPiece.CITY);
		flat_state[8] = playerData.getNumKnights();
		for(int d = 0; d < 5; d++) {
			flat_state[9+d] = getNumCardsFromInventory(inventory, SOCInventory.OLD, d);
		}
		for(int d = 0; d < 5; d++) {
			flat_state[14+d] = inventory.getAmount(SOCInventory.NEW, d);
		}
		for(int h = 0; h < 6; h++) {
			flat_state[19+h] = playerData.getPortFlag(jsettlersHarbor(h)) ? 1 : 0;
		}
		flat_state[25] = longestRoad == playerData ? 1 : 0;
		flat_state[26] = largestArmy == playerData ? 1 : 0;

		// --- opponents --- //
		for(int opp = 1; opp < format.players; opp++) {
			int c_player = 19 + opp * 8;
			SOCPlayer player = game.getPlayer((playerData.getPlayerNumber() + opp) % format.players);
			flat_state[c_player] = player.getResources().getTotal();
			flat_state[c_player+1] = player.getNumPieces(SOCPlayingPiece.ROAD);
			flat_state[c_player+2] = player.getNumPieces(SOCPlayingPiece.SETTLEMENT);
			flat_state[c_player+3] = player.getNumPieces(SOCPlayingPiece.CITY);
			flat_state[c_player+4] = player.getNumKnights();
			flat_state[c_player+5] = player.getInventory().getTotal();
			flat_state[c_player+6] = longestRoad == player ? 1 : 0;
			flat_state[c_player+7] = largestArmy == player ? 1 : 0;
		}

		// --- state --- //
		int c_state = 19 + 8 * format.players;
		for(int r = 0; r < 5; r++) {
			int in_bank = bank_resources[r];
			flat_state[c_state + r] = Integer.max(0, Integer.min(in_bank, 19));
			if(in_bank > 19) {
				System.out.println("[ERROR] bank has " + in_bank + " resources of type " + r + " which is more than 19, the maximum");
			}
			if(in_bank <= 0) {
				System.out.println("[REALLY?] bank has " + in_bank + " resources of type " + r + " which is strange");
			}
		}
		flat_state[c_state+5] = game.getNumDevCards();

		// --- phase --- //
		int c_phase = c_state + 6;
		flat_state[c_phase] = (game.getGameState() == SOCGame.ROLL_OR_CARD) ? 1 : 0;
		flat_state[c_phase+1] = playerData.hasPlayedDevCard() ? 0 : 1;
		flat_state[c_phase+2] = brain.expectPLACING_FREE_ROAD1 ? 2 : brain.expectPLACING_FREE_ROAD2 ? 1 : 0;
		flat_state[c_phase+3] = brain.expectWAITING_FOR_DISCOVERY ? 2 : 0;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////
	// ACTIONS                                                                                          //
	//////////////////////////////////////////////////////////////////////////////////////////////////////

	private void freshActions() {
		action_choices = new boolean[format.totalActions];
	}

	public void turnActions() {
		freshActions();

		int index = 0;

		// # BOARD
		// ## Hexes: MoveThief
		index += format.faceActions;

		// ## Paths: BuildRoad
		boolean canPlaceRoad = playerData.getNumPieces(SOCPlayingPiece.ROAD) > 0 &&
				playerData.getResources().contains(SOCPlayingPiece.getResourcesToBuild(SOCPlayingPiece.ROAD));

		if(canPlaceRoad) {
			for(int road : format.edges) {
				action_choices[index] = playerData.isPotentialRoad(road);
				index++;
			}
		} else {
			index += format.edgeActions;
		}

		// ## Intersections: BuildSettlement and BuildCity

		boolean canPlaceSettlement = playerData.getNumPieces(SOCPlayingPiece.SETTLEMENT) > 0 &&
				playerData.getResources().contains(SOCPlayingPiece.getResourcesToBuild(SOCPlayingPiece.SETTLEMENT));
		boolean canPlaceCity = playerData.getNumPieces(SOCPlayingPiece.CITY) > 0 &&
				playerData.getResources().contains(SOCPlayingPiece.getResourcesToBuild(SOCPlayingPiece.CITY));

		if(canPlaceCity || canPlaceSettlement) {

			Vector<Integer> currentSettlements = new Vector<Integer>();;

			if(canPlaceCity) {
				for (SOCSettlement settlement : playerData.getSettlements()) {
					currentSettlements.add(settlement.getCoordinates());
				}
			}
			for(int node : format.vertices) {
				// Settlement
				action_choices[index] = canPlaceSettlement && playerData.canPlaceSettlement(node);
				index++;
				// City
				action_choices[index] = canPlaceCity && currentSettlements.contains(node);
				index++;
			}
		} else {
			index += format.vertexActions;
		}

		// # FLAT
		// ## TurnPhase
		action_choices[index] = false;  // RollDice
		index ++;
		action_choices[index] = true;  // Pass
		index ++;

		// ## Trade

		SOCResourceSet ourResources = playerData.getResources();

		for (int rustResourceType = 0; rustResourceType < 5; rustResourceType++) {
			int resourceType = jsettlersResource[rustResourceType];
			int needed = 4;
			if (playerData.getPortFlag(resourceType)) {
				needed = 2;
			} else if (playerData.getPortFlag(SOCBoard.MISC_PORT)) {
				needed = 3;
			}

			if (ourResources.getAmount(resourceType) >= needed) {
				for(int i = 0; i < 4 ; i ++) {
					action_choices[index] = true;
					index++;
				}
			} else {
				index += 4;
			}
		} // index += 5x4 = 20

		// ## Development
		action_choices[index] = game.couldBuyDevCard(playerData.getPlayerNumber());  // BuyDevelopment
		index ++;
		if (!playerData.hasPlayedDevCard()) {
			// Knight
			action_choices[index] = game.canPlayKnight(playerData.getPlayerNumber());
			index ++;
			// RoadBuilding
			action_choices[index] = (playerData.getNumPieces(SOCPlayingPiece.ROAD) >= 1) && playerData.getInventory().hasPlayable(SOCDevCardConstants.ROADS);
			index ++;
			// YearOfPlenty
			action_choices[index] = playerData.getInventory().hasPlayable(SOCDevCardConstants.DISC);
			index ++;
			// FreeResources
			index += 5;
			// Monopole
			boolean canUseMonopole = playerData.getInventory().hasPlayable(SOCDevCardConstants.DISC);
			if (canUseMonopole) {
				for (int r = 0; r < 5; r++) {
					action_choices[index] = true;
					index++;
				}
			} // else { index += 5; }
		}
	}

	public void prerollActions() {
		freshActions();

		int index = format.faceActions + format.edgeActions + format.vertexActions;

		// # FLAT
		// ## TurnPhase
		action_choices[index] = true;  // RollDice
		index ++;
		action_choices[index] = false;  // Pass
		index += 21;
		// ## Development
		action_choices[index] = false;  // BuyDevelopment
		index ++;
		if (!playerData.hasPlayedDevCard()) {
			// Knight
			action_choices[index] = game.canPlayKnight(playerData.getPlayerNumber());
			index ++;
			// RoadBuilding
			action_choices[index] = (playerData.getNumPieces(SOCPlayingPiece.ROAD) >= 1) && playerData.getInventory().hasPlayable(SOCDevCardConstants.ROADS);
			index ++;
			// YearOfPlenty
			action_choices[index] = playerData.getInventory().hasPlayable(SOCDevCardConstants.DISC);
		}
	}

	public void initialSettlementActions() {
		freshActions();

		int index = 0;

		// # BOARD
		// ## Hexes: MoveThief
		index += format.faceActions;

		// ## Paths: BuildRoad
		index += format.edgeActions;

		// ## Intersections: BuildSettlement and BuildCity
		final int[] ourPotentialSettlements = playerData.getPotentialSettlements_arr();
		for(int node : format.vertices) {
			boolean canPlace = Arrays.stream(ourPotentialSettlements).anyMatch(x -> x == node);
			// Settlement
			action_choices[index] = canPlace;
			index++;
			// City
			action_choices[index] = false;
			index++;
		}
	}

	public void initialRoadActions() {
		freshActions();

		int index = 0;

		// # BOARD
		// ## Hexes: MoveThief
		index += format.faceActions;

		// ## Paths: BuildRoad
		final int settlementNode = playerData.getLastSettlementCoord();
		Vector<Integer> possibleRoads = new Vector<Integer>();
		for (int edge : game.getBoard().getAdjacentEdgesToNode_arr(settlementNode)) {
			if (playerData.isLegalRoad(edge)) {
				possibleRoads.add(edge);
			}
		}
		for(int road : format.edges) {
			if(possibleRoads.contains(road)) {
				action_choices[index] = true;
			}
			index++;
		}
	}

	public void roadActions() {
		freshActions();

		int index = 0;

		// # BOARD
		// ## Hexes: MoveThief
		index += format.faceActions;

		// ## Paths: BuildRoad
		for(int road : format.edges) {
			action_choices[index] = playerData.isPotentialRoad(road);
			index++;
		}
	}

	public void thiefActions() {
		freshActions();

		for(int i = 0; i < Format.HEXES.length; i++) {
			int face = format.facesCoords[i];
			if(face == game.getBoard().getRobberHex()) {
				continue;
			}
			List<SOCPlayer> victims = game.getPlayersOnHex(face, null);
			victims.remove(playerData);
			if(victims.isEmpty()) {
				action_choices[format.players * i] = true;
			} else {
				for(SOCPlayer victim : victims) {
					action_choices[format.players * i + ((victim.getPlayerNumber() + format.players - playerData.getPlayerNumber()) % format.players)] = true;
				}
			}
		}
	}

	public void firstFreeResource() {
		flat_state[flat_state.length - 1] = 2;
		freshActions();
		int freeResourceIndex = format.faceActions + format.edgeActions + format.vertexActions + 2 + 20 + 4;
		for(int i = 0; i < 5; i++) {
			action_choices[freeResourceIndex + i] = true;
		}
	}

	// HACK
	public void secondFreeResource(int firstRes) {
		// Pretends the first action was played
		flat_state[rustResource(firstRes)] += 1;
		int bank_res_index = 19 + 8 * format.players + rustResource(firstRes);
		flat_state[bank_res_index] = Integer.max(0, flat_state[bank_res_index] - 1);
		flat_state[flat_state.length - 1] = 1;
	}

	//////////////////////////////////////////////////////////////////////////////////////////////////////
	// UTIL                                                                                             //
	//////////////////////////////////////////////////////////////////////////////////////////////////////

	public static class SOCAction {
		public static final int BUILD_ROAD = 1; // =SOCPlayingPiece.ROAD;
		public static final int BUILD_SETTLEMENT = 2; // =SOCPlayingPiece.SETTLEMENT;
		public static final int BUILD_CITY = 3; // =SOCPlayingPiece.CITY;

		public static final int ROLL = 10;
		public static final int PASS = 11;
		public static final int MOVE_THIEF = 12;

		public static final int BUY_DEVELOPMENT_CARD = 20;
		public static final int USE_KNIGHT = 21;
		public static final int USE_ROAD_BUILDING = 22;
		public static final int USE_YEAR_OF_PLENTY = 23;
		public static final int PICK_FREE_RESOURCE = 24;
		public static final int USE_MONOPOLE = 25;

		public static final int BANK_TRADE = 30;

		//public Format format;
		public int id;
		public int type;
		public int[] parameters; //Not always used

		public SOCAction(Format format, SOCPlayer player, int index) {
			id = index;
			// # BOARD
			// ## Hexes: MoveThief
			if(index < format.faceActions) {
				int face = format.facesCoords[index / format.players];
				int victim = (index + player.getPlayerNumber()) % format.players;
				type = MOVE_THIEF;
				parameters = new int[] { face, victim };
				return;
			}
			index -= format.faceActions;

			// ## Paths: BuildRoad
			if(index < format.edgeActions) {
				int edge = format.edges [index];
				type = BUILD_ROAD;
				parameters = new int[] { edge };
				return;
			}
			index -= format.edgeActions;

			// ## Intersections: BuildSettlement and BuildCity
			if(index < format.vertexActions) {
				int position = format.vertices[index / 2];
				parameters = new int[] { position }; // Settlement/City position
				type = (index % 2 == 0) ? BUILD_SETTLEMENT : BUILD_CITY;
				return;
			}
			index -= format.vertexActions;

			// # FLAT
			// ## TurnPhase
			if(index==0) {
				type = ROLL;
				return;
			}
			index --;
			if(index==0) {
				type = PASS;
				return;
			}
			index --;

			// ## Trade

			if(index < 20) {
				type = BANK_TRADE;
				int from_index = index / 4;
				int from = jsettlersResource[from_index];
				int to_index = index % 4;
				if (to_index >= from_index) { to_index++; }
				int to = jsettlersResource[to_index];
				parameters = new int[] { from, to }; // Resource trade
				return;
			}
			index -= 20;

			// ## Development
			if(index==0) {
				type = BUY_DEVELOPMENT_CARD;
				return;
			}
			index --;
			if(index==0) {
				type = USE_KNIGHT;
				return;
			}
			index --;
			if(index==0) {
				type = USE_ROAD_BUILDING;
				return;
			}
			index --;
			if(index==0) {
				type = USE_YEAR_OF_PLENTY;
				return;
			}
			index --;
			if(index < 5) {
				type = PICK_FREE_RESOURCE;
				parameters = new int[] { jsettlersResource[index] };
				return;
			}
			index -= 5;
			assert(index < 5);
			type = USE_MONOPOLE;
			parameters = new int[] { jsettlersResource[index] };
		}
	}
}
