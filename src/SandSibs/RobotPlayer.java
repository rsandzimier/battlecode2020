package SandSibs;
import battlecode.common.*;
import java.util.ArrayList;
import java.util.List;

public strictfp class RobotPlayer {
    static RobotController rc;

    static Direction[] directions = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTHEAST, Direction.NORTHWEST, Direction.SOUTHEAST, Direction.SOUTHWEST};
    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};

    static int turnCount;

    private static class Square {
        int turn;
        boolean flooded;
        int elevation; 
        int soup;
        int pollution;
        // unit/building
    }

    static Square[][] map = new Square[GameConstants.MAP_MAX_WIDTH][GameConstants.MAP_MAX_HEIGHT];

    static List<MapLocation> soup_deposits = new ArrayList<>();

    static MapLocation HQ_loc;

    static Direction last_move_direction = Direction.CENTER;

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
        // MapLocation l = rc.getLocation();
        // for (int i = 0; i != 64; i++)
        //    for (int j = 0; j != 64; j++)
        //       if (i <= l.x && j <= l.y)
        //       {
        //          // map[i][j].discovered = true;
        //          // System.out.println("New indicator at: "+i+", "+j+". Used " + Clock.getBytecodeNum() + " bytecodes so far");
        //          System.out.println("Start:"+Clock.getBytecodeNum());

        //          MapLocation ml = new MapLocation(i,j);
        //          System.out.println("Middle:"+Clock.getBytecodeNum());

        //          rc.setIndicatorDot(ml,255,0,0);
        //          System.out.println("End:"+Clock.getBytecodeNum());

        //       }
        // MapLocation ml = new MapLocation(l.x,l.y);
        // rc.setIndicatorDot(ml,0,0,255);
        updateMap();

        for (Direction dir : directions){
                tryBuild(RobotType.MINER, dir)
        }
    }

    static void runMiner() throws GameActionException {
        // tryBlockchain();
        // 
        // if (tryMove(randomDirection()))
           // System.out.println("I moved!");
        //tryBuild(randomSpawnedByMiner(), randomDirection());
        // for (Direction dir : directions)
        //     tryBuild(RobotType.FULFILLMENT_CENTER, dir);
        // for (Direction dir : directions)
        //     if (tryRefine(dir))
        //         System.out.println("I refined soup! " + rc.getTeamSoup());
        // for (Direction dir : directions)
        //     if (tryMine(dir))
        //         System.out.println("I mined soup! " + rc.getSoupCarrying());
        
        // UPDATE CRITICAL MAP SECTION


        // DO ACTION


        // EXTRA COMPUTATION
        System.out.println("START: "+Clock.getBytecodeNum());
        if (tryRefine()){
            ;        
        }        
        else if (rc.getSoupCarrying() == 100 && HQ_loc != null){ // Need GAME CONSTANT
            if (tryMove(rc.getLocation().directionTo(HQ_loc))){
                ;
            }
            else{
                tryMove(randomDirection());
            }
        }
        else if (tryMine()){
        ;        
        }
        else if (soup_deposits.size() > 0){
            MapLocation loc = soup_deposits.get(0);
            if (tryMove(rc.getLocation().directionTo(loc))){
                ;
            }
            else{
                tryMove(randomDirection());
            }
        }
        else if(buildDS && rc.getTeamSoup() >= 150){
            if(tryBuild(RobotType.DESIGN_SCHOOL,Direction.NORTH)){
                buildDS = false;
            }
        }
        else{
            tryMove(randomDirection());
        }   
        System.out.println("MOVE: "+Clock.getBytecodeNum());

        updateMapDiscovered();
        System.out.println("DISCOVER: "+Clock.getBytecodeNum());
        updateMapSoupDeposits();
        System.out.println("SOUP: "+Clock.getBytecodeNum());
        updateMapRobots(); 
        System.out.println("ROBOTS: "+Clock.getBytecodeNum());

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

    static void updateMap() throws GameActionException{
        System.out.println("START"+Clock.getBytecodeNum());

        MapLocation l = rc.getLocation();

        int rad_sq = rc.getType().sensorRadiusSquared; 
        double rad = Math.sqrt(rad_sq);
        int rad_int = (int) rad;

        int min_x = l.x - rad_int;
        int max_x = l.x + rad_int + 1;
        int min_y = l.y - rad_int;
        int max_y = l.y + rad_int + 1;

        // MapLocation ml = new MapLocation(l.x,l.y);
        System.out.println("SETUP"+Clock.getBytecodeNum());
        System.out.println("X"+(max_x-min_x-1)+"Y"+(max_y-min_y-1));

        for (int i = min_x; i < max_x; i++)
        {
            for (int j = min_y; j < max_y; j++)
            {
                MapLocation ml = new MapLocation(i,j);
                if (rc.canSenseLocation(ml) && rc.onTheMap(ml))
                {
                // Square sq = 
                // map[i][j] = new Square(1,false,0,0);

                map[i][j].turn = rc.getRoundNum();
                map[i][j].elevation = rc.senseElevation(ml);
                map[i][j].flooded = rc.senseFlooding(ml);
                // map[i][j].pollution = rc.sensePollution(ml);
                map[i][j].soup = rc.senseSoup(ml);

                if (map[i][j].soup > 0){
                    boolean new_soup = true;
                    for (MapLocation loc : soup_deposits){
                        if (ml.equals(loc)){
                            new_soup = false;
                            break;
                        }
                    }
                    soup_deposits.add(ml);
                }
                // System.out.println(Clock.getBytecodeNum());

                // System.out.println("Coords: "+i+", "+j+" Turn: "+map[i][j].turn+" Elevation: "+map[i][j].elevation+" Flood: "+map[i][j].flooded+ " Pollution: "+map[i][j].pollution+" soup: "+map[i][j].soup);
                }
            }
        }
        System.out.println("SQUARES"+Clock.getBytecodeNum());

        
        System.out.println("SOUP"+Clock.getBytecodeNum());


        RobotInfo[] nearby_robots = rc.senseNearbyRobots();
        for (RobotInfo nr : nearby_robots){
            if (HQ_loc == null && nr.getType() == RobotType.HQ){
                HQ_loc = nr.getLocation();
            }
        }
        System.out.println("ROBOTS"+Clock.getBytecodeNum());

        System.out.println("END"+Clock.getBytecodeNum());
    } 

    static void updateSquare(int x, int y) throws GameActionException{
        MapLocation ml = new MapLocation(x,y);
        // System.out.println("Try to Update Square at: "+x+", "+y);
        // rc.setIndicatorDot(ml,0,0,255);

        if (rc.canSenseLocation(ml) && rc.onTheMap(ml)){
            // Square sq = 
            // map[i][j] = new Square(1,false,0,0);
            int soup_old = map[x][y].soup;
            map[x][y].turn = rc.getRoundNum();
            map[x][y].elevation = rc.senseElevation(ml);
            map[x][y].flooded = rc.senseFlooding(ml);
            // map[i][j].pollution = rc.sensePollution(ml);
            map[x][y].soup = rc.senseSoup(ml);

            // System.out.println("Updated Square at: "+x+", "+y);
            // System.out.println("Num soup deposits: "+soup_deposits.size());
            // rc.setIndicatorDot(ml,255,0,0);
            if (map[x][y].soup > 0){
                boolean new_soup = true;
                for (MapLocation loc : soup_deposits){
                    if (ml.equals(loc)){
                        new_soup = false;
                        break;
                    }
                }
                if (new_soup){
                    soup_deposits.add(ml);
                }
            }
            if (soup_old != 0 && map[x][y].soup == 0){
                for (int i = 0; i < soup_deposits.size(); i++){
                    MapLocation soup_deposit_location = soup_deposits.get(i);
                    if (ml.equals(soup_deposit_location)){
                        soup_deposits.remove(i);
                        break;
                    }
                } 
            }
        }
    }

    static void updateMapDiscovered() throws GameActionException{
        MapLocation current_location = rc.getLocation().subtract(last_move_direction);

        int rad_sq = rc.getCurrentSensorRadiusSquared(); 
        int rad_int = (int) Math.sqrt(rad_sq);

        // int min_x = l.x - rad_int;
        // int max_x = l.x + rad_int + 1;
        // int min_y = l.y - rad_int;
        // int max_y = l.y + rad_int + 1;


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
    } 

    static void updateMapSoupDeposits() throws GameActionException{
        for (int i =  soup_deposits.size() - 1; i >= 0; i--){
            MapLocation loc = soup_deposits.get(i);
            updateSquare(loc.x, loc.y);
        } 
    }

    static void updateMapRobots() throws GameActionException{
        RobotInfo[] nearby_robots = rc.senseNearbyRobots();
        for (RobotInfo nr : nearby_robots){
            if (HQ_loc == null && nr.getType() == RobotType.HQ){
                HQ_loc = nr.getLocation();
            }
        }
    }    

    static boolean tryMove() throws GameActionException {
        for (Direction dir : directions)
            if (tryMove(dir))
                return true;
        return false;
        // MapLocation loc = rc.getLocation();
        // if (loc.x < 10 && loc.x < loc.y)
        //     return tryMove(Direction.EAST);
        // else if (loc.x < 10)
        //     return tryMove(Direction.SOUTH);
        // else if (loc.x > loc.y)
        //     return tryMove(Direction.WEST);
        // else
        //     return tryMove(Direction.NORTH);
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
        /*if (turnCount < 3) {
            int[] message = new int[10];
            for (int i = 0; i < 10; i++) {
                message[i] = 123;
            }
            if (rc.canSubmitTransaction(message, 10))
                rc.submitTransaction(message, 10);
        }*/
        // System.out.println(rc.getRoundMessages(turnCount-1));
    }
}
