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
		super.setOurPlayerData();
		ourPlayerData.setFaceId(-2);
		utils = new Utils(client.format, ourPlayerData, game);
	}

	protected abstract void setStrategyFields();

	// Simple copy of SOCRobotBrain's run
	// Swynfel TODO: Adds functions in original run and override those functions
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

					/**
					 * Handle some message types early.
					 *
					 * When reading the main flow of this method, skip past here;
					 * search for "it's time to decide to build or take other normal actions".
					 */
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

					if ((game.getGameState() == SOCGame.ROLL_OR_CARD) && ! waitingForGameState)
					{
						rollOrPlayKnightOrExpectDice();

						// On our turn, ask client to roll dice or play a knight;
						// on other turns, update flags to expect dice result.
						// Clears expectROLL_OR_CARD to false.
						// Sets either expectDICERESULT, or expectPLACING_ROBBER and waitingForGameState.
					}

					if (ourTurn && (game.getGameState() == SOCGame.WAITING_FOR_ROBBER_OR_PIRATE) && ! waitingForGameState)
					{
						// TODO handle moving the pirate too
						// For now, always decide to move the robber.
						// Once we move the robber, will also need to deal with state WAITING_FOR_ROB_CLOTH_OR_RESOURCE.
						expectPLACING_ROBBER = true;
						waitingForGameState = true;
						counter = 0;
						client.choosePlayer(game, SOCChoosePlayer.CHOICE_MOVE_ROBBER);
						pause(200);
					}

					else if ((game.getGameState() == SOCGame.PLACING_ROBBER) && ! waitingForGameState)
					{
						expectPLACING_ROBBER = false;

						if ((! waitingForOurTurn) && ourTurn)
						{
							if (! ((expectROLL_OR_CARD || expectPLAY1) && (counter < 4000)))
							{
								if (moveRobberOnSeven)
								{
									// robber moved because 7 rolled on dice
									moveRobberOnSeven = false;
									waitingForGameState = true;
									counter = 0;
									expectPLAY1 = true;
								}
								else
								{
									waitingForGameState = true;
									counter = 0;

									if (oldGameState == SOCGame.ROLL_OR_CARD)
									{
										// robber moved from playing knight card before dice roll
										expectROLL_OR_CARD = true;
									}
									else if (oldGameState == SOCGame.PLAY1)
									{
										// robber moved from playing knight card after dice roll
										expectPLAY1 = true;
									}
								}

								counter = 0;
								moveRobber();
							}
						}
					}

					if ((game.getGameState() == SOCGame.WAITING_FOR_DISCOVERY) && ! waitingForGameState)
					{
						expectWAITING_FOR_DISCOVERY = false;

						if ((! waitingForOurTurn) && ourTurn)
						{
							if (! (expectPLAY1) && (counter < 4000))
							{
								waitingForGameState = true;
								expectPLAY1 = true;
								counter = 0;
								client.pickResources(game, resourceChoices);
								pause(1500);
							}
						}
					}

					if ((game.getGameState() == SOCGame.WAITING_FOR_MONOPOLY) && ! waitingForGameState)
					{
						expectWAITING_FOR_MONOPOLY = false;

						if ((! waitingForOurTurn) && ourTurn)
						{
							if (!(expectPLAY1) && (counter < 4000))
							{
								waitingForGameState = true;
								expectPLAY1 = true;
								counter = 0;
								client.pickResourceType(game, monopolyStrategy.getMonopolyChoice());
								pause(1500);
							}
						}
					}

					if (ourTurn && (! waitingForOurTurn)
							&& (game.getGameState() == SOCGame.PLACING_INV_ITEM) && (! waitingForGameState))
					{
						planAndPlaceInvItem();  // choose and send a placement location
					}

					if (waitingForTradeMsg && (mesType == SOCMessage.BANKTRADE)
							&& (((SOCBankTrade) mes).getPlayerNumber() == ourPlayerNumber))
					{
						//
						// This is the bank/port trade confirmation announcement we've been waiting for
						//
						waitingForTradeMsg = false;
					}

					if (waitingForDevCard && (mesType == SOCMessage.SIMPLEACTION)
							&& (((SOCSimpleAction) mes).getPlayerNumber() == ourPlayerNumber)
							&& (((SOCSimpleAction) mes).getActionType() == SOCSimpleAction.DEVCARD_BOUGHT))
					{
						//
						// This is the "dev card bought" message we've been waiting for
						//
						waitingForDevCard = false;
					}

					/**
					 * Planning: If our turn and not waiting for something,
					 * it's time to decide to build or take other normal actions.
					 */
					if (((game.getGameState() == SOCGame.PLAY1) || (game.getGameState() == SOCGame.SPECIAL_BUILDING))
							&& ! (waitingForGameState || waitingForTradeMsg || waitingForTradeResponse || waitingForDevCard
							|| expectPLACING_ROAD || expectPLACING_SETTLEMENT || expectPLACING_CITY
							|| expectPLACING_SHIP || expectPLACING_FREE_ROAD1 || expectPLACING_FREE_ROAD2
							|| expectPLACING_ROBBER || expectWAITING_FOR_DISCOVERY || expectWAITING_FOR_MONOPOLY
							|| waitingForSC_PIRI_FortressRequest || (waitingForPickSpecialItem != null)))
					{
						expectPLAY1 = false;

						// 6-player: check Special Building Phase
						// during other players' turns.
						if ((! ourTurn) && waitingForOurTurn && gameIs6Player
								&& (! decidedIfSpecialBuild) && (! expectPLACING_ROBBER))
						{
							decidedIfSpecialBuild = true;

							/**
							 * It's not our turn.  We're not doing anything else right now.
							 * Gamestate has passed ROLL_OR_CARD, so we know what resources to expect.
							 * Do we want to Special Build?  Check the same conditions as during our turn.
							 * Make a plan if we don't have one,
							 * and if we haven't given up building attempts this turn.
							 */

							if (buildingPlan.empty() && (ourPlayerData.getResources().getTotal() > 1)
									&& (failedBuildingAttempts < MAX_DENIED_BUILDING_PER_TURN))
							{
								planBuilding();

                                    /*
                                     * planBuilding takes these actions, sets buildingPlan and other fields
                                     * (see its javadoc):
                                     *
                                    decisionMaker.planStuff(robotParameters.getStrategyType());

                                    if (! buildingPlan.empty())
                                    {
                                        lastTarget = (SOCPossiblePiece) buildingPlan.peek();
                                        negotiator.setTargetPiece(ourPlayerNumber, buildingPlan.peek());
                                    }
                                     */

								if ( ! buildingPlan.empty())
								{
									// If we have the resources right now, ask to Special Build

									final SOCPossiblePiece targetPiece = buildingPlan.peek();
									final SOCResourceSet targetResources = targetPiece.getResourcesToBuild();
									// may be null

									if ((ourPlayerData.getResources().contains(targetResources)))
									{
										// Ask server for the Special Building Phase.
										// (TODO) if FAST_STRATEGY: Maybe randomly don't ask, to lower opponent difficulty?
										waitingForSpecialBuild = true;
										client.buildRequest(game, -1);
										pause(100);
									}
								}
							}
						}

						if ((! waitingForOurTurn) && ourTurn)
						{
							if (! (expectROLL_OR_CARD && (counter < 4000)))
							{
								counter = 0;

								//D.ebugPrintln("DOING PLAY1");
								if (D.ebugOn)
								{
									client.sendText(game, "================================");

									// for each player in game:
									//    sendText and debug-prn game.getPlayer(i).getResources()
									printResources();
								}

								/* Swynfel : Not using this original code, ask our agent instead

								 * if we haven't played a dev card yet,
								 * and we have a knight, and we can get
								 * largest army, play the knight.
								 * If we're in SPECIAL_BUILDING (not PLAY1),
								 * can't trade or play development cards.
								 *
								 * In scenario _SC_PIRI (which has no robber and
								 * no largest army), play one whenever we have
								 * it, someone else has resources, and we can
								 * convert a ship to a warship.

								if ((game.getGameState() == SOCGame.PLAY1) && ! ourPlayerData.hasPlayedDevCard())
								{
									playKnightCardIfShould();  // might set expectPLACING_ROBBER and waitingForGameState
								}

								 *
								 * make a plan if we don't have one,
								 * and if we haven't given up building
								 * attempts this turn.
								 *
								if ( (! expectPLACING_ROBBER) && buildingPlan.empty()
										&& (ourPlayerData.getResources().getTotal() > 1)
										&& (failedBuildingAttempts < MAX_DENIED_BUILDING_PER_TURN))
								{
									planBuilding();

                                        /*
                                         * planBuilding takes these actions, sets buildingPlan and other fields
                                         * (see its javadoc):
                                         *
                                        decisionMaker.planStuff(robotParameters.getStrategyType());

                                        if (! buildingPlan.empty())
                                        {
                                            lastTarget = (SOCPossiblePiece) buildingPlan.peek();
                                            negotiator.setTargetPiece(ourPlayerNumber, buildingPlan.peek());
                                        }

								}

								//D.ebugPrintln("DONE PLANNING");
								if ( (! expectPLACING_ROBBER) && (! buildingPlan.empty()))
								{
									// Time to build something.

									// Either ask to build a piece, or use trading or development
									// cards to get resources to build it.  See javadoc for flags set
									// (expectPLACING_ROAD, etc).  In a future iteration of the run loop
									// with the expected PLACING_ state, we'll build whatWeWantToBuild
									// in placeIfExpectPlacing().

									buildOrGetResourceByTradeOrCard();
								}
								*/

								if (!expectPLACING_ROBBER) {
									// Swynfel: Our real turn
									playMsg();
									play();
								}

								/**
								 * see if we're done with our turn
								 */
								if (! (expectPLACING_SETTLEMENT || expectPLACING_FREE_ROAD1 || expectPLACING_FREE_ROAD2
										|| expectPLACING_ROAD || expectPLACING_CITY || expectPLACING_SHIP
										|| expectWAITING_FOR_DISCOVERY || expectWAITING_FOR_MONOPOLY
										|| expectPLACING_ROBBER || waitingForTradeMsg || waitingForTradeResponse
										|| waitingForDevCard
										|| waitingForGameState
										|| (waitingForPickSpecialItem != null)))
								{
									// Any last things for turn from game's scenario?
									boolean scenActionTaken = false;
									if (game.isGameOptionSet(SOCGameOption.K_SC_FTRI)
											|| game.isGameOptionSet(SOCGameOption.K_SC_PIRI))
									{
										// possibly attack pirate fortress
										// or place a gift port for better bank trades
										scenActionTaken = considerScenarioTurnFinalActions();
									}

									if (! scenActionTaken)
									{
										resetFieldsAtEndTurn();
                                            /*
                                             * These state fields are reset:
                                             *
                                            waitingForGameState = true;
                                            counter = 0;
                                            expectROLL_OR_CARD = true;
                                            waitingForOurTurn = true;

                                            doneTrading = (robotParameters.getTradeFlag() != 1);

                                            //D.ebugPrintln("!!! ENDING TURN !!!");
                                            negotiator.resetIsSelling();
                                            negotiator.resetOffersMade();
                                            buildingPlan.clear();
                                            negotiator.resetTargetPieces();
                                             */

										pause(1500);
										client.endTurn(game);
									}
								}
							}
						}
					}

					/**
					 * Placement: Make various putPiece calls; server has told us it's OK to buy them.
					 * Call client.putPiece.
					 * Works when it's our turn and we have an expect flag set
					 * (such as expectPLACING_SETTLEMENT, in these game states:
					 * START1A - START2B or - START3B
					 * PLACING_SETTLEMENT, PLACING_ROAD, PLACING_CITY
					 * PLACING_FREE_ROAD1, PLACING_FREE_ROAD2
					 */
					if (! waitingForGameState)
					{
						placeIfExpectPlacing();
					}

					/**
					 * End of various putPiece placement calls.
					 */

                    /*
                       if (game.getGameState() == SOCGame.OVER) {
                       client.leaveGame(game);
                       alive = false;
                       }
                     */

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
							final int choicePl = robberStrategy.chooseRobberVictim
									(msg.getChoices(), msg.canChooseNone());
							counter = 0;
							client.choosePlayer(game, choicePl);
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
		}
		else
		{
			System.out.println("AGG! NO PINGER!");
		}

		clean();
	}

	// Variant of SOCRobotBrain.rollOrPlayKnightOrExpectDice
	private void rollOrPlayKnightOrExpectDice() {
		expectROLL_OR_CARD = false;

		if ((!waitingForOurTurn) && ourTurn) {
			if (!expectPLAY1 && !expectDISCARD && !expectPLACING_ROBBER && !(expectDICERESULT && (counter < 4000))) {
				if (playKightBeforeDie()) {
					playKnightCard();  // sets expectPLACING_ROBBER, waitingForGameState
				} else {
					expectDICERESULT = true;
					counter = 0;
					client.rollDice(game);
				}
			}
		} else {
			expectDICERESULT = true;
		}
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

	protected void pickFreeResources(int numChoose) {
		// Swynfel TODO: Have AI choose
		super.pickFreeResources(numChoose);
	}

	protected boolean chooseFreeResources(final SOCResourceSet targetResources, final int numChoose,
			final boolean clearResChoices) {
		// Swynfel TODO: Have AI choose
		return super.chooseFreeResources(targetResources, numChoose, clearResChoices);
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
			System.out.println(message);
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
		utils.newState(utils.encodePossibleActions(game, getOurPlayerData()));

		boolean[] possibleActions = utils.getActions();
		int actionId = chooseAction(possibleActions);
		if(!possibleActions[actionId]) {
			System.out.print("ERROR, picked action " + actionId +" that isn't legal, replaced by action 0 (pass turn)");
			actionId = 0;
		}

		SOCAction action = new SOCAction(utils.format, game.getBoard(), actionId);

		if(action.type == SOCAction.PASS) {
			return;
		}

		execute(action);
	}

	protected void finished() {
		utils.endState();
	}

	protected abstract int chooseAction(boolean[] possibleActions);

	protected abstract boolean playKightBeforeDie();
		
	protected void execute(SOCAction action) {
		switch(action.type) {
		case(SOCAction.BUILD_ROAD):
			buildRoad(action.parameters[0]);
			return;
		case(SOCAction.BUILD_SETTLEMENT):
			buildSettelement(action.parameters[0]);
			return;
		case(SOCAction.BUILD_CITY):
			buildCity(action.parameters[0]);
			return;
		case(SOCAction.BUY_DEVELOPMENT_CARD):
			buyDevCard();
			return;
		case(SOCAction.BANK_TRADE):
			tradeBank(action.parameters[0], action.parameters[1]);
			return;
		}
	}
	
	private void buildPiece() {
		waitingForGameState = true;
		actionMsg("Building", whatWeWantToBuild.toString());
		client.buildRequest(game, whatWeWantToBuild.getType());
	}
	
	protected void buildCity(int position) {
		whatWeWantToBuild = new SOCCity(ourPlayerData, position, game.getBoard());
		
		expectPLACING_CITY = true;
		buildPiece();
	}
	
	protected void buildSettelement(int position) {
		whatWeWantToBuild = new SOCSettlement(ourPlayerData, position, game.getBoard());

		expectPLACING_SETTLEMENT = true;
		buildPiece();
	}
	
	protected void buildRoad(int position) {
		whatWeWantToBuild = new SOCRoad(ourPlayerData, position, game.getBoard());

		expectPLACING_ROAD = true;
		buildPiece();
	}
	
	protected void buyDevCard() {
		client.buyDevCard(game);

		waitingForDevCard = true;
		actionMsg("Buying DevCard");
	}

	protected void tradeBank(int give, int want) {
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
		tradeBank(giveSet, wantSet);
	}

	protected void tradeBank(SOCResourceSet give, SOCResourceSet want) {
		client.bankTrade(game, give, want);
		actionMsg("BankTrade", resourceString(give) + " ---> " + resourceString(want));
		waitingForTradeMsg = true;
	}
}
