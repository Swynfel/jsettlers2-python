package soc.robot.general;

import soc.game.*;
import soc.message.*;
import soc.robot.*;
import soc.robot.general.Utils.SOCAction;
import soc.baseclient.SOCDisplaylessPlayerClient;
import soc.disableDebug.D;

import soc.util.CappedQueue;
import soc.util.SOCRobotParameters;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Vector;

public abstract class SwynfelRobotBrainInterface extends SOCRobotBrain {
	public static Random random = new Random();

	protected Utils utils;
	
	protected SwynfelRobotClientInterface client;
	
	public SwynfelRobotBrainInterface(SwynfelRobotClientInterface rc, SOCRobotParameters params, SOCGame ga, CappedQueue<SOCMessage> mq) {
		super(rc, params, ga, mq);
		client = rc;
		robotParameters = params.copyIfOptionChanged(ga.getGameOptions());
		turnEventsCurrent = new Vector<SOCMessage>();
		turnEventsPrev = new Vector<SOCMessage>();
		fastPause = rc.getBoolProp(PROPS_FASTPAUSE, fastPause);
		verbose = rc.getIntProp(PROPS_VERBOSE, verbose);
	}

	private SOCRobotParameters robotParameters;

	public void setOurPlayerData() {
		ourPlayerData = game.getPlayer(client.getNickname());
		utils = new Utils(this, client.format, ourPlayerData, game);
		super.setOurPlayerData();
		ourPlayerData.setFaceId(-2);
	}

	protected abstract void setStrategyFields();

	public boolean preRoll;

