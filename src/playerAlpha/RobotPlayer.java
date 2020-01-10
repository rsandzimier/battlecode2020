package PlayerAlpha;
import battlecode.common.*;

public strictfp class RobotPlayer {

    static RobotController rc;

    static Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST
    };

    static int turnCount;
    
    static boolean buildMiners = true, buildDesignSchool = true;
    static boolean moveMinerLeft = true, moveMinerDown = true;
    static boolean buildFulfillmentCenter = true, buildDrone = true;
    static boolean firstMove = true, buildLSs = true;
    static int moveMinerDownC = 0, LSCount = 0;
    
    /*IMPORTANT*/
    static boolean LSGoNorth = false, LSGoSouth = false, LSGoEast = false, LSGoWest = false, LSDigTime = true, moveOnce = false;
    static int LSGoNorthCount = 0, LSGoSouthCount = 0, LSGoEastCount = 0, LSGoWestCount = 0;

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

        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                // Here, we've separated the controls into a different method for each RobotType.
                // You can add the missing ones or rewrite this into your own control structure.
                switch (rc.getType()) {
                    case HQ:                 runHQ();                break;
                    case MINER:              runMiner();             break;
                    case DESIGN_SCHOOL:      runDesignSchool();      break;
                    case FULFILLMENT_CENTER: runFulfillmentCenter(); break;
                    case LANDSCAPER:         runLandscaper();        break;
                    case DELIVERY_DRONE:     runDeliveryDrone();     break;
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
        if(buildMiners){
            if (rc.isReady() && rc.canBuildRobot(RobotType.MINER, Direction.NORTH)) {
                rc.buildRobot(RobotType.MINER, Direction.NORTH);
                buildMiners = false;
            }
        }
    }

    static void runMiner() throws GameActionException {
        if(buildDesignSchool){
            if(rc.canBuildRobot(RobotType.DESIGN_SCHOOL,Direction.EAST) && rc.isReady()){
                rc.buildRobot(RobotType.DESIGN_SCHOOL,Direction.EAST);
                buildDesignSchool = false;
            }
        }
        else if(moveMinerLeft){
            if(rc.isReady() && rc.canMove(Direction.WEST)) {
                rc.move(Direction.WEST);
                moveMinerLeft = false;
            }
        }
        else if(moveMinerDown){
            if(rc.isReady() && rc.canMove(Direction.SOUTH)) {
                rc.move(Direction.SOUTH);
                moveMinerDownC++;
                if(moveMinerDownC == 2) moveMinerDown = false;
            }
        }
        else if(buildFulfillmentCenter){
            if(rc.canBuildRobot(RobotType.FULFILLMENT_CENTER,Direction.EAST) && rc.isReady()){
                rc.buildRobot(RobotType.FULFILLMENT_CENTER,Direction.EAST);
                buildFulfillmentCenter = false;
            }
        }
        else;
    }
    
    static void runDesignSchool() throws GameActionException {
        if(buildLSs){
            if(rc.canBuildRobot(RobotType.LANDSCAPER,Direction.NORTH) && rc.isReady()){
                rc.buildRobot(RobotType.LANDSCAPER,Direction.NORTH);
                LSCount++;
            }
            else if(rc.canBuildRobot(RobotType.LANDSCAPER,Direction.EAST) && rc.isReady()){
                rc.buildRobot(RobotType.LANDSCAPER,Direction.EAST);
                LSCount++;
            }
            else if(rc.canBuildRobot(RobotType.LANDSCAPER,Direction.SOUTH) && rc.isReady()){
                rc.buildRobot(RobotType.LANDSCAPER,Direction.SOUTH);
                LSCount++;
            }
            else;
        }
        if(LSCount == 15) buildLSs = false;
    }
    
    static void runFulfillmentCenter() throws GameActionException {
        if(buildDrone){
            if(rc.canBuildRobot(RobotType.DELIVERY_DRONE,Direction.EAST) && rc.isReady()){
                rc.buildRobot(RobotType.DELIVERY_DRONE,Direction.EAST);
                buildDrone = false;
            }
        }
    }
    
    static void runDeliveryDrone() throws GameActionException {
        if(rc.canDropUnit(Direction.EAST) && rc.isReady()){
            rc.dropUnit(Direction.EAST);
        }
        else if(rc.canPickUpUnit(rc.senseRobotAtLocation(rc.getLocation().add(Direction.NORTH)).getID()) && rc.isReady()){
            rc.pickUpUnit(rc.senseRobotAtLocation(rc.getLocation().add(Direction.NORTH)).getID());
        }
        else;
    }
    
    static void runLandscaper() throws GameActionException {
        if(firstMove){
            if(rc.canMove(Direction.WEST)){
                LSGoWest= true;
                LSGoWestCount = 1;
                firstMove = false;
            }
            else if(rc.canMove(Direction.NORTH)){
                LSGoNorth = true;
                LSGoNorthCount = 1;
                firstMove = false;
            }
            else;
            
        }
        
        if(LSGoNorth){
            LSGoNorthCount = buildWall(Direction.NORTH, Direction.EAST, LSGoNorthCount);
            
            if(LSGoNorthCount == 4){
                LSGoNorthCount = 0;
                LSGoNorth = false;
                LSGoWest = true;
            }
        }
        else if(LSGoWest){
            LSGoWestCount = buildWall(Direction.WEST, Direction.NORTH, LSGoWestCount);
            
            if(LSGoWestCount == 4){
                LSGoWestCount = 0;
                LSGoWest = false;
                LSGoSouth = true;
            }
        }
        else if(LSGoSouth){
            LSGoSouthCount = buildWall(Direction.SOUTH, Direction.WEST, LSGoSouthCount);
            
            if(LSGoSouthCount == 4){
                LSGoSouthCount = 0;
                LSGoSouth = false;
                LSGoEast = true;
            }
        }
        else if(LSGoEast){
            LSGoEastCount = buildWall(Direction.EAST, Direction.SOUTH, LSGoEastCount);
            
            if(LSGoEastCount == 4){
                LSGoEastCount = 0;
                LSGoEast = false;
                LSGoNorth = true;
            }
        }
        else;
        
    }
    
    static int buildWall(Direction move, Direction dig, int LSGoAmount) throws GameActionException {
        int elevationOfMe = rc.senseElevation(rc.getLocation());
        int elevationOfMoveSpace = rc.senseElevation(rc.getLocation().add(move));
        
        if(LSDigTime && rc.isReady() && rc.canDigDirt(dig)){
            rc.digDirt(dig);
            LSDigTime = false;
        }
        else if(!(LSDigTime) && rc.isReady() && rc.canDepositDirt(move) && !(moveOnce)){
            rc.depositDirt(move);
            LSDigTime = true;
            moveOnce = true;
        }
        else if(elevationOfMe - elevationOfMoveSpace > 3 || elevationOfMe - elevationOfMoveSpace < -3){
            if(rc.isReady() && rc.canDigDirt(move)){
                rc.digDirt(move);
            }
        }
        else{
            if(rc.isReady() && rc.canMove(move)){
                rc.move(move);
                LSGoAmount++;
                moveOnce = false;
            }
        }
        
        return LSGoAmount;
    }
    
    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }
}


