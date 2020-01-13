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
    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL, RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};
    static Direction[] setWallDirections = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
    
    static MapLocation droneSpawnLocation;
    
    static int turnCount;
    static int helpUnitID;
    static boolean moveLS = true, wallLocSet = true, helpUnit = false;
    static boolean enemyUnitInDrone = false, allyLandscaperUnitInDrone = false;
    static int LSBuild = 0, BuildDrone = 0;
    
    static class Square {
        int turn;
        boolean flooded;
        int elevation;
        int soup;
        int pollution;
        // unit/building
    }

    static class PathResult{
        Direction direction;
        int steps;
        MapLocation end_location;

        public PathResult(Direction dir, int s, MapLocation end) {
            direction = dir;
            steps = s;
            end_location = end;
        }
    }

    enum Phase
    {
        EARLY;
    }
    enum MissionType
    {
        SCOUT, SCOUT_MINE, MINE, BUILD; 
    }
    enum ReportType
    {
        SOUP, ROBOT, NO_ROBOT, MISSION_STATUS; 
    }
    enum Symmetry
    {
        HORIZONTAL, VERTICAL, ROTATIONAL; 
    }   
    enum Label
    {
        MISSION_TYPE, ROBOT_ID, LOCATION, ROBOT_TYPE, DISTANCE, REPORT_TYPE, ROBOT_TEAM, MISSION_SUCCESSFUL, SOUP_AMOUNT, END_MESSAGE;
    }

    static class Mission{
        ArrayList<Integer> robot_ids = new ArrayList<Integer>();
        MissionType mission_type;
        MapLocation location;
        RobotType robot_type;
        int distance; 
    }    

    static class Report{
        ReportType report_type;
        MapLocation location;
        RobotType robot_type;
        boolean robot_team; // True if ours, false if opponent
        int robot_id;
        int soup_amount;
        MissionType mission_type;
        boolean successful; 
    }

    static class RobotStatus{
        int robot_id;
        ArrayList<Mission> missions;
        MapLocation location;

        public RobotStatus(int id){
            robot_id = id;
            missions = new ArrayList<Mission>();
        }
    }

    static ArrayList<Symmetry> possible_symmetries = new ArrayList<Symmetry>();
    static MapLocation[] symmetric_HQ_locs = new MapLocation[3];
    
    static MapLocation[] wallLocation = {null, null,  null, null, null, null,  null, null, null, null,  null, null, null, null,  null, null};

    static Square[][] map = new Square[GameConstants.MAP_MAX_WIDTH][GameConstants.MAP_MAX_HEIGHT];

    // static List<MapLocation> soup_deposits = new ArrayList<>();
    static HashSet<MapLocation> soup_deposits = new HashSet<MapLocation>();
    static ArrayList<MapLocation> soup_deposits_public = new ArrayList<MapLocation>();

    static HashSet<MapLocation> visited = new HashSet<MapLocation>();

    static MapLocation HQ_loc;

    static MapLocation enemy_HQ_loc;

    static Direction last_move_direction = Direction.CENTER;

    static ArrayList<RobotStatus> miners = new ArrayList<RobotStatus>();
    static ArrayList<RobotStatus> landscapers = new ArrayList<RobotStatus>();
    static ArrayList<RobotStatus> drones = new ArrayList<RobotStatus>();

    static ArrayList<RobotStatus> refineries = new ArrayList<RobotStatus>();
    static ArrayList<RobotStatus> vaporators = new ArrayList<RobotStatus>();
    static ArrayList<RobotStatus> design_schools = new ArrayList<RobotStatus>();
    static ArrayList<RobotStatus> fulfillment_centers = new ArrayList<RobotStatus>();
    static ArrayList<RobotStatus> net_guns = new ArrayList<RobotStatus>();

    static MapLocation goal_location = new MapLocation(0,0);

    static Phase phase = Phase.EARLY;

    static ArrayList<Mission> mission_queue = new ArrayList<Mission>();
    static ArrayList<Report> report_queue = new ArrayList<Report>();

    static ArrayList<Mission> active_missions = new ArrayList<Mission>();

    static int map_width;
    static int map_height;

    static int blockchain_read_index;
    static int blockchain_password;
    static HashSet<Integer> blockchain_password_hashes = new HashSet<Integer>(); 

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

        map_width = rc.getMapWidth();
        map_height = rc.getMapHeight();

        blockchain_read_index = Math.max(rc.getRoundNum()-1,2);
        blockchain_password = 599 + rc.getTeam().ordinal();

        possible_symmetries.add(Symmetry.HORIZONTAL);
        possible_symmetries.add(Symmetry.VERTICAL);
        possible_symmetries.add(Symmetry.ROTATIONAL);

        if (rc.getType() == RobotType.HQ){
            HQ_loc = rc.getLocation();
            int mirror_x = map_width - HQ_loc.x - 1;
            int mirror_y = map_height - HQ_loc.y - 1;
            symmetric_HQ_locs[Symmetry.HORIZONTAL.ordinal()] = new MapLocation(HQ_loc.x, mirror_y);
            symmetric_HQ_locs[Symmetry.VERTICAL.ordinal()] = new MapLocation(mirror_x, HQ_loc.y);
            symmetric_HQ_locs[Symmetry.ROTATIONAL.ordinal()] = new MapLocation(mirror_x, mirror_y);
        }

        System.out.println("I'm a " + rc.getType() + " and I just got created!");

        for (int i = 0; i != map_width; i++)
        {
            for (int j = 0; j != map_height; j++)
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
                // if (enemy_HQ_loc != null)
                //     rc.setIndicatorDot(enemy_HQ_loc, 0,0,0);

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
        readBlockChain();
        updateMapRobots();
        if (phase == Phase.EARLY){
            if (miners.size() < 4){
                for (Direction dir : directions)
                    if (tryBuild(RobotType.MINER, dir)){
                        RobotInfo new_miner = rc.senseRobotAtLocation(HQ_loc.add(dir));
                        addRobotToList(miners, new_miner.getID());
                    }
            }

            boolean needsScouting[] = {false, false, false};
            if (possible_symmetries.size() > 1){
                for (Symmetry sym : possible_symmetries){
                    needsScouting[sym.ordinal()] = true;
                }
            } 

            for (RobotStatus rs : miners){
                if (rs.missions.size() > 0 && rs.missions.get(0).mission_type == MissionType.SCOUT_MINE){
                    MapLocation loc = rs.missions.get(0).location;
                    for (int i = 0; i != 3; i ++){
                        if (needsScouting[i] && loc.equals(symmetric_HQ_locs[i])){
                            needsScouting[i] = false;
                        }
                    }
                }
            }

            for(int i = 0; i !=  miners.size(); i++){
                for (int j = 0; j != 3; j++){
                    if (needsScouting[j] && (miners.get(i).missions.size() == 0  || miners.get(i).missions.get(0).mission_type == MissionType.MINE)){
                        // Assign this miner the mission
                        Mission new_mission = new Mission();
                        new_mission.mission_type = MissionType.SCOUT_MINE;
                        new_mission.location = symmetric_HQ_locs[j];
                        new_mission.robot_ids.add(miners.get(i).robot_id);
                        mission_queue.add(new_mission);
                        break;
                    }
                }
            } 
            if (fulfillment_centers.size() == 0 && !needsScouting[0] && !needsScouting[1] && !needsScouting[2]){
                for (RobotStatus rs : miners){
                    if (rs.missions.size() == 0 || rs.missions.get(0).mission_type == MissionType.MINE){
                        Mission new_mission = new Mission();
                        new_mission.mission_type = MissionType.BUILD;
                        new_mission.location = HQ_loc;
                        new_mission.robot_ids.add(rs.robot_id);
                        new_mission.robot_type = RobotType.FULFILLMENT_CENTER;
                        new_mission.distance = 1;
                        mission_queue.add(new_mission);
                        break;
                    }
                }
            }
            if (design_schools.size() == 0 && !needsScouting[0] && !needsScouting[1] && !needsScouting[2]){
                for (RobotStatus rs : miners){
                    if (rs.missions.size() == 0 || rs.missions.get(0).mission_type == MissionType.MINE){
                        Mission new_mission = new Mission();
                        new_mission.mission_type = MissionType.BUILD;
                        new_mission.location = HQ_loc;
                        new_mission.robot_ids.add(rs.robot_id);
                        new_mission.robot_type = RobotType.DESIGN_SCHOOL;
                        new_mission.distance = 1;
                        mission_queue.add(new_mission);
                        break;
                    }
                }                
            }
            // If no needsScouting and builder is free        
        }
        tryBlockchain();
        mission_queue.clear();
    }

    static void tryBuildMission(Mission mission) throws GameActionException {
        // If can build, build
        // Otherwise, path toward
        MapLocation current_location = rc.getLocation();
        for (Direction dir : directions){
            if (current_location.add(dir).isWithinDistanceSquared(mission.location, mission.distance*mission.distance) && 
                tryBuild(mission.robot_type, dir)){

                Report report = new Report();
                report.report_type = ReportType.MISSION_STATUS;
                report.mission_type = mission.mission_type;
                report.successful = true;
                report.robot_type = rc.getType();
                report_queue.add(report);

                report = new Report();
                report.report_type = ReportType.ROBOT;
                report.robot_type = mission.robot_type;
                report.robot_team = true;
                report.location = current_location.add(dir);
                report.robot_id = rc.senseRobotAtLocation(report.location).getID();
                report_queue.add(report);
                active_missions.remove(0);                
                return;
            }
        }
        moveToLocationUsingBugPathing(mission.location);
    }

    static boolean adjacentToSoup() throws GameActionException{
        for (Direction dir : directions){
            if (rc.canSenseLocation(rc.getLocation().add(dir)) && rc.senseSoup(rc.getLocation().add(dir)) > 0){
                return true;
            }
        }
        return false;
    }

    static int distanceSquaredToNearestRefinery() throws GameActionException{
        RobotInfo[] nearby_robots = rc.senseNearbyRobots();
        for (RobotInfo nr : nearby_robots){
            if (rc.getTeam() == nr.getTeam() ){
                int id = nr.getID();
                if (nr.getType() == RobotType.REFINERY){
                    RobotStatus rs = new RobotStatus(id);
                    rs.location = nr.getLocation();
                    refineries.add(rs);                
                }
            }
        }
        RobotStatus refinery = nearestRefinery();
        if (refinery == null) return 100;
        return rc.getLocation().distanceSquaredTo(refinery.location);
    }

    static RobotStatus nearestRefinery() throws GameActionException{
        int dist = 100;
        RobotStatus refinery = null;
        for (RobotStatus rs : refineries){
            int dist_rs = rc.getLocation().distanceSquaredTo(rs.location);
            if (dist_rs < dist){
                dist = dist_rs;
                refinery = rs;
            }
        }
        return refinery;
    }

    static boolean tryBuildRefinery() throws GameActionException{
        for (Direction dir : directions){
            MapLocation loc = rc.getLocation().add(dir);
            if (!loc.isWithinDistanceSquared(HQ_loc, 8) && tryBuild(RobotType.REFINERY, dir)){
                RobotStatus rs = new RobotStatus(rc.senseRobotAtLocation(loc).getID());
                rs.location = loc;
                refineries.add(rs);

                Report report = new Report();
                report.report_type = ReportType.ROBOT;
                report.robot_type = RobotType.REFINERY;
                report.location = rs.location;
                report.robot_id = rs.robot_id;
                report_queue.add(report);  
                return true;
            }
        }
        return false;
    }

    static boolean tryMineMission() throws GameActionException {
        if (tryRefine()){
            ;
        }
        else if (distanceSquaredToNearestRefinery() > 10 && adjacentToSoup() && tryBuildRefinery()){
            ; 
        }
        else if (rc.getSoupCarrying() == 100 && (HQ_loc != null || refineries.size() != 0)){
            RobotStatus nearest_refinery = nearestRefinery();
            if (nearest_refinery != null){
                moveToLocationUsingBugPathing(nearest_refinery.location);
            }
            else{
                moveToLocationUsingBugPathing(HQ_loc);
            }
        }
        else if (tryMine()){
            boolean should_report = true;
            for (MapLocation loc : soup_deposits_public){
                if (rc.getLocation().isWithinDistanceSquared(loc, 25)){
                    should_report = false;
                    break;
                }
            }     
            if (should_report){
                Report report = new Report();
                report.report_type = ReportType.SOUP;
                report.location = rc.getLocation();
                report_queue.add(report);
                soup_deposits_public.add(rc.getLocation());
            }
        }
        else if (soup_deposits.size() > 0){
            MapLocation loc = soup_deposits.iterator().next();
            moveToLocationUsingBugPathing(loc);
        }  
        else if (soup_deposits_public.size() > 0){
            MapLocation closest_loc = null;
            MapLocation current_location = rc.getLocation();
            int distance = 10000;
            for (int i = soup_deposits_public.size()-1; i >= 0; i--){
                int dist_i = current_location.distanceSquaredTo(soup_deposits_public.get(i));
                if (dist_i == 0){
                    soup_deposits_public.remove(i);
                }
                else if (dist_i < distance){
                    closest_loc = soup_deposits_public.get(i);
                    distance = dist_i;
                }
            }
            if (closest_loc == null)
                return false;
            moveToLocationUsingBugPathing(closest_loc);
        }        
        else{
            return false;
        }
        return true;
    }

    static void tryScoutMission(Mission mission) throws GameActionException {
        MapLocation location = mission.location;
        if (rc.isReady()){
            moveToLocationUsingBugPathing(location);
        }
        if (rc.canSenseLocation(location))
        {
            updateSquare(location.x, location.y);
            RobotInfo robot = rc.senseRobotAtLocation(location);
            Report report = new Report();
            report.report_type = ReportType.MISSION_STATUS;
            report.mission_type = mission.mission_type;
            report.successful = true;
            report.robot_type = rc.getType();
            report_queue.add(report);

            report = new Report();
            if (robot != null){
                report.report_type = ReportType.ROBOT;
                report.robot_type = robot.getType();
                report.robot_team = robot.getTeam() == rc.getTeam();
                report.robot_id = robot.getID();
            }
            else{
                report.report_type = ReportType.NO_ROBOT;
            }
            report.location = location;
            report_queue.add(report);
            active_missions.remove(0);
        }
    }

    static void runMiner() throws GameActionException {
        readBlockChain();

        Mission current_mission = new Mission();
        if (active_missions.size() > 0){
            current_mission = active_missions.get(0);
        }
        else{
            current_mission.mission_type = MissionType.MINE;
        }

        switch(current_mission.mission_type){
            case SCOUT:
                tryScoutMission(current_mission);
                break;
            case SCOUT_MINE:
                tryMineMission();
                tryScoutMission(current_mission);
                break;
            case BUILD:
                tryBuildMission(current_mission);
                break;
            case MINE:
            default:
                if (tryMineMission()) break;
                tryMove(randomDirection());
                break;
        }

        updateMapDiscovered();
        updateMapGoalLocation();
        // updateMapSoupDeposits();
        updateMapRobots(); 
        tryBlockchain();
    }

    static void runRefinery() throws GameActionException {
        // System.out.println("Pollution: " + rc.sensePollution(rc.getLocation()));
    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {
        if (rc.getTeamSoup() >= 350)
            for(Direction dir : directions)
                if(tryBuild(RobotType.LANDSCAPER, dir));
    }

    static void runFulfillmentCenter() throws GameActionException {
        if(BuildDrone < 2){
            for(Direction dir : directions){
                if(tryBuild(RobotType.DELIVERY_DRONE, dir)){
                    BuildDrone++;
                }
            }
        }
    }

    static void runLandscaper() throws GameActionException {
        if(HQ_loc != null && wallLocSet) {
            setWallLocations();
            wallLocSet = false;
        }
        
        if(moveLS){
            if(HQ_loc != null && rc.getLocation().isAdjacentTo(HQ_loc)) moveToLocationUsingBugPathing(HQ_loc.add(Direction.NORTH).add(Direction.NORTH).add(Direction.NORTH));
            else if(HQ_loc != null && rc.getLocation().isAdjacentTo(HQ_loc.add(HQ_loc.directionTo(rc.getLocation())))) moveLS = false;
            else if(HQ_loc != null) moveToLocationUsingBugPathing(HQ_loc);
            else tryMove(randomDirection());
        }
        
        else{
            MapLocation buildLocation = checkElevationsOfWall();
            if(buildLocation == rc.getLocation()){
                if(rc.canDepositDirt(Direction.CENTER) && rc.isReady()) rc.depositDirt(Direction.CENTER);
                else if(rc.canDigDirt(HQ_loc.directionTo(rc.getLocation())) && rc.isReady()) rc.digDirt(HQ_loc.directionTo(rc.getLocation()));
                else;
            }
            else if(rc.getLocation().isAdjacentTo(buildLocation)){
                if(rc.canDepositDirt(rc.getLocation().directionTo(buildLocation)) && rc.isReady()) rc.depositDirt(rc.getLocation().directionTo(buildLocation));
                else if(rc.canDigDirt(HQ_loc.directionTo(rc.getLocation())) && rc.isReady()) rc.digDirt(HQ_loc.directionTo(rc.getLocation()));
                else;
            }
            else {
                System.out.println("TRYING TO GO :: " + buildLocation);
                moveToLocationUsingBugPathing(buildLocation);
                for(int i=0;i<16;i++){
                    if(wallLocation[i].isAdjacentTo(rc.getLocation()) && Math.abs(rc.senseElevation(rc.getLocation()) - rc.senseElevation(wallLocation[i])) > 3){
                        if(rc.canDepositDirt(rc.getLocation().directionTo(wallLocation[i])) && rc.isReady()) rc.depositDirt(rc.getLocation().directionTo(wallLocation[i]));
                        else if(rc.canDigDirt(HQ_loc.directionTo(rc.getLocation())) && rc.isReady()) rc.digDirt(HQ_loc.directionTo(rc.getLocation()));
                        else;
                    }
                }
            }
        }
        
        updateMapDiscovered();
        updateMapGoalLocation();
        // updateMapSoupDeposits();
        updateMapRobots();
    }
    
    static void setWallLocations() throws GameActionException {
        int j = 0;
        MapLocation start = HQ_loc.add(Direction.NORTHWEST).add(Direction.NORTHWEST);
        for(int i=0; i<16; i++){
            wallLocation[i] = start;
            if(i%4 == 0 && i != 0) j++;
            start = start.add(setWallDirections[j]);
        }
    }
    
    static MapLocation checkElevationsOfWall() throws GameActionException {
        MapLocation returnLoc = rc.getLocation();
        for(int i=0; i<16; i++){
            if(rc.canSenseLocation(wallLocation[i])){
                if(rc.senseElevation(returnLoc) > rc.senseElevation(wallLocation[i]) + 3*(int)Math.sqrt(rc.getLocation().distanceSquaredTo(wallLocation[i]))){
                    returnLoc = wallLocation[i];
                }
            }
        }
        
        return returnLoc;
    }

    static void runDeliveryDrone() throws GameActionException {
        updateMapDiscovered();
        updateMapGoalLocation();
        // updateMapSoupDeposits();
        updateMapRobots();
        if(HQ_loc != null && wallLocSet) {
            setWallLocations();
            wallLocSet = false;
        }
        if(droneSpawnLocation == null) droneSpawnLocation = rc.getLocation();
        Team ourTeam = rc.getTeam();
        
        if(rc.isCurrentlyHoldingUnit() && enemyUnitInDrone){
            for(Direction dir : directions){
                if(rc.senseFlooding(rc.getLocation().add(dir)) && rc.canDropUnit(dir)) {
                    rc.dropUnit(dir);
                    enemyUnitInDrone = false;
                }
                else;
            }
            tryMove(randomDirection());
        }
        
        if(rc.isCurrentlyHoldingUnit() && allyLandscaperUnitInDrone){
            for(int i = 0; i < 16; i++){
                if(rc.getLocation().isAdjacentTo(wallLocation[i]) && rc.isReady()  && rc.canDropUnit(rc.getLocation().directionTo(wallLocation[i]))){
                    rc.dropUnit(rc.getLocation().directionTo(wallLocation[i]));
                    allyLandscaperUnitInDrone = false;
                    helpUnit = false;
                    helpUnitID = 0;
                }
            }
            tryMove(randomDirection());
        }
        
        RobotInfo[] nearby_robots = rc.senseNearbyRobots();
        for(int i=0; i < nearby_robots.length; i++){
            if(nearby_robots[i].getTeam() != ourTeam){
                if(rc.getLocation().isAdjacentTo(nearby_robots[i].getLocation()) && rc.isReady() && rc.canPickUpUnit(nearby_robots[i].getID())){
                    rc.pickUpUnit(nearby_robots[i].getID());
                    enemyUnitInDrone = true;
                }
                else moveToLocationUsingBugPathing(nearby_robots[i].getLocation());
            }
        }
        for(int i=0; i < nearby_robots.length; i++){
            if(nearby_robots[i].getTeam() == ourTeam && nearby_robots[i].getType() == RobotType.LANDSCAPER){
                if(helpUnit){
                    if(rc.getLocation().isAdjacentTo(nearby_robots[i].getLocation()) && rc.isReady() && rc.canPickUpUnit(helpUnitID)){
                        rc.pickUpUnit(helpUnitID);
                        allyLandscaperUnitInDrone = true;
                    }
                    else moveToLocationUsingBugPathing(nearby_robots[i].getLocation());
                }
                
                for(int j = 0; j < 16; j++){
                    if(nearby_robots[i].getLocation() == wallLocation[j]) helpUnit = false;
                    else;
                }
                helpUnit = true;
                helpUnitID = nearby_robots[i].getID();
            }
        }
        
        if(rc.canSenseLocation(HQ_loc)) tryMove(randomDirection());
        else moveToLocationUsingBugPathing(HQ_loc);
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

    static void addRobotToList(ArrayList<RobotStatus> robots, int id){
        if (!robotListContainsID(robots, id)){
            robots.add(new RobotStatus(id));
        }
    }

    static boolean robotListContainsID(ArrayList<RobotStatus> robots, int id){
        for (RobotStatus r : robots){
            if (r.robot_id == id){
                return true;
            }
        }
        return false;
    }

    static void updateMapRobots() throws GameActionException{
        RobotInfo[] nearby_robots = rc.senseNearbyRobots();
        RobotType robot_type = rc.getType();
        for (RobotInfo nr : nearby_robots){
            if (robot_type == RobotType.HQ && rc.getTeam() == nr.getTeam() ){
                int id = nr.getID();
                switch(nr.getType()){
                    case MINER:
                        addRobotToList(miners, id);
                        break;
                    case LANDSCAPER:
                        addRobotToList(landscapers, id);
                        break;
                    case DELIVERY_DRONE:
                        addRobotToList(drones, id);
                        break;
                    case REFINERY:
                        addRobotToList(refineries, id);
                        break;
                    case VAPORATOR:
                        addRobotToList(vaporators, id);
                        break;
                    case DESIGN_SCHOOL:
                        addRobotToList(design_schools, id);
                        break;
                    case FULFILLMENT_CENTER:
                        addRobotToList(fulfillment_centers, id);
                        break;
                    case NET_GUN:
                        addRobotToList(net_guns, id);
                        break;
                    default:
                        break;
                }

            }
            else if (HQ_loc == null && nr.getType() == RobotType.HQ){
                HQ_loc = nr.getLocation();
                int mirror_x = map_width - HQ_loc.x - 1;
                int mirror_y = map_height - HQ_loc.y - 1;
                symmetric_HQ_locs[Symmetry.HORIZONTAL.ordinal()] = new MapLocation(HQ_loc.x, mirror_y);
                symmetric_HQ_locs[Symmetry.VERTICAL.ordinal()] = new MapLocation(mirror_x, HQ_loc.y);
                symmetric_HQ_locs[Symmetry.ROTATIONAL.ordinal()] = new MapLocation(mirror_x, mirror_y);
            }
        }
    }

    static void moveToLocationUsingBugPathing(MapLocation location) throws GameActionException{
        if (!goal_location.equals(location))
        {
            goal_location = location;
            visited.clear();
        }
        PathResult path_result_left = bugPathPlan(location,true);
        PathResult path_result_right = bugPathPlan(location,false);

        int left_steps = path_result_left.steps + Math.max(Math.abs(path_result_left.end_location.x - location.x), Math.abs(path_result_left.end_location.y - location.y));
        int right_steps = path_result_right.steps + Math.max(Math.abs(path_result_right.end_location.x - location.x), Math.abs(path_result_right.end_location.y - location.y));

        if (left_steps <= right_steps){
            tryMove(path_result_left.direction);
            if (path_result_left.steps >= 100){
                visited.clear();
            }
        }
        else{
            tryMove(path_result_right.direction);
            if (path_result_right.steps >= 100){
                visited.clear();
            }
        }
        visited.add(rc.getLocation());
    }

    static boolean onTheMap(MapLocation location){
        return (location.x >= 0 && location.x < map_width && location.y >= 0 && location.y < map_height);
    }

    static PathResult bugPathPlan(MapLocation goal, boolean turn_left) throws GameActionException {
        MapLocation current_location = rc.getLocation();
        Direction dir = current_location.directionTo(goal);
        HashSet<MapLocation> visited_plan = new HashSet<MapLocation>();

        visited_plan.add(current_location);

        Direction first_dir = Direction.CENTER;

        int num_steps = 0;

        while(true){

            for (int i = 0; i != directions.length; i++){
                MapLocation destination = current_location.add(dir);
                if (onTheMap(destination) && (!rc.canSenseLocation(destination) || (!rc.isLocationOccupied(destination) &&
                    !rc.senseFlooding(destination) && Math.abs(rc.senseElevation(destination)-rc.senseElevation(current_location)) <= 3 
                    && !visited.contains(destination) &&! visited_plan.contains(destination)))){
                    current_location = destination;
                    visited_plan.add(current_location);
                    //rc.setIndicatorDot(current_location,0,255,0);
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
                    return new PathResult(first_dir,100,rc.getLocation());
                }
            }
            dir = current_location.directionTo(goal);

            if (current_location.isAdjacentTo(goal)){
                return new PathResult(first_dir,num_steps,current_location);
            }
            if (!rc.canSenseLocation(current_location))
            {
                return new PathResult(first_dir,num_steps,current_location);
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

    static int addToMessage(int[] message, int remaining_bits, int data, int data_label, int data_size){
        int data_label_size = 5;
        int shift = ((remaining_bits - 1) % 32 + 1 - data_label_size);

        message[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH -1 - (remaining_bits-1) / 32] += (data_label >>> Math.max(0, -shift)) << Math.max(0, shift);
        remaining_bits -= data_label_size - Math.max(0, -shift);

        if (shift < 0) {
            message[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH -1 - (remaining_bits-1) / 32] += (data_label << (32 - Math.max(0, -shift)) >>> (32 - Math.max(0,-shift))) << (32 - Math.max(0, -shift));
            remaining_bits -= Math.max(0, -shift);
        }

        shift = ((remaining_bits - 1) % 32 + 1 - data_size);
        message[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH -1 - (remaining_bits-1) / 32] += (data >>> Math.max(0, -shift)) << Math.max(0, shift);

        remaining_bits -= data_size - Math.max(0, -shift);
        if (shift < 0){
            message[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH -1 - (remaining_bits-1) / 32] += (data << (32 - Math.max(0, -shift)) >>> (32 - Math.max(0,-shift))) << (32 - Math.max(0, -shift));
            remaining_bits -= Math.max(0, -shift);            
        }

        return remaining_bits;
    }

    static void addPasswordAndHashToMessage(int[] message){
        message[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH - 1] += blockchain_password << 22;
        String message_serialized = "";
        for (int i = 0; i != GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH; i++){
            message_serialized += Integer.toString(message[i]) + "_"; 
        }
        int hash = message_serialized.hashCode();
        message[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH - 1] += hash >>> 10;
    }

    static int getBitRange(int[] message, int start, int length){

        int value = (message[start / 32] << start % 32 >>> 32 - length);
        if (start % 32 + length > 32){
            value += (message[(start + length) / 32] >>> 64 - start - length);
        }

        return value;
    }

    static void updateMissionStatus(Report report, ArrayList<RobotStatus> robots){
        for (RobotStatus r : robots){
            if (r.robot_id == report.robot_id){
                for (int i = 0; i != r.missions.size(); i++){
                    Mission mission = r.missions.get(i);
                    if (mission.mission_type == report.mission_type){
                        r.missions.remove(i);
                        break;
                    }
                }
            }
        }
    }

    static void updateMissionStatus(Report report){
        switch(report.robot_type){
            case MINER:  
                updateMissionStatus(report, miners);
                break;
            case REFINERY: 
                updateMissionStatus(report, refineries);
                break;
            case VAPORATOR:
                updateMissionStatus(report, vaporators);
                break;
            case DESIGN_SCHOOL:
                updateMissionStatus(report, design_schools);
                break;
            case FULFILLMENT_CENTER:
                updateMissionStatus(report, fulfillment_centers);
                break;
            case LANDSCAPER:  
                updateMissionStatus(report, landscapers);
                break;
            case DELIVERY_DRONE: 
                updateMissionStatus(report, drones);
                break;
            case NET_GUN:
                updateMissionStatus(report, net_guns);
                break;     
            default:
                break;   
        }
    }

    static void updateRobot(Report report){
        if (report.robot_type == RobotType.HQ || report.report_type == ReportType.NO_ROBOT){
            if (possible_symmetries.size() > 1 && symmetric_HQ_locs[0] != null){
                for (int i = 0; i != symmetric_HQ_locs.length; i++){
                    if (possible_symmetries.contains(Symmetry.values()[i]) && report.location.equals(symmetric_HQ_locs[i])){
                        if (report.report_type == ReportType.ROBOT && report.robot_type == RobotType.HQ){
                            possible_symmetries.clear();
                            possible_symmetries.add(Symmetry.values()[i]);
                            if (possible_symmetries.size() == 1 && enemy_HQ_loc == null){
                                enemy_HQ_loc = symmetric_HQ_locs[possible_symmetries.get(0).ordinal()];
                            }
                            return;
                        }
                        else{
                            possible_symmetries.remove(Symmetry.values()[i]);
                            if (possible_symmetries.size() == 1 && enemy_HQ_loc == null){
                                enemy_HQ_loc = symmetric_HQ_locs[possible_symmetries.get(0).ordinal()];
                            }
                            return;
                        } 
                    }
                }
            }     
        }

        if (report.robot_team){
            switch(report.robot_type){
                case MINER:  
                    addRobotToList(miners, report.robot_id);
                    break;
                case REFINERY: 
                    addRobotToList(refineries, report.robot_id);
                    break;
                case VAPORATOR:
                    addRobotToList(vaporators, report.robot_id);
                    break;
                case DESIGN_SCHOOL:
                    addRobotToList(design_schools, report.robot_id);
                    break;
                case FULFILLMENT_CENTER:
                    addRobotToList(fulfillment_centers, report.robot_id);
                    break;
                case LANDSCAPER:  
                    addRobotToList(landscapers, report.robot_id);
                    break;
                case DELIVERY_DRONE: 
                    addRobotToList(drones, report.robot_id);
                    break;
                case NET_GUN:
                    addRobotToList(net_guns, report.robot_id);
                    break;   
            }
        }
      
    }

    static void updateFromReport (Report report) throws GameActionException{
        // Update mission status
        // Update enemy HQ loc

        switch(report.report_type){
            case SOUP:
                soup_deposits_public.add(report.location);
                break;
            case ROBOT:
                updateRobot(report);
                break;
            case NO_ROBOT:
                updateRobot(report);
                break;
            case MISSION_STATUS:
                if (rc.getType() == RobotType.HQ)
                    updateMissionStatus(report);
                break;
            default:
                break;
        }
    }

    static boolean checkPasswordAndHash(int[] message){
        if (blockchain_password_hashes.contains(message[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH - 1]))
            return false;
        int pass = message[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH - 1] >>> 22;
        if (pass != blockchain_password) 
            return false;
        int hash = message[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH - 1] << 10 >>> 10;
        message[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH - 1] = message[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH - 1] >>> 22 << 22;
        String message_serialized = "";
        for (int i = 0; i != GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH; i++){
            message_serialized += Integer.toString(message[i]) + "_"; 
        }
        if (hash != message_serialized.hashCode()>>>10)
            return false;
        message[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH - 1] += hash;
        return true; 
    }

    static void readMessage(int[] message) throws GameActionException {
        if (!checkPasswordAndHash(message)){
            return;
        }
        blockchain_password_hashes.add(message[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH - 1]);
        // Check if valid message from our team
        int start_bit = 0;
        int data_label_size = 5;

        Mission mission = null;
        boolean on_mission = false;
        boolean cleared_old_missions = false;

        Report report = null;

        while (true){
            Label data_label = Label.values()[getBitRange(message, start_bit, data_label_size)];
            start_bit += data_label_size;

            switch(data_label){
                case MISSION_TYPE:
                    if (mission != null && on_mission){
                        if (!cleared_old_missions){
                            active_missions.clear();
                            cleared_old_missions = true;
                        }
                        System.out.println("Added mission");
                        active_missions.add(mission);
                        mission = null;
                        on_mission = false;
                    }
                    if (report == null){
                        mission = new Mission();
                        mission.mission_type = MissionType.values()[getBitRange(message, start_bit, 5)];
                        start_bit += 5;
                    }
                    else{
                        report.mission_type = MissionType.values()[getBitRange(message, start_bit, 5)];
                        start_bit += 5;   
                    }
                    break;
                case ROBOT_ID:
                    int length = 15;
                    int robot_id = getBitRange(message, start_bit, length);
                    start_bit += 15;
                    if (mission != null){
                        mission.robot_ids.add(robot_id);

                        if (robot_id == rc.getID()){
                            on_mission = true;
                        }
                    }
                    if (report != null){
                        report.robot_id = robot_id;
                    }
                    break;                
                case LOCATION:
                    int x = getBitRange(message, start_bit, 6);
                    start_bit += 6;
                    int y = getBitRange(message, start_bit, 6);
                    start_bit += 6;
                    if (mission != null){
                        mission.location = new MapLocation(x,y);
                    }
                    if (report != null){
                        report.location = new MapLocation(x,y);
                    }
                    break;
                case ROBOT_TYPE:
                    if (mission != null){
                        mission.robot_type = RobotType.values()[getBitRange(message, start_bit, 4)];
                        start_bit += 4;
                    }
                    if (report != null){
                        report.robot_type = RobotType.values()[getBitRange(message, start_bit, 4)];
                        start_bit += 4;
                    }
                    break;
                case DISTANCE:
                   if (mission != null){
                        mission.distance = getBitRange(message, start_bit, 7);
                        start_bit += 7;
                    }
                    break;
                case REPORT_TYPE:
                    if (mission != null && on_mission){
                        if (!cleared_old_missions){
                            active_missions.clear();
                            cleared_old_missions = true;
                        }
                        System.out.println("Added mission");
                        active_missions.add(mission);
                        mission = null;
                        on_mission = false;
                    }    
                    if (report != null){
                        updateFromReport(report);
                        report = null;
                    }          
                    report = new Report(); 
                    report.report_type = ReportType.values()[getBitRange(message, start_bit, 5)];
                    start_bit += 5;                         
                    break;
                case ROBOT_TEAM:
                   if (report != null){
                        report.robot_team = (getBitRange(message, start_bit, 1) != 0);
                        start_bit += 1;
                    }
                    break;
                case MISSION_SUCCESSFUL:
                    if (report != null){
                        report.successful = (getBitRange(message, start_bit, 1) != 0);
                        start_bit += 1;
                    }
                    break;
                case SOUP_AMOUNT:
                    break;
                case END_MESSAGE:
                    if (mission != null && on_mission){
                        if (!cleared_old_missions){
                            active_missions.clear();
                            cleared_old_missions = true;
                        }
                        System.out.println("Added mission");
                        active_missions.add(mission);
                        mission = null;
                        on_mission = false;
                    }   
                    if (report != null){
                        updateFromReport(report);
                        report = null;
                    }   
                    return;             
                    // break;
            }   
        }
    }

    static void readBlockChain() throws GameActionException{
        int current_round = rc.getRoundNum();
        if (current_round <= 1) return;
        while (blockchain_read_index < current_round){
            System.out.println("reading blockchain: round " + blockchain_read_index);
            Transaction[] block = rc.getBlock(blockchain_read_index);
            for (Transaction msg : block){
                System.out.println("Active Missions");
                System.out.println(active_missions.size());
                readMessage(msg.getMessage());
                System.out.println(active_missions.size());
            }   
            blockchain_read_index++;
        }
    }

    static int getMissionBits(Mission mission){
        int mission_bits = 0; 
        mission_bits += 5 + 5; // Data Type Tag #bits Mission type #bits
        mission_bits += (5 + 15)*mission.robot_ids.size(); // Robot ID #bits
        mission_bits += 5 + 12; // Location # bits
        mission_bits += 5; // End message label
        mission_bits += 10; // Password 
        mission_bits += 22; // hash
        return mission_bits;
    }

    static int getReportBits(Report report){
        ReportType report_type;
        MapLocation location; // null
        RobotType robot_type;
        boolean robot_team;
        int robot_id;
        int soup_amount;
        MissionType mission_type;
        boolean successful;

        int report_bits = 0;
        report_bits += 5 + 5; // Report type

        switch(report.report_type){
            case SOUP:
                break;
            case ROBOT:
                report_bits += 5 + 15; // Robot ID
                report_bits += 5 + 12; // location
                report_bits += 5 + 4; // robot_type
                report_bits += 5 + 1; // team
                break;
            case NO_ROBOT:
                report_bits += 5 + 12; // location
                break;
            case MISSION_STATUS:
                report_bits += 5 + 15; // Robot ID
                report_bits += 5 + 4; // robot_type
                report_bits += 5 + 5; // Mission type
                report_bits += 5 + 1; // Mission success
                break;
        }
        report_bits += 10; // Password
        report_bits += 22; // hash
        return report_bits;
    }

    static void tryBlockchain() throws GameActionException {
        if (rc.getRoundNum() == 0)
            return;
        if (rc.getType() == RobotType.HQ){
            if (mission_queue.size() == 0){
                System.out.println("No Missions");
                return;
            }

            int[] message = new int[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH];
            int remaining_bits = 32*GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH;
            int i = 0;

            Mission mission = new Mission();

            while(i < mission_queue.size()){
                mission = mission_queue.get(i);

                int mission_bits = getMissionBits(mission);

                if (mission_bits > remaining_bits){
                    addToMessage(message, remaining_bits, 0, Label.END_MESSAGE.ordinal(), 0);
                    addPasswordAndHashToMessage(message);

                    if (rc.canSubmitTransaction(message, 1))
                        rc.submitTransaction(message, 1);
                        for (RobotStatus rs : miners){
                            for (int id : mission.robot_ids){
                                if (rs.robot_id == id){
                                    rs.missions.clear();
                                    rs.missions.add(mission);                            
                                }
                            }                        
                        }

                    remaining_bits = 32*GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH;
                    message = new int[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH];
                    continue;
                } 

                remaining_bits = addToMessage(message, remaining_bits, mission.mission_type.ordinal(), Label.MISSION_TYPE.ordinal(), 5);
                for (int id : mission.robot_ids){
                    remaining_bits = addToMessage(message, remaining_bits, id, Label.ROBOT_ID.ordinal(), 15);
                }
                remaining_bits = addToMessage(message, remaining_bits, (mission.location.x << 6) + mission.location.y, Label.LOCATION.ordinal(), 12);
                if (mission.mission_type == MissionType.BUILD){
                    remaining_bits = addToMessage(message, remaining_bits, mission.robot_type.ordinal(), Label.ROBOT_TYPE.ordinal(), 4);
                    remaining_bits = addToMessage(message, remaining_bits, mission.distance, Label.DISTANCE.ordinal(), 7);
                }
                i++;
            }

            addToMessage(message, remaining_bits, 0, Label.END_MESSAGE.ordinal(), 0);
            addPasswordAndHashToMessage(message);

            if (rc.canSubmitTransaction(message, 1)){
                rc.submitTransaction(message, 1);
                for (RobotStatus rs : miners){
                    for (int id : mission.robot_ids){
                        if (rs.robot_id == id){
                            rs.missions.clear();
                            rs.missions.add(mission);                            
                        }
                    }                        
                }
            }
        }
        else{
            if (report_queue.size() == 0){
                System.out.println("No Reports");
                return;
            }

            int[] message = new int[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH];
            int remaining_bits = 32*GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH;
            int i = 0;

            Report report = new Report();

            ArrayList<Integer> ind_to_remove = new ArrayList<Integer>();
            ArrayList<Integer> ind_to_remove_cumm = new ArrayList<Integer>();


            while(i < report_queue.size()){
                report = report_queue.get(i);
                int report_bits = 0; 

                // Check SIZE
                // report_bits += 5 + 5; // Data Type Tag #bits Mission type #bits
                // report_bits += (5 + 15)*mission.robot_ids.size(); // Robot ID #bits
                // report_bits += 5 + 12; // Location # bits
                // report_bits += 5; // End message label

                if (report_bits > remaining_bits){
                    addToMessage(message, remaining_bits, 0, Label.END_MESSAGE.ordinal(), 0);
                    addPasswordAndHashToMessage(message);

                    if (rc.canSubmitTransaction(message, 1))
                        rc.submitTransaction(message, 1);

                        ind_to_remove_cumm.addAll(ind_to_remove);
                        ind_to_remove.clear();

                    remaining_bits = 32*GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH;
                    message = new int[GameConstants.MAX_BLOCKCHAIN_TRANSACTION_LENGTH];
                    continue;
                } 

                remaining_bits = addToMessage(message, remaining_bits, report.report_type.ordinal(), Label.REPORT_TYPE.ordinal(), 5);
                if (report.report_type == ReportType.MISSION_STATUS){
                    remaining_bits = addToMessage(message, remaining_bits, report.robot_type.ordinal(), Label.ROBOT_TYPE.ordinal(), 4);
                    remaining_bits = addToMessage(message, remaining_bits, report.robot_id, Label.ROBOT_ID.ordinal(), 15);
                    remaining_bits = addToMessage(message, remaining_bits, report.mission_type.ordinal(), Label.MISSION_TYPE.ordinal(), 5); 
                    remaining_bits = addToMessage(message, remaining_bits, report.successful ? 1:0, Label.MISSION_SUCCESSFUL.ordinal(), 1); 
                }
                else if (report.report_type == ReportType.ROBOT){
                    remaining_bits = addToMessage(message, remaining_bits, report.robot_id, Label.ROBOT_ID.ordinal(), 15);
                    remaining_bits = addToMessage(message, remaining_bits, report.robot_type.ordinal(), Label.ROBOT_TYPE.ordinal(), 4);
                    remaining_bits = addToMessage(message, remaining_bits, report.robot_team ? 1:0, Label.ROBOT_TEAM.ordinal(), 1);
                    remaining_bits = addToMessage(message, remaining_bits, (report.location.x << 6) + report.location.y, Label.LOCATION.ordinal(), 12);
                }
                else if (report.report_type == ReportType.NO_ROBOT){
                    remaining_bits = addToMessage(message, remaining_bits, (report.location.x << 6) + report.location.y, Label.LOCATION.ordinal(), 12);
                }
                else if (report.report_type == ReportType.SOUP){
                    remaining_bits = addToMessage(message, remaining_bits, (report.location.x << 6) + report.location.y, Label.LOCATION.ordinal(), 12);
                }
                ind_to_remove.add(i);
                i++;
            }

            addToMessage(message, remaining_bits, 0, Label.END_MESSAGE.ordinal(), 0);
            addPasswordAndHashToMessage(message);

            if (rc.canSubmitTransaction(message, 1)){
                rc.submitTransaction(message, 1);
                ind_to_remove_cumm.addAll(ind_to_remove);
                ind_to_remove.clear();
            }

            for (int j = ind_to_remove_cumm.size() - 1; j >= 0; j--){
                report_queue.remove(j);
            }




        }
        // System.out.println(rc.getRoundMessages(turnCount-1));
    }
}
/*
 HQ:
    Does its own thing
    Limit amount of miners it creates
 
 MINERS:
    CONSTRUCTION
        Would need a location and a buildingType
    MINING
        Unsure what is needed (I would assume the location of soup and location of refinery/HQ)
    DEFAULT
        Move randomly looking for soup

 LANDSCAPERS:
    BUILD WALL
        Would need location to move to
    DEFEND
        Would need to know where location of HQ is
 
 DRONES:
    CARRY ROBOT
        Would need a location of Robot and a location to drop
    DEFEND
        Location of enemy unit to pick up and location to drop (Water)
 
 DESIGN_SCHOOL:
    PRODUCE
        just an indicator to produce a landscaper
 
 FULFILLMENT_CENTER:
    PRODUCE
        just an indicator to produce a drone
 
 
 */