	// Simple copy of SOCRobotBrain's run, with slight modifications in the middle
	// Swynfel TODO: Add functions in original run and override those functions
	@Override
	public void run()
	{
		// Thread name for debug
		try
		{
			Thread.currentThread().setName("robotBrain-" + client.getNickname() + "-" + game.getName());
		}
		catch (Throwable th) {}

		if (pinger != null)
		{
			pinger.start();

			//
			// Along with actual game events, the pinger sends a TIMINGPING message
			// once per second, to aid the robot's timekeeping counter.
			//

			while (alive)
			{
				try
				{
					final SOCMessage mes = gameEventQ.get();  // Sleeps until message received
					final int mesType;
					if (mes != null)
					{
						// Debug aid: When looking at message contents or setting a per-message breakpoint,
						// skip the pings; note (mesType != SOCMessage.TIMINGPING) here.

						mesType = mes.getType();
						if (mesType != SOCMessage.TIMINGPING)
							turnEventsCurrent.addElement(mes);
						if (D.ebugOn)
							D.ebugPrintln("mes - " + mes);
					}
					else
					{
						mesType = -1;
					}
					if (mesType == SOCMessage.BANKRESOURCES) {
						utils.handleBankResources((SOCBankResources) mes);
						continue;
					}

					if (waitingForTradeMsg && (counter > 10))
					{
						waitingForTradeMsg = false;
						counter = 0;
					}

					if (waitingForTradeResponse && (counter > tradeResponseTimeoutSec))
					{
						// Remember other players' responses, call client.clearOffer,
						// clear waitingForTradeResponse and counter.
						tradeStopWaitingClearOffer();
					}

					if (waitingForGameState && (counter > 10000))
					{
						//D.ebugPrintln("counter = "+counter);
						//D.ebugPrintln("RESEND");
						counter = 0;
						client.resend();
					}

					if (mesType == SOCMessage.GAMESTATE)
					{
						handleGAMESTATE(((SOCGameState) mes).getState());
						// clears waitingForGameState, updates oldGameState, calls ga.setGameState
					}

					else if (mesType == SOCMessage.STARTGAME)
					{
						SOCDisplaylessPlayerClient.handleSTARTGAME_checkIsBotsOnly(game);
						// might set game.isBotsOnly
						handleGAMESTATE(((SOCStartGame) mes).getGameState());
						// clears waitingForGameState, updates oldGameState, calls ga.setGameState
					}

					else if (mesType == SOCMessage.TURN)
					{
						// Start of a new player's turn.
						// Update game and reset most of our state fields.
						// See also below: if ((mesType == SOCMessage.TURN) && ourTurn).

						handleGAMESTATE(((SOCTurn) mes).getGameState());
						// clears waitingForGameState, updates oldGameState, calls ga.setGameState

						game.setCurrentPlayerNumber(((SOCTurn) mes).getPlayerNumber());
						game.updateAtTurn();

						//
						// remove any expected states
						//
						expectROLL_OR_CARD = false;
						expectPLAY1 = false;
						expectPLACING_ROAD = false;
						expectPLACING_SETTLEMENT = false;
						expectPLACING_CITY = false;
						expectPLACING_SHIP = false;
						expectPLACING_ROBBER = false;
						expectPLACING_FREE_ROAD1 = false;
						expectPLACING_FREE_ROAD2 = false;
						expectPLACING_INV_ITEM = false;
						expectDICERESULT = false;
						expectDISCARD = false;
						expectMOVEROBBER = false;
						expectWAITING_FOR_DISCOVERY = false;
						expectWAITING_FOR_MONOPOLY = false;

						preRoll = true;

						//
						// reset the selling flags and offers history
						//
						if (robotParameters.getTradeFlag() == 1)
						{
							doneTrading = false;
						}
						else
						{
							doneTrading = true;
						}

						waitingForTradeMsg = false;
						waitingForTradeResponse = false;
						negotiator.resetIsSelling();
						negotiator.resetOffersMade();

						waitingForPickSpecialItem = null;
						waitingForSC_PIRI_FortressRequest = false;

						//
						// check or reset any special-building-phase decisions
						//
						decidedIfSpecialBuild = false;
						if (game.getGameState() == SOCGame.SPECIAL_BUILDING)
						{
							if (waitingForSpecialBuild && ! buildingPlan.isEmpty())
							{
								// Keep the building plan.
								// Will ask during loop body to build.
							} else {
								// We have no plan, but will call planBuilding()
								// during the loop body.  If buildingPlan still empty,
								// bottom of loop will end our Special Building turn,
								// just as it would in gamestate PLAY1.  Otherwise,
								// will ask to build after planBuilding.
							}
						} else {
							//
							// reset any plans we had
							//
							buildingPlan.clear();
						}
						negotiator.resetTargetPieces();

						//
						// swap the message-history queues
						//
						{
							Vector<SOCMessage> oldPrev = turnEventsPrev;
							turnEventsPrev = turnEventsCurrent;
							oldPrev.clear();
							turnEventsCurrent = oldPrev;
						}

						turnExceptionCount = 0;
					}

					if (game.getCurrentPlayerNumber() == ourPlayerNumber)
					{
						ourTurn = true;
						waitingForSpecialBuild = false;
					}
					else
					{
						ourTurn = false;
					}

					if ((mesType == SOCMessage.TURN) && ourTurn)
					{
						waitingForOurTurn = false;

						// Clear some per-turn variables.
						// For others, see above: if (mesType == SOCMessage.TURN)
						whatWeFailedToBuild = null;
						failedBuildingAttempts = 0;
						rejectedPlayDevCardType = -1;
						rejectedPlayInvItem = null;
					}


					switch (mesType)
					{
						case SOCMessage.PLAYERELEMENT:
							// If this during the ROLL_OR_CARD state, also updates the
							// negotiator's is-selling flags.
							// If our player is losing a resource needed for the buildingPlan,
							// clear the plan if this is for the Special Building Phase (on the 6-player board).
							// In normal game play, we clear the building plan at the start of each turn.

							handlePLAYERELEMENT((SOCPlayerElement) mes);
							break;

						case SOCMessage.PLAYERELEMENTS:
							// Multiple PLAYERELEMENT updates;
							// see comment above for actions taken.

							handlePLAYERELEMENTS((SOCPlayerElements) mes);
							break;

						case SOCMessage.RESOURCECOUNT:
							handlePLAYERELEMENT
									(null, ((SOCResourceCount) mes).getPlayerNumber(), SOCPlayerElement.SET,
											SOCPlayerElement.RESOURCE_COUNT, ((SOCResourceCount) mes).getCount());
							break;

						case SOCMessage.DICERESULT:
							preRoll = false;
							game.setCurrentDice(((SOCDiceResult) mes).getResult());
							break;

						case SOCMessage.PUTPIECE:
							handlePUTPIECE_updateGameData((SOCPutPiece) mes);
							// For initial roads, also tracks their initial settlement in SOCPlayerTracker.
							break;

						case SOCMessage.MOVEPIECE:
						{
							SOCMovePiece mpm = (SOCMovePiece) mes;
							SOCShip sh = new SOCShip
									(game.getPlayer(mpm.getPlayerNumber()), mpm.getFromCoord(), null);
							game.moveShip(sh, mpm.getToCoord());
						}
						break;

						case SOCMessage.CANCELBUILDREQUEST:
							handleCANCELBUILDREQUEST((SOCCancelBuildRequest) mes);
							break;

						case SOCMessage.MOVEROBBER:
						{
							//
							// Note: Don't call ga.moveRobber() because that will call the
							// functions to do the stealing.  We just want to set where
							// the robber moved, without seeing if something was stolen.
							// MOVEROBBER will be followed by PLAYERELEMENT messages to
							// report the gain/loss of resources.
							//
							moveRobberOnSeven = false;
							final int newHex = ((SOCMoveRobber) mes).getCoordinates();
							if (newHex > 0)
								game.getBoard().setRobberHex(newHex, true);
							else
								((SOCBoardLarge) game.getBoard()).setPirateHex(-newHex, true);
						}
						break;

						case SOCMessage.MAKEOFFER:
							if (robotParameters.getTradeFlag() == 1)
								handleMAKEOFFER((SOCMakeOffer) mes);
							break;

						case SOCMessage.CLEAROFFER:
							if (robotParameters.getTradeFlag() == 1)
							{
								final int pn = ((SOCClearOffer) mes).getPlayerNumber();
								if (pn != -1)
								{
									game.getPlayer(pn).setCurrentOffer(null);
								} else {
									for (int i = 0; i < game.maxPlayers; ++i)
										game.getPlayer(i).setCurrentOffer(null);
								}
							}
							break;

						case SOCMessage.ACCEPTOFFER:
							if (waitingForTradeResponse && (robotParameters.getTradeFlag() == 1))
							{
								if ((ourPlayerNumber == (((SOCAcceptOffer) mes).getOfferingNumber()))
										|| (ourPlayerNumber == ((SOCAcceptOffer) mes).getAcceptingNumber()))
								{
									waitingForTradeResponse = false;
								}
							}
							break;

						case SOCMessage.REJECTOFFER:
							if (robotParameters.getTradeFlag() == 1)
								handleREJECTOFFER((SOCRejectOffer) mes);
							break;

						case SOCMessage.DEVCARDACTION:
						{
							SOCDevCardAction dcMes = (SOCDevCardAction) mes;
							if (dcMes.getAction() != SOCDevCardAction.CANNOT_PLAY)
							{
								handleDEVCARDACTION(dcMes);
							} else {
								// rejected by server, can't play our requested card
								rejectedPlayDevCardType = dcMes.getCardType();
								waitingForGameState = false;
								expectPLACING_FREE_ROAD1 = false;
								expectWAITING_FOR_DISCOVERY = false;
								expectWAITING_FOR_MONOPOLY = false;
								expectPLACING_ROBBER = false;
							}
						}
						break;

						case SOCMessage.SIMPLEREQUEST:
							// These messages can almost always be ignored by bots,
							// unless we've just sent a request to attack a pirate fortress.
							// Some request types are handled at the bottom of the loop body;
							// search for SOCMessage.SIMPLEREQUEST

							if (ourTurn && waitingForSC_PIRI_FortressRequest)
							{
								final SOCSimpleRequest rqMes = (SOCSimpleRequest) mes;

								if ((rqMes.getRequestType() == SOCSimpleRequest.SC_PIRI_FORT_ATTACK)
										&& (rqMes.getPlayerNumber() == -1))
								{
									// Attack request was denied: End our turn now.
									// Reset method sets waitingForGameState, which will bypass
									// any further actions in the run() loop body.

									waitingForSC_PIRI_FortressRequest = false;
									resetFieldsAtEndTurn();
									client.endTurn(game);
								}
								// else, from another player; we can ignore it
							}
							break;

						case SOCMessage.SIMPLEACTION:
							// Most action types are handled later in the loop body;
							// search for SOCMessage.SIMPLEACTION

							switch(((SOCSimpleAction) mes).getActionType())
							{
								case SOCSimpleAction.SC_PIRI_FORT_ATTACK_RESULT:
									if (ourTurn && waitingForSC_PIRI_FortressRequest)
									{
										// Our player has won or lost an attack on a pirate fortress.
										// When we receive this message, other messages have already
										// been sent to update related game state. End our turn now.
										// Reset method sets waitingForGameState, which will bypass
										// any further actions in the run() loop body.

										waitingForSC_PIRI_FortressRequest = false;
										resetFieldsAtEndTurn();
										// client.endTurn not needed; making the attack implies sending endTurn
									}
									// else, from another player; we can ignore it

									break;
							}
							break;

						case SOCMessage.INVENTORYITEMACTION:
							if (((SOCInventoryItemAction) mes).action == SOCInventoryItemAction.CANNOT_PLAY)
							{
								final List<SOCInventoryItem> itms = ourPlayerData.getInventory().getByStateAndType
										(SOCInventory.PLAYABLE, ((SOCInventoryItemAction) mes).itemType);
								if (itms != null)
									rejectedPlayInvItem = itms.get(0);  // any item of same type# is similar enough here

								waitingForGameState = false;
								expectPLACING_INV_ITEM = false;  // in case was rejected placement (SC_FTRI gift port, etc)
							}
							break;

					}  // switch(mesType)

					if (!waitingForGameState) {
						switch (game.getGameState()) {
							case SOCGame.ROLL_OR_CARD:
								expectROLL_OR_CARD = false;
								if ((!waitingForOurTurn) && ourTurn && !((expectPLAY1 || expectDISCARD || expectPLACING_ROBBER || expectDICERESULT) && counter < 4000)) {
									playPreRoll();
								} else {
									expectDICERESULT = true;
								}
								break;

							case SOCGame.WAITING_FOR_ROBBER_OR_PIRATE:
								if (ourTurn) {
									expectPLACING_ROBBER = true;
									waitingForGameState = true;
									counter = 0;
									client.choosePlayer(game, SOCChoosePlayer.CHOICE_MOVE_ROBBER);
									pause(200);
								}
								break;
							case SOCGame.PLACING_ROBBER:
								expectPLACING_ROBBER = false;
								if ((!waitingForOurTurn) && ourTurn && !((expectROLL_OR_CARD || expectPLAY1) && (counter < 4000))) {
									waitingForGameState = true;
									if (moveRobberOnSeven) {
										// robber moved because 7 rolled on dice
										moveRobberOnSeven = false;
										expectPLAY1 = true;
									} else {

										if (oldGameState == SOCGame.ROLL_OR_CARD) {
											// robber moved from playing knight card before dice roll
											expectROLL_OR_CARD = true;
										} else if (oldGameState == SOCGame.PLAY1) {
											// robber moved from playing knight card after dice roll
											expectPLAY1 = true;
										}
									}

									counter = 0;
									moveRobber();
								}
								break;
							case SOCGame.WAITING_FOR_DISCOVERY:
								expectWAITING_FOR_DISCOVERY = false;
								if ((!waitingForOurTurn) && ourTurn && !(expectPLAY1) && (counter < 4000)) {
									playFreeResources();
								}
								break;
							case SOCGame.WAITING_FOR_MONOPOLY:
								expectWAITING_FOR_MONOPOLY = false;
								if ((! waitingForOurTurn) && ourTurn && !(expectPLAY1) && (counter < 4000)) {
									waitingForGameState = true;
									if(preRoll) {
										expectROLL_OR_CARD = true;
									} else {
										expectPLAY1 = true;
									}
									counter = 0;
									client.pickResourceType(game, monopolyChoice);
									actionMsg("Monopole", ""+monopolyChoice);
									pause(1500);
								}
							case SOCGame.PLACING_FREE_ROAD1:
								expectPLACING_FREE_ROAD1 = false;
								if((!waitingForOurTurn && ourTurn && !(expectPLAY1) && counter < 4000)) {
									playRoadBuilding();
									expectPLACING_FREE_ROAD2 = true;
								}
								break;
							case SOCGame.PLACING_FREE_ROAD2:
								expectPLACING_FREE_ROAD2 = false;
								if((!waitingForOurTurn && ourTurn && !(expectPLAY1) && counter < 4000)) {
									playRoadBuilding();
									if(preRoll) {
										expectROLL_OR_CARD = true;
									} else {
										expectPLAY1 = true;
									}
								}
								break;
						}
					}

					if (waitingForTradeMsg && (mesType == SOCMessage.BANKTRADE)
							&& (((SOCBankTrade) mes).getPlayerNumber() == ourPlayerNumber)) {
						// This is the bank/port trade confirmation announcement we've been waiting for
						waitingForTradeMsg = false;
					}

					if (waitingForDevCard && (mesType == SOCMessage.SIMPLEACTION)
							&& (((SOCSimpleAction) mes).getPlayerNumber() == ourPlayerNumber)
							&& (((SOCSimpleAction) mes).getActionType() == SOCSimpleAction.DEVCARD_BOUGHT)) {
						// This is the "dev card bought" message we've been waiting for
						waitingForDevCard = false;
					}

					if (((game.getGameState() == SOCGame.PLAY1) || (game.getGameState() == SOCGame.SPECIAL_BUILDING))
							&& ! (waitingForGameState || waitingForTradeMsg || waitingForTradeResponse || waitingForDevCard
							|| expectPLACING_ROAD || expectPLACING_SETTLEMENT || expectPLACING_CITY
							|| expectPLACING_SHIP || expectPLACING_FREE_ROAD1 || expectPLACING_FREE_ROAD2
							|| expectPLACING_ROBBER || expectWAITING_FOR_DISCOVERY || expectWAITING_FOR_MONOPOLY
							|| waitingForSC_PIRI_FortressRequest || (waitingForPickSpecialItem != null)))
					{
						expectPLAY1 = false;

						if ((! waitingForOurTurn) && ourTurn && !(expectROLL_OR_CARD && (counter < 4000))) {
							counter = 0;

							////////////////////////////////////////////////////////////////////////////////////////////
							// Swynfel: Our real turn                                                                 //
							////////////////////////////////////////////////////////////////////////////////////////////
							playMsg();
							play();
						}
					}

					placeIfExpectPlacing();

					/**
					 * Handle various message types here at bottom of loop.
					 */
					switch (mesType)
					{
						case SOCMessage.PUTPIECE:
							/**
							 * this is for player tracking
							 *
							 * For initial placement of our own pieces, also checks
							 * and clears expectPUTPIECE_FROM_START1A,
							 * and sets expectSTART1B, etc.  The final initial putpiece
							 * clears expectPUTPIECE_FROM_START2B and sets expectROLL_OR_CARD.
							 */
						{
							final SOCPutPiece mpp = (SOCPutPiece) mes;
							final int pn = mpp.getPlayerNumber();
							final int coord = mpp.getCoordinates();
							final int pieceType = mpp.getPieceType();
							handlePUTPIECE_updateTrackers(pn, coord, pieceType);
						}

						break;

						case SOCMessage.MOVEPIECE:
							/**
							 * this is for player tracking of moved ships
							 */
						{
							final SOCMovePiece mpp = (SOCMovePiece) mes;
							final int pn = mpp.getPlayerNumber();
							final int coord = mpp.getToCoord();
							final int pieceType = mpp.getPieceType();
							// TODO what about getFromCoord()? Should mark that loc as unoccupied in trackers
							handlePUTPIECE_updateTrackers(pn, coord, pieceType);
						}
						break;

						case SOCMessage.DICERESULT:
							if (expectDICERESULT)
							{
								expectDICERESULT = false;

								if (((SOCDiceResult) mes).getResult() == 7)
								{
									final boolean robWithoutRobber = game.isGameOptionSet(SOCGameOption.K_SC_PIRI);
									// In scenario SC_PIRI there's no robber to be moved. Instead,
									// current player will be prompted soon to choose a player to rob on 7

									if (! robWithoutRobber)
										moveRobberOnSeven = true;

									if (ourPlayerData.getResources().getTotal() > 7)
									{
										expectDISCARD = true;
									} else if (ourTurn) {
										if (! robWithoutRobber)
											expectPLACING_ROBBER = true;
										else
											expectPLAY1 = true;
									}
								}
								else
								{
									expectPLAY1 = true;
								}
							}
							break;

						case SOCMessage.SIMPLEREQUEST:
						{
							// Some request types are handled at the top of the loop body;
							//   search for SOCMessage.SIMPLEREQUEST
							// Some are handled here
							// Most can be ignored by bots

							final SOCSimpleRequest rqMes = (SOCSimpleRequest) mes;
							switch (rqMes.getRequestType())
							{
								case SOCSimpleRequest.PROMPT_PICK_RESOURCES:
									// gold hex
									counter = 0;
									pickFreeResources(rqMes.getValue1());
									waitingForGameState = true;
									if (game.isInitialPlacement())
									{
										if (game.isGameOptionSet(SOCGameOption.K_SC_3IP))
											expectSTART3B = true;
										else
											expectSTART2B = true;
									} else {
										expectPLAY1 = true;
									}
									break;
							}
						}
						break;

						case SOCMessage.DISCARDREQUEST:
							expectDISCARD = false;

							if ((game.getCurrentDice() == 7) && ourTurn)
							{
								if (! game.isGameOptionSet(SOCGameOption.K_SC_PIRI))
									expectPLACING_ROBBER = true;
								else
									expectPLAY1 = true;
							}
							else
							{
								expectPLAY1 = true;
							}

							counter = 0;
							client.discard(game, discardStrategy.discard
									(((SOCDiscardRequest) mes).getNumberOfDiscards(), buildingPlan));

							break;

						case SOCMessage.CHOOSEPLAYERREQUEST:
						{
							final SOCChoosePlayerRequest msg = (SOCChoosePlayerRequest) mes;
							counter = 0;
							client.choosePlayer(game, thiefVictim);
							actionMsg("Thief choose victim", ""+thiefVictim);
						}
						break;

						case SOCMessage.CHOOSEPLAYER:
						{
							final int vpn = ((SOCChoosePlayer) mes).getChoice();
							// Cloth is more valuable.
							// TODO decide when we should choose resources instead
							client.choosePlayer(game, -(vpn + 1));
						}
						break;

						case SOCMessage.SETSPECIALITEM:
							if (waitingForPickSpecialItem != null)
							{
								final SOCSetSpecialItem siMes = (SOCSetSpecialItem) mes;
								if (siMes.typeKey.equals(waitingForPickSpecialItem))
								{
									// This could be the "pick special item" message we've been waiting for,
									// or a related SET/CLEAR message that precedes it

									switch (siMes.op)
									{
										case SOCSetSpecialItem.OP_PICK:
											waitingForPickSpecialItem = null;

											// Now that this is received, can continue our turn.
											// Any specific action needed? Not for SC_WOND.
											break;

										case SOCSetSpecialItem.OP_DECLINE:
											waitingForPickSpecialItem = null;

											// TODO how to prevent asking again? (similar to whatWeFailedtoBuild)
											break;

										// ignore SET or CLEAR that precedes the PICK message
									}
								}
							}
							break;

						case SOCMessage.ROBOTDISMISS:
							if ((! expectDISCARD) && (! expectPLACING_ROBBER))
							{
								client.leaveGame(game, "dismiss msg", false, false);
								alive = false;
							}
							break;

						case SOCMessage.TIMINGPING:
							// Once-per-second message from the pinger thread
							counter++;
							break;

					}  // switch (mesType) - for some types, at bottom of loop body

					if (ourTurn && (counter > 15000))
					{
						// We've been waiting too long, must be a bug: Leave the game.
						// This is a fallback, server has SOCForceEndTurnThread which
						// should have already taken action.
						// Before v1.1.20, would leave game even during other (human) players' turns.
						client.leaveGame(game, "counter 15000", true, false);
						alive = false;
					}

					if ((failedBuildingAttempts > (2 * MAX_DENIED_BUILDING_PER_TURN))
							&& game.isInitialPlacement())
					{
						// Apparently can't decide where we can initially place:
						// Leave the game.
						client.leaveGame(game, "failedBuildingAttempts at start", true, false);
						alive = false;
					}

                    /*
                       if (D.ebugOn) {
                       if (mes != null) {
                       debugInfo();
                       D.ebugPrintln("~~~~~~~~~~~~~~~~");
                       }
                       }
                     */

					Thread.yield();
				}
				catch (Exception e)
				{
					// Print exception; ignore errors due to game reset in another thread
					if (alive && ((game == null) || (game.getGameState() != SOCGame.RESET_OLD)))
					{
						++turnExceptionCount;  // TODO end our turn if too many

						String eMsg = (turnExceptionCount == 1)
								? "*** Robot caught an exception - " + e
								: "*** Robot caught an exception (" + turnExceptionCount + " this turn) - " + e;
						D.ebugPrintln(eMsg);
						System.out.println(eMsg);
						e.printStackTrace();
					}
				}
			}
			finished();
		}
		else
		{
			System.out.println("AGG! NO PINGER!");
		}

		clean();
	}

