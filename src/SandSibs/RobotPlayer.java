package SandSibs;
import battlecode.common.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap; 
import java.util.Map; 
import java.util.HashSet; 
import java.util.LinkedHashSet; 

import java.util.Iterator; 


public strictfp class RobotPlayer {
    static RobotController rc;

    static Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTHEAST, Direction.NORTHWEST, Direction.SOUTHEAST, Direction.SOUTHWEST};
    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};

    static int turnCount;

    static class Square {
        int turn;
        boolean flooded;
        int elevation; 
        int soup;
        int pollution;
        // unit/building
    }

    static class pathResult{
        Direction direction;
        int steps;
        MapLocation end_location;

        public pathResult(Direction dir, int s, MapLocation end) {
            direction = dir;
            steps = s;
            end_location = end;
        }
    }


    static Square[][] map = new Square[GameConstants.MAP_MAX_WIDTH][GameConstants.MAP_MAX_HEIGHT];

    // static List<MapLocation> soup_deposits = new ArrayList<>();
    static HashSet<MapLocation> soup_deposits = new HashSet<MapLocation>(); 
    static HashSet<MapLocation> visited = new HashSet<MapLocation>(); 

    
    static MapLocation HQ_loc;

    static Direction last_move_direction = Direction.CENTER;

    static int num_miners;

    static MapLocation goal_location = new MapLocation(0,0);

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;

        turnCount = 0;

        num_miners = 0;



        System.out.println("I'm a " + rc.getType() + " and I just got created!");

        for (int i = 0; i != rc.getMapWidth(); i++)
        {
            for (int j = 0; j != rc.getMapHeight(); j++)
            {
                map[i][j] = new Square();
            }
         }

        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                // Here, we've separated the controls into a different method for each RobotType.
                // You can add the missing ones or rewrite this into your own control structure.
                System.out.println("I'm a " + rc.getType() + "! Location " + rc.getLocation());
                switch (rc.getType()) {
                    case HQ:                 runHQ();                break;
                    case MINER:              runMiner();             break;
                    case REFINERY:           runRefinery();          break;
                    case VAPORATOR:          runVaporator();         break;
                    case DESIGN_SCHOOL:      runDesignSchool();      break;
                    case FULFILLMENT_CENTER: runFulfillmentCenter(); break;
                    case LANDSCAPER:         runLandscaper();        break;
                    case DELIVERY_DRONE:     runDeliveryDrone();     break;
                    case NET_GUN:            runNetGun();            break;
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
        }
    }

    static void runHQ() throws GameActionException {
        for (Direction dir : directions)
            if (num_miners < 5 && tryBuild(RobotType.MINER, dir)){
                num_miners++;
            }
    }

    static void runMiner() throws GameActionException {
        if (tryRefine()){
            ;        
        }        
        else if (rc.getSoupCarrying() == 100 && HQ_loc != null){ // Need GAME CONSTANT
            moveToLocationUsingBugPathing(HQ_loc);
        }
        else if (tryMine()){
        ;        
        }
        else if (soup_deposits.size() > 0){
            MapLocation loc = soup_deposits.iterator().next();

            moveToLocationUsingBugPathing(loc);
        }
        else{
            tryMove(randomDirection());
        }   
        updateMapDiscovered();
        updateMapGoalLocation();
        // updateMapSoupDeposits();
        updateMapRobots(); 
    }

    static void runRefinery() throws GameActionException {
        // System.out.println("Pollution: " + rc.sensePollution(rc.getLocation()));
    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {

    }

    static void runFulfillmentCenter() throws GameActionException {
        /*for (Direction dir : directions)
            tryBuild(RobotType.DELIVERY_DRONE, dir);
            */
    }

    static void runLandscaper() throws GameActionException {

    }

    static void runDeliveryDrone() throws GameActionException {
        /*Team enemy = rc.getTeam().opponent();
        if (!rc.isCurrentlyHoldingUnit()) {
            // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
            RobotInfo[] robots = rc.senseNearbyRobots(GameConstants.DELIVERY_DRONE_PICKUP_RADIUS_SQUARED, enemy);

            if (robots.length > 0) {
                // Pick up a first robot within range
                rc.pickUpUnit(robots[0].getID());
                System.out.println("I picked up " + robots[0].getID() + "!");
            }
        } else {
            // No close robots, so search for robots within sight radius
            tryMove(randomDirection());
        }*/
    }

    static void runNetGun() throws GameActionException {

    }

    /**
     * Returns a random Direction.
     *
     * @return a random Direction
     */
    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    /**
     * Returns a random RobotType spawned by miners.
     *
     * @return a random RobotType
     */
    static RobotType randomSpawnedByMiner() {
        return spawnedByMiner[(int) (Math.random() * spawnedByMiner.length)];
    }

    static void updateSquare(int x, int y) throws GameActionException{
        MapLocation ml = new MapLocation(x,y);

        if (rc.canSenseLocation(ml) && rc.onTheMap(ml)){
            int soup_old = map[x][y].soup;
            map[x][y].turn = rc.getRoundNum();
            map[x][y].elevation = rc.senseElevation(ml);
            map[x][y].flooded = rc.senseFlooding(ml);
            // map[i][j].pollution = rc.sensePollution(ml);
            map[x][y].soup = rc.senseSoup(ml);

            if (map[x][y].soup > 0){
                soup_deposits.add(ml);
            }
            if (soup_old != 0 && map[x][y].soup == 0){
                soup_deposits.remove(ml);
            }
        }
    }

    static void updateMapDiscovered() throws GameActionException{
        MapLocation current_location = rc.getLocation().subtract(last_move_direction);

        int rad_sq = rc.getCurrentSensorRadiusSquared(); 
        int rad_int = (int) Math.sqrt(rad_sq);

        if (last_move_direction.getDeltaY() != 0)
        {
            int signY = last_move_direction.getDeltaY();
            current_location = new MapLocation(current_location.x, current_location.y + signY);
            int min_x = current_location.x - rad_int;
            int max_x = current_location.x + rad_int + 1;

            for (int x = min_x; x < max_x; x++)
            {
                int dx = x - current_location.x;
                int dy = (int) Math.sqrt(rad_sq - dx*dx);

                int y = current_location.y + dy*signY; 

                updateSquare(x,y);
            }
        }
        if (last_move_direction.getDeltaX() != 0)
        {
            int signX = last_move_direction.getDeltaX();
            current_location = new MapLocation(current_location.x+signX, current_location.y);

            int min_y = current_location.y - rad_int;
            int max_y = current_location.y + rad_int + 1;

            for (int y = min_y; y < max_y; y++)
            {
                int dy = y - current_location.y;
                int dx = (int) Math.sqrt(rad_sq - dy*dy);

                int x = current_location.x + dx*signX; 

                updateSquare(x,y);
            }
        }
        last_move_direction = Direction.CENTER;
    } 

    static void updateMapSoupDeposits() throws GameActionException{
        for (MapLocation loc : soup_deposits){
            updateSquare(loc.x, loc.y);
        } 
    }


    static void updateMapGoalLocation() throws GameActionException{
        updateSquare(goal_location.x, goal_location.y);
    }


    static void updateMapRobots() throws GameActionException{
        RobotInfo[] nearby_robots = rc.senseNearbyRobots();
        for (RobotInfo nr : nearby_robots){
            if (HQ_loc == null && nr.getType() == RobotType.HQ){
                HQ_loc = nr.getLocation();
            }
        }
    }   

    static void moveToLocationUsingBugPathing(MapLocation location) throws GameActionException{
        if (!goal_location.equals(location))
        {
            goal_location = location;
            visited.clear();
        }
        pathResult path_result_left = bugPathPlan(location,true);
        pathResult path_result_right = bugPathPlan(location,false);

        int left_steps = path_result_left.steps + Math.max(Math.abs(path_result_left.end_location.x - location.x), Math.abs(path_result_left.end_location.y - location.y));
        int right_steps = path_result_right.steps + Math.max(Math.abs(path_result_right.end_location.x - location.x), Math.abs(path_result_right.end_location.y - location.y));

        if (left_steps <= right_steps){
            tryMove(path_result_left.direction);
        }
        else{
            tryMove(path_result_right.direction);
        }
        visited.add(rc.getLocation());
    }

    static pathResult bugPathPlan(MapLocation goal, boolean turn_left) throws GameActionException {
        MapLocation current_location = rc.getLocation();
        Direction dir = current_location.directionTo(goal);
        HashSet<MapLocation> visited_plan = new HashSet<MapLocation>();

        visited_plan.add(current_location);

        Direction first_dir = Direction.CENTER;

        int num_steps = 0;

        while(true){

            for (int i = 0; i != directions.length; i++){
                MapLocation destination = current_location.add(dir);

                if (!rc.canSenseLocation(destination) || (!rc.isLocationOccupied(destination) && !rc.senseFlooding(destination) && 
                    Math.abs(rc.senseElevation(destination)-rc.senseElevation(current_location)) <= 3 && 
                    !visited.contains(destination) &&! visited_plan.contains(destination))){

                    current_location = destination;
                    visited_plan.add(current_location);
                    rc.setIndicatorDot(current_location,255,0,0);
                    if (first_dir == Direction.CENTER)
                        first_dir = dir;
                    num_steps++;
                    break;
                }
                
                if (turn_left)
                    dir = dir.rotateLeft();
                else 
                    dir = dir.rotateRight();

                if (i == directions.length - 1){
                    return new pathResult(Direction.CENTER,0,rc.getLocation());
                }
            }
            dir = current_location.directionTo(goal);

            if (current_location.isAdjacentTo(goal)){
                return new pathResult(first_dir,num_steps,current_location);
            }
            if (!rc.canSenseLocation(current_location))
            {
                return new pathResult(first_dir,num_steps,current_location);
            }

        }
        // return true;
    }

    static boolean tryMove() throws GameActionException {
        for (Direction dir : directions)
            if (tryMove(dir))
                return true;
        return false;
    }

    /**
     * Attempts to move in a given direction.
     *
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryMove(Direction dir) throws GameActionException {
        // System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.isReady() && rc.canMove(dir)) {
            rc.move(dir);
            last_move_direction = dir;
            return true;
        } else return false;
    }

    /**
     * Attempts to build a given robot in a given direction.
     *
     * @param type The type of the robot to build
     * @param dir The intended direction of movement
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir)) {
            rc.buildRobot(type, dir);
            return true;
        } else return false;
    }

    /**
     * Attempts to mine soup in a given direction.
     *
     * @param dir The intended direction of mining
     * @return true if a move was performed
     * @throws GameActionException
     */

    static boolean tryMine() throws GameActionException {
        for (Direction dir : directions)
            if (tryMine(dir))
                return true;
        return false;
    }

    static boolean tryMine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMineSoup(dir)) {
            rc.mineSoup(dir);
            return true;
        } else return false;
    }

    static boolean tryRefine() throws GameActionException {
        for (Direction dir : directions)
            if (tryRefine(dir))
                return true;
        return false;
    }

    /**
     * Attempts to refine soup in a given direction.
     *
     * @param dir The intended direction of refining
     * @return true if a move was performed
     * @throws GameActionException
     */
    static boolean tryRefine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositSoup(dir)) {
            rc.depositSoup(dir, rc.getSoupCarrying());
            return true;
        } else return false;
    }


    static void tryBlockchain() throws GameActionException {
        if (turnCount < 3) {
            int[] message = new int[10];
            for (int i = 0; i < 10; i++) {
                message[i] = 123;
            }
            if (rc.canSubmitTransaction(message, 10))
                rc.submitTransaction(message, 10);
        }
        // System.out.println(rc.getRoundMessages(turnCount-1));
    }
}