/*
if(LSGoNorth){
        if(LSDigTime && rc.isReady() && rc.canDigDirt(Direction.EAST)){
            rc.digDirt(Direction.EAST);
            LSDigTime = false;
        }
        else if(!(LSDigTime) && rc.isReady() && rc.canDepositDirt(Direction.NORTH) && !(moveOnce)){
            rc.depositDirt(Direction.NORTH);
            LSDigTime = true;
            moveOnce = true;
        }
        else{
            if(rc.isReady() && rc.canMove(Direction.NORTH)){
                rc.move(Direction.NORTH);
                LSGoNorthCount++;
                moveOnce = false;
            }
        }
        if(LSGoNorthCount == 4){
            LSGoNorthCount = 0;
            LSGoNorth = false;
            LSGoWest = true;
        }
    }

    else if(LSGoWest){
        if(LSDigTime && rc.isReady() && rc.canDigDirt(Direction.NORTH)){
            rc.digDirt(Direction.NORTH);
            LSDigTime = false;
        }
        else if(!(LSDigTime) && rc.isReady() && rc.canDepositDirt(Direction.WEST) && !(moveOnce)){
            rc.depositDirt(Direction.WEST);
            LSDigTime = true;
            moveOnce = true;
        }
        else{
            if(rc.isReady() && rc.canMove(Direction.WEST)){
                rc.move(Direction.WEST);
                LSGoWestCount++;
                moveOnce = false;
            }
        }
        if(LSGoWestCount == 4){
            LSGoWestCount = 0;
            LSGoWest = false;
            LSGoSouth = true;
        }
    }

    else if(LSGoSouth){
        if(LSDigTime && rc.isReady() && rc.canDigDirt(Direction.WEST)){
            rc.digDirt(Direction.WEST);
            LSDigTime = false;
        }
        else if(!(LSDigTime) && rc.isReady() && rc.canDepositDirt(Direction.SOUTH) && !(moveOnce)){
            rc.depositDirt(Direction.SOUTH);
            LSDigTime = true;
            moveOnce = true;
        }
        else{
            if(rc.isReady() && rc.canMove(Direction.SOUTH)){
                rc.move(Direction.SOUTH);
                LSGoSouthCount++;
                moveOnce = false;
            }
        }
        if(LSGoSouthCount == 4){
            LSGoSouthCount = 0;
            LSGoSouth = false;
            LSGoEast = true;
        }
    }

    else if(LSGoEast){
        if(LSDigTime && rc.isReady() && rc.canDigDirt(Direction.SOUTH)){
            rc.digDirt(Direction.SOUTH);
            LSDigTime = false;
        }
        else if(!(LSDigTime) && rc.isReady() && rc.canDepositDirt(Direction.EAST) && !(moveOnce)){
            rc.depositDirt(Direction.EAST);
            LSDigTime = true;
            moveOnce = true;
        }
        else{
            if(rc.isReady() && rc.canMove(Direction.EAST)){
                rc.move(Direction.EAST);
                LSGoEastCount++;
                moveOnce = false;
            }
        }
        if(LSGoEastCount == 4){
            LSGoEastCount = 0;
            LSGoEast = false;
            LSGoNorth = true;
        }
    }
}
 
*/