	private static final String PROPS_FASTPAUSE = "fast";
	private boolean fastPause = false;

	private static final String PROPS_VERBOSE = "verbose";
	public int verbose = 0; //0 : No, 1 : My new turns only, 2 : All

	public void pause(int msec) {
		if (!fastPause) {
			super.pause(msec);
		}
	}

	//Stops and cleans the brain variables
	protected void clean() {
		gameEventQ = null;

		client.addCleanKill();
		client = null;

		game = null;
		ourPlayerData = null;
		dummyCancelPlayerData = null;
		whatWeWantToBuild = null;
		whatWeFailedToBuild = null;
		rejectedPlayInvItem = null;
		resourceChoices = null;
		ourPlayerTracker = null;
		playerTrackers = null;

		pinger.stopPinger();
		pinger = null;
	}

	////////////////////////////////////////////////////////////////////////////
	// Swynfel Others
	////////////////////////////////////////////////////////////////////////////

	protected void handleGAMESTATS(SOCGameStats mes)
	{
		int[] scores = mes.getScores();

		if (game.getGameState() != SOCGame.OVER)
		{
			System.err.println("End game stats while at " + game.getGameState());
			return;
		}

		for (int i = 0; i < scores.length; ++i) {
			game.getPlayer(i).forceFinalVP(scores[i]);
		}
	}

	private void planBuilding() {}

	////////////////////////////////////////////////////////////////////////////
	// Swynfel Debug Code
	////////////////////////////////////////////////////////////////////////////
	
	protected void print(String message, int requiredVerbose) {
		if(verbose >= requiredVerbose) {
			String name = game.getName();
			name = name.substring(name.length()-3);
			System.out.println(name+" "+message);
		}
	}
	
	protected void print(String message) {
		print(message, 2);
	}
	
	protected void turnMsg() {
		boolean myTurn = game.getCurrentPlayerNumber() == ourPlayerNumber;
		String side = new String(new char[12]).replace("\0", myTurn ? "=" : "-");
		print(String.format("%s New turn (%03d) - [%s] %s",
				side,
				game.getRoundCount(),
				resourceString(getOurPlayerData().getResources()),
				side
			), myTurn ? 1 : 2);
	}
	
	protected void playMsg() {
		//print("----------------------------------------");
	}

	protected void actionMsg(String type, String message) {
		print("<"+type+"> "+message);
	}
	
	protected void actionMsg(String type) {
		actionMsg(type, "");
	}

	protected void debugMsg(String message) {
		print("[Debug] "+message);
	}
	
	public static String resourceString(SOCResourceSet resources) {
		int[] set = resources.getAmounts(false);
		return String.format("%2d|%2d|%2d|%2d|%2d", set[0], set[1], set[2], set[3], set[4]);
	}
	
	////////////////////////////////////////////////////////////////////////////
	// Swynfel Gameplay Code
	////////////////////////////////////////////////////////////////////////////

	protected void play() {
		utils.newState();
		utils.turnActions();

		SOCAction action = chooseAction();

		execute(action);
	}

	protected void playPreRoll() {
		utils.newState();
		utils.prerollActions();

		SOCAction action = chooseAction();

		execute(action);
	}

	protected void playFreeResources() {
		utils.newState();
		utils.firstFreeResource();
		SOCAction firstResource = chooseAction();
		int firstRes = firstResource.parameters[0];
		utils.secondFreeResource(firstRes);
		SOCAction secondResource = chooseAction();
		int secondRes = firstResource.parameters[0];
		resourceChoices = new SOCResourceSet();
		resourceChoices.add(1, firstRes);
		resourceChoices.add(1, secondRes);
		waitingForGameState = true;
		if(preRoll) {
			expectROLL_OR_CARD = true;
		} else {
			expectPLAY1 = true;
		}
		counter = 0;
		client.pickResources(game, resourceChoices);

		actionMsg("Free Resource", resourceChoices.toString());
	}

	protected void playRoadBuilding() {
		utils.newState();
		utils.roadActions();

		SOCAction action = chooseAction();

		whatWeWantToBuild = new SOCRoad(ourPlayerData, action.parameters[0], game.getBoard());

		waitingForGameState = true;
		counter = 0;

		pause(500);
		client.putPiece(game, whatWeWantToBuild);
		pause(1000);

		actionMsg("Road Building", whatWeWantToBuild.toString());
	}

	@Override
	protected void moveRobber()
	{
		utils.newState();
		utils.thiefActions();

		SOCAction action = chooseAction();
		play_moveThief(action.parameters[0], action.parameters[1]);
	}

	protected void finished() {
		utils.endState();
	}

	protected abstract int pickAction();

	public SOCAction chooseAction() {
		int actionId = pickAction();
		if(!utils.action_choices[actionId]) {
			int k = 0;
			while(!utils.action_choices[k]) {
				k++;
			}
			System.out.print("ERROR, picked action " + actionId +" that isn't legal, replaced by first valid action (" + k + ")");
			actionId = k;
		}

		return new SOCAction(utils.format, ourPlayerData, actionId);
	}
		
	protected void execute(SOCAction action) {
		switch(action.type) {
			case SOCAction.BUILD_ROAD:
				play_buildRoad(action.parameters[0]);
				return;
			case SOCAction.BUILD_SETTLEMENT:
				play_buildSettelement(action.parameters[0]);
				return;
			case SOCAction.BUILD_CITY:
				play_buildCity(action.parameters[0]);
				return;

			case SOCAction.ROLL:
				play_roll();
				return;
			case SOCAction.PASS:
				play_pass();
				return;
			case SOCAction.MOVE_THIEF:
				play_moveThief(action.parameters[0], action.parameters[1]);
				return;

			case SOCAction.BUY_DEVELOPMENT_CARD:
				play_buyDevCard();
				return;
			case SOCAction.USE_KNIGHT:
				play_useKnight();
				return;
			case SOCAction.USE_ROAD_BUILDING:
				play_useRoadBuilding();
				return;
			case SOCAction.USE_YEAR_OF_PLENTY:
				play_useYearOfPlenty();
				return;
			case SOCAction.PICK_FREE_RESOURCE:
				play_pickFreeResource(action.parameters[0]);
				return;
			case SOCAction.USE_MONOPOLE:
				play_useMonopoly(action.parameters[0]);
				return;
			case SOCAction.BANK_TRADE:
				play_tradeBank(action.parameters[0], action.parameters[1]);
				return;
			default:
		}
	}
	
	private void buildPiece() {
		waitingForGameState = true;
		actionMsg("Building", whatWeWantToBuild.toString());
		client.buildRequest(game, whatWeWantToBuild.getType());
	}
	
	protected void play_buildCity(int position) {
		whatWeWantToBuild = new SOCCity(ourPlayerData, position, game.getBoard());
		
		expectPLACING_CITY = true;
		buildPiece();
	}
	
	protected void play_buildSettelement(int position) {
		whatWeWantToBuild = new SOCSettlement(ourPlayerData, position, game.getBoard());

		expectPLACING_SETTLEMENT = true;
		buildPiece();
	}
	
	protected void play_buildRoad(int position) {
		whatWeWantToBuild = new SOCRoad(ourPlayerData, position, game.getBoard());

		expectPLACING_ROAD = true;
		buildPiece();
	}

	protected void play_roll() {
		expectDICERESULT = true;
		counter = 0;
		client.rollDice(game);

		actionMsg("Rolling Dice");
	}

	protected void play_pass() {
		resetFieldsAtEndTurn();
		client.endTurn(game);

		actionMsg("Passing Turn");
	}

	protected int thiefVictim = 0;

	protected void play_moveThief(int position, int victim) {
		client.moveRobber(game, ourPlayerData, position);
		thiefVictim = victim;

		actionMsg("Moving Thief", game.getBoard().getRobberHex() + " ---> " + position  + " (" +victim+")");
	}

	protected void play_buyDevCard() {
		client.buyDevCard(game);

		waitingForDevCard = true;
		actionMsg("Buying Development Card");
	}

	protected void play_tradeBank(int give, int want) {
		SOCResourceSet giveSet = new SOCResourceSet();
		int needed = 4;
		if (ourPlayerData.getPortFlag(give)) {
			needed = 2;
		} else if (ourPlayerData.getPortFlag(SOCBoard.MISC_PORT)) {
			needed = 3;
		}
		giveSet.add(needed, give);
		SOCResourceSet wantSet = new SOCResourceSet();
		wantSet.add(1, want);
		play_tradeBank(giveSet, wantSet);
	}

	protected void play_tradeBank(SOCResourceSet give, SOCResourceSet want) {
		client.bankTrade(game, give, want);
		actionMsg("BankTrade", resourceString(give) + " ---> " + resourceString(want));
		waitingForTradeMsg = true;
	}

	protected void play_useKnight() {
		expectPLACING_ROBBER = true;
		waitingForGameState = true;
		counter = 0;
		client.playDevCard(game, SOCDevCardConstants.KNIGHT);

		actionMsg("DVP Knight");
	}

	protected void play_useRoadBuilding() {
		expectPLACING_FREE_ROAD1 = true;
		waitingForGameState = true;
		counter = 0;
		client.playDevCard(game, SOCDevCardConstants.ROADS);

		actionMsg("DVP Road Building");
	}

	protected void play_useYearOfPlenty() {
		expectWAITING_FOR_DISCOVERY = true;
		waitingForGameState = true;
		counter = 0;
		client.playDevCard(game, SOCDevCardConstants.DISC);

		actionMsg("DVP Year of Plenty");
	}

	protected void play_pickFreeResource(int res) {
		System.out.println("[ERROR] Shouldn't pick free resources from inside play");
		return;
		/*
		utils.secondFreeResource();
		SOCAction secondResource = chooseAction();
		int otherRes = 1;
		if(secondResource.type == SOCAction.PICK_FREE_RESOURCE) {
			otherRes = secondResource.parameters[0];
		} else {
			System.out.println("[ERROR] Couldn't pick second resource "+secondResource);
		}
		resourceChoices = new SOCResourceSet();
		resourceChoices.add(1, res);
		resourceChoices.add(1, otherRes);
		waitingForGameState = true;
		expectPLAY1 = true;
		counter = 0;
		client.pickResources(game, resourceChoices);
		 */
	}

	private int monopolyChoice = 0;

	protected void play_useMonopoly(int res) {
		expectWAITING_FOR_MONOPOLY = true;
		waitingForGameState = true;
		counter = 0;
		client.playDevCard(game, SOCDevCardConstants.MONO);
		monopolyChoice = res;

		actionMsg("DVP Monopoly");
	}
}
