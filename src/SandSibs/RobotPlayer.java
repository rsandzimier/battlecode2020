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
        SCOUT, SCOUT_MINE, MINE, BUILD, DRONE_DEFAULT; 
    }
    enum ReportType
    {
        SOUP, ROBOT, NO_ROBOT, MISSION_STATUS, FLOOD; 
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

    static MapLocation closest_water_to_HQ = null;
    static MapLocation closest_water_to_enemy_HQ = null;

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

    static ArrayList<RobotStatus> enemy_drones = new ArrayList<RobotStatus>();

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

        System.out.println("Find design school location");
        MapLocation design_school_location = chooseDesignSchoolLocation();
        System.out.println("Design school location: "+ design_school_location.x + ", "+ design_school_location.y);

        System.out.println("Find FC location");
        MapLocation fc_location = chooseFulfillmentCenterLocation(design_school_location);
        System.out.println("FC location: "+ fc_location.x + ", "+ fc_location.y);


        for (RobotStatus ed :enemy_drones){
            if (rc.canShootUnit(ed.robot_id)){
                rc.shootUnit(ed.robot_id);
            }
        }

        if (phase == Phase.EARLY){
            if (miners.size() < 6){
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

            for (RobotStatus rs : drones){
                if (rs.missions.size() > 0 && rs.missions.get(0).mission_type == MissionType.SCOUT){
                    MapLocation loc = rs.missions.get(0).location;
                    for (int i = 0; i != 3; i ++){
                        if (needsScouting[i] && loc.equals(symmetric_HQ_locs[i])){
                            needsScouting[i] = false;
                        }
                    }
                }
            }
            boolean assigned_scouting_mission = false;
            for(int i = 0; i !=  drones.size() && !assigned_scouting_mission; i++){
                for (int j = 0; j != 3 && !assigned_scouting_mission; j++){
                    if (needsScouting[j] && ((drones.get(i).missions.size() == 0  || drones.get(i).missions.get(0).mission_type == MissionType.DRONE_DEFAULT))){
                        // Assign this drone the mission
                        Mission new_mission = new Mission();
                        new_mission.mission_type = MissionType.SCOUT;
                        new_mission.location = symmetric_HQ_locs[j];
                        new_mission.robot_ids.add(drones.get(i).robot_id);
                        mission_queue.add(new_mission);
                        assigned_scouting_mission = true;
                    }
                }
            } 
            if (fulfillment_centers.size() == 0 && miners.size() > 3){
                Mission new_mission = null;
                for (RobotStatus rs : miners){
                    if (rs.missions.size() != 0 && rs.missions.get(0).mission_type == MissionType.BUILD &&
                         rs.missions.get(0).robot_type == RobotType.FULFILLMENT_CENTER){
                        new_mission = null;
                        break;
                    }

                    System.out.println("START");
                    if (new_mission == null && (rs.missions.size() == 0 || rs.missions.get(0).mission_type == MissionType.MINE)){
                        System.out.println("TEST 1");

                        MapLocation loc = chooseFulfillmentCenterLocation(design_schools.get(0).location);
                        System.out.println("TEST 2");

                        if (loc == null) {
                            System.out.println("TEST 3a");

                            continue;
                        }
                        System.out.println("TEST 3b");

                        new_mission = new Mission();
                        new_mission.mission_type = MissionType.BUILD;
                        System.out.println(design_schools.get(0).location);
                        System.out.println(design_schools.get(0).robot_id);

                        new_mission.location = loc;
                        new_mission.robot_ids.add(rs.robot_id);
                        new_mission.robot_type = RobotType.FULFILLMENT_CENTER;
                        new_mission.distance = 0;
                    }
                    System.out.println("END");

                }
                if (new_mission != null){
                    mission_queue.add(new_mission);
                }
            }

            if (design_schools.size() == 0 && miners.size() > 3){
                Mission new_mission = null;
                for (RobotStatus rs : miners){
                    if (rs.missions.size() != 0 && rs.missions.get(0).mission_type == MissionType.BUILD &&
                         rs.missions.get(0).robot_type == RobotType.DESIGN_SCHOOL){
                        new_mission = null;
                        break;
                    }
                    if (new_mission == null && (rs.missions.size() == 0 || rs.missions.get(0).mission_type == MissionType.MINE)){
                        boolean miner_already_queued = false;
                        for (Mission m : mission_queue){
                            if (m.robot_ids.size() != 0 &&  m.robot_ids.get(0) == rs.robot_id){
                                miner_already_queued = true;
                                break;
                            }
                        }
                        if (miner_already_queued){
                            continue;
                        }
                        new_mission = new Mission();
                        new_mission.mission_type = MissionType.BUILD;
                        new_mission.location = chooseDesignSchoolLocation();
                        new_mission.robot_ids.add(rs.robot_id);
                        new_mission.robot_type = RobotType.DESIGN_SCHOOL;
                        new_mission.distance = 0;
                    }
                }
                if (new_mission != null){
                    mission_queue.add(new_mission);
                }

            }
        }
        tryBlockchain();
        mission_queue.clear();
    }

    static void tryBuildMission(Mission mission) throws GameActionException {
        // If can build, build
        // Otherwise, path toward
        MapLocation current_location = rc.getLocation();
        // if (current_location.equals(mission.location) && mission.distance == 0){
        //     tryMove();
        //     return;
        // }
        for (Direction dir : directions){
            if (current_location.add(dir).isWithinDistanceSquared(mission.location, mission.distance*mission.distance)){
                int build_cost = 0;
                switch(mission.robot_type){
                    case MINER:
                        build_cost = 70;
                        break;
                    case LANDSCAPER:
                        build_cost = 150;
                        break;
                    case DELIVERY_DRONE:
                        build_cost = 150;
                        break;
                    case REFINERY:
                        build_cost = 200;
                        break;
                    case VAPORATOR:
                        build_cost = 1000;
                        break;
                    case DESIGN_SCHOOL:
                        build_cost = 150;
                        break;
                    case FULFILLMENT_CENTER:
                        build_cost = 150;
                        break;
                    case NET_GUN:
                        build_cost = 250;
                        break;
                    default:
                        break;                    
                }
                if (mission.robot_type == RobotType.FULFILLMENT_CENTER && design_schools.size()==0){
                    continue;
                }
                if (tryBuild(mission.robot_type, dir)){
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
                else if (rc.canMove(dir) && rc.getTeamSoup() < build_cost){
                    visited.clear();
                    return;
                }
                else if (rc.canMove(dir) && rc.getLocation().add(dir).equals(mission.location)){
                    visited.clear();
                    return;
                }
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
        int dist = 10000;
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
            if (HQ_loc != null && !loc.isWithinDistanceSquared(HQ_loc, 8) && tryBuild(RobotType.REFINERY, dir)){
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
    static MapLocation nextSoupLocation() throws GameActionException {
        Iterator<MapLocation> it = soup_deposits.iterator();
        MapLocation location = null;
        while(it.hasNext()){
            location = it.next();
            if (HQ_loc != null && (!location.isWithinDistanceSquared(HQ_loc, 8)) || design_schools.size() == 0){
                return location;
            }
        }
        location = null;
        return location;
    }

    static boolean tryMineMission() throws GameActionException {
        MapLocation soup_location = nextSoupLocation();
        if (tryRefine()){

            ;
        }
        else if (distanceSquaredToNearestRefinery() > 50 && adjacentToSoup() && tryBuildRefinery()){
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
        else if ((HQ_loc != null && (!rc.getLocation().isWithinDistanceSquared(HQ_loc, 8) || (design_schools.size() == 0 && !rc.getLocation().isWithinDistanceSquared(HQ_loc, 2))) && tryMine())){
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
        else if (soup_location != null){
            moveToLocationUsingBugPathing(soup_location);
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

    static MapLocation[] getBaseBounds() throws GameActionException{
        int min_x = HQ_loc.x - 1;
        int max_x = HQ_loc.x + 1;
        int min_y = HQ_loc.y - 1;
        int max_y = HQ_loc.y + 1;

        int map_width = rc.getMapWidth();
        int map_height = rc.getMapHeight();

        if (min_x < 0){
            max_x -= min_x;
            min_x = 0;
        }
        else if (max_x >= map_width){
            min_x -= max_x - map_width + 1;
            max_x = map_width - 1;
        }
        if (min_y < 0){
            max_y -= min_y;
            min_y = 0;
        }
        else if (max_y >= map_height){
            min_y -= max_y - map_height + 1;
            max_y = map_height - 1;
        }
        MapLocation[] bounds = {new MapLocation(min_x,min_y), new MapLocation(max_x, max_y)};      
        return bounds;  
    }

    static ArrayList<Direction> getExitPriority() throws GameActionException{
        MapLocation[] base_bounds = getBaseBounds();

        ArrayList<Direction> exit_directions = new ArrayList<Direction>();
        exit_directions.add(Direction.NORTH);
        exit_directions.add(Direction.WEST);
        exit_directions.add(Direction.SOUTH);
        exit_directions.add(Direction.EAST);

        ArrayList<Direction> exit_priority = new ArrayList<Direction>();

        while (exit_directions.size() > 0){
            int longest_dist = -100;
            int longest_index = -1;
            for (int i = 0; i != exit_directions.size(); i++){
                Direction dir = exit_directions.get(i);
                int dist = 0;
                switch(dir){
                    case NORTH:
                        dist = rc.getMapHeight() - 1 - base_bounds[1].y;   
                        break;
                    case WEST:
                        dist = base_bounds[0].x;   
                        break;
                    case SOUTH:
                        dist = base_bounds[0].y;   
                        break;
                    case EAST:
                        dist = rc.getMapWidth() - 1 - base_bounds[1].x;   
                        break;
                }
                if (dist > longest_dist){
                    longest_dist = dist;
                    longest_index = i;
                }
            }
            if (longest_index < 0)
                break;
            if (longest_dist > 5){
                exit_priority.add(exit_directions.get(longest_index));
            }
            exit_directions.remove(longest_index);
        }

        return exit_priority;
    }

    static boolean canBuildDesignSchoolAtLocation(MapLocation location) throws GameActionException{
        int elevation_HQ = 0;
        if (rc.canSenseLocation(HQ_loc)){
            elevation_HQ = rc.senseElevation(HQ_loc);
        }
        // Need to add condition for enemy at location
        return rc.canSenseLocation(location) && Math.abs(rc.senseElevation(location) - elevation_HQ) <= 3;
    }

    static MapLocation chooseDesignSchoolLocation() throws GameActionException{
        MapLocation[] base_bounds = getBaseBounds();
        ArrayList<Direction> exit_priority = getExitPriority();

        MapLocation center = base_bounds[0].add(Direction.NORTHEAST);

        if (HQ_loc.equals(center)){
            Direction exit1 = exit_priority.get(0);
            Direction exit2 = exit_priority.get(1);
            if (exit1 == exit2.opposite()){
                exit2 = exit_priority.get(2);
            }

            MapLocation candidate_location = center.add(exit1).add(exit2);
            if (canBuildDesignSchoolAtLocation(candidate_location)) return candidate_location;
            candidate_location = center.add(exit1).add(exit2.opposite());
            if (canBuildDesignSchoolAtLocation(candidate_location)) return candidate_location;
            candidate_location = center.add(exit1.opposite()).add(exit2);
            if (canBuildDesignSchoolAtLocation(candidate_location)) return candidate_location;
            candidate_location = center.add(exit1.opposite()).add(exit2.opposite());
            if (canBuildDesignSchoolAtLocation(candidate_location)) return candidate_location;
            candidate_location = center.add(exit2);
            if (canBuildDesignSchoolAtLocation(candidate_location)) return candidate_location;
            candidate_location = center.add(exit2.opposite());
            if (canBuildDesignSchoolAtLocation(candidate_location)) return candidate_location;            
            candidate_location = center.add(exit1);
            if (canBuildDesignSchoolAtLocation(candidate_location)) return candidate_location;
            candidate_location = center.add(exit1.opposite());
            if (canBuildDesignSchoolAtLocation(candidate_location)) return candidate_location;   
        }

        // if centered
            // Try diagonal near best 2 exits (1)
            // Try diagonal near best exit (1)
            // Try diagonal near other exit (1)
            // Try last diagonal (2)
            // Try edge 90 degrees from best exit (2)
            // Try edge 90 degrees from other exit (2)



        // MapLocation[] base_bounds = getBaseBounds();
        // double best_score = 0;
        // MapLocation best_location = null;
        // // System.out.println("Start choose design school");
        // for (int x = base_bounds[0].x; x <= base_bounds[1].x; x++){
        //     for (int y = base_bounds[0].y; y <= base_bounds[1].y; y++){
        //         MapLocation loc = new MapLocation(x,y);
        //         if (!loc.equals(HQ_loc) && rc.canSenseLocation(loc) && rc.canSenseLocation(HQ_loc) && 
        //             Math.abs(rc.senseElevation(loc)-rc.senseElevation(HQ_loc))<= 3 &&
        //             (rc.senseRobotAtLocation(loc) == null || rc.senseRobotAtLocation(loc).getTeam() == rc.getTeam().opponent())){
        //             boolean can_build = false;
        //             for (Direction dir : directions){
        //                 MapLocation loc2 = loc.add(dir);
        //                 if (!loc2.equals(HQ_loc) && rc.canSenseLocation(loc2) &&
        //                     Math.abs(rc.senseElevation(loc2)-rc.senseElevation(loc))<= 3 && 
        //                     (rc.senseRobotAtLocation(loc2) == null || rc.senseRobotAtLocation(loc2).getTeam() == rc.getTeam().opponent())){
        //                     can_build = true;
        //                     break;
        //                 }
        //             }
        //             if (can_build){
        //                 MapLocation fulfillment_center_location = chooseFulfillmentCenterLocation(loc);
        //                 double score = baseLayoutScore(loc, fulfillment_center_location);
        //                 if (score > best_score){
        //                     best_score = score;
        //                     best_location = loc;
        //                 }
        //             }
        //         } 
        //     }
        // }
        // // System.out.println("End choose design school");

        return null;
    }

    static int getBaseTileIndex(MapLocation location) throws GameActionException{
        //       NORTH
        //       0 1 2
        // WEST  3 4 5  EAST
        //       6 7 8
        //       SOUTH
        // -1 if outside base

        MapLocation[] base_bounds = getBaseBounds();
        MapLocation center = base_bounds[0].add(Direction.NORTHEAST);
        if (!location.isWithinDistanceSquared(center, 2)) return -1;
        switch(center.directionTo(location)){
            case NORTHWEST: return 0;
            case NORTH: return 1;
            case NORTHEAST: return 2;
            case WEST: return 3;
            case CENTER: return 4;
            case EAST: return 5;
            case SOUTHWEST: return 6;
            case SOUTH: return 7;
            case SOUTHEAST: return 8;
            default: return -1;
        }
    }

    static MapLocation getLocationFromBaseTileIndex(int index) throws GameActionException{
        if (index < 0 || index > 8) return null;
        MapLocation[] base_bounds = getBaseBounds();
        MapLocation center = base_bounds[0].add(Direction.NORTHEAST);
        Direction direction = Direction.CENTER;
        switch(index){
            case 0: direction = Direction.NORTHWEST; break;
            case 1: direction = Direction.NORTH; break;
            case 2: direction = Direction.NORTHEAST; break;
            case 3: direction = Direction.WEST; break;
            case 4: direction = Direction.CENTER; break;
            case 5: direction = Direction.EAST; break;
            case 6: direction = Direction.SOUTHWEST; break;
            case 7: direction = Direction.SOUTH; break;
            case 8: direction = Direction.SOUTHEAST; break;
            default: break;
        }
        return center.add(direction);
    }

    static int rotateBaseTileIndex(int index, Direction direction){
        // Rotate base so that "direction" is the new North and output new index
        if (index < 0 || index > 8) return -1;
        int num_rotations = 0;
        switch(direction){
            case NORTH: num_rotations = 0; break;
            case NORTHEAST: num_rotations = 1; break;
            case EAST: num_rotations = 2; break;
            case SOUTHEAST: num_rotations = 3; break;
            case SOUTH: num_rotations = 4; break;
            case SOUTHWEST: num_rotations = 5; break;
            case WEST: num_rotations = 6; break;
            case NORTHWEST: num_rotations = 7; break;
            default: return -1;
        }
        for (int i = 0; i != num_rotations; i++){
            index = rotateBaseTileIndexCounterClockwiseOnce(index);
        }
        return index;
    }

    static int rotateBaseTileIndexCounterClockwiseOnce(int index){
        // Rotate base clockwise by one step (45 degrees) and return new index
        if (index < 0 || index > 8) return -1;
        switch(index){
            case 0: return 3;
            case 1: return 0;
            case 2: return 1;
            case 3: return 6;
            case 4: return 4;
            case 5: return 2;
            case 6: return 7;
            case 7: return 8;
            case 8: return 5;
        }
        return -1;
    }

    static boolean canBuildFulfillmentCenterAtLocation(MapLocation location) throws GameActionException{
        // Need to add condition
        return true;
    }

    static Direction mirrorDirectionAboutNorth(Direction direction){
        switch(direction){
            case NORTH: return Direction.NORTH;
            case NORTHEAST: return Direction.NORTHWEST;
            case EAST: return Direction.WEST;
            case SOUTHEAST: return Direction.SOUTHWEST;
            case SOUTH: return Direction.SOUTH;
            case SOUTHWEST: return Direction.SOUTHEAST;
            case WEST: return Direction.EAST;
            case NORTHWEST: return Direction.NORTHEAST;
            case CENTER: return Direction.CENTER;
        }
        return null;
    }

    static MapLocation chooseFulfillmentCenterLocation(MapLocation design_school_location) throws GameActionException{
        if (design_school_location == null) return null;
        MapLocation[] base_bounds = getBaseBounds();
        ArrayList<Direction> exit_priority = getExitPriority();

        MapLocation center = base_bounds[0].add(Direction.NORTHEAST);

        int design_school_base_index = getBaseTileIndex(design_school_location);

        MapLocation candidate_location = center;

        System.out.println("Exit Priority");
        for (Direction ep: exit_priority)
            System.out.println(ep);

        if (HQ_loc.equals(center)){
            for (Direction dir : exit_priority){
                System.out.println("Direction " + dir);
                int design_school_rotated_base_index = rotateBaseTileIndex(design_school_base_index, dir);
                System.out.println("Base index " + design_school_base_index + " rotated " + design_school_rotated_base_index);
                switch(design_school_rotated_base_index){
                    case 0:
                    case 3:
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(2, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        break;
                    case 2:
                    case 5:
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(0, mirrorDirectionAboutNorth(dir)));
                        System.out.println("index " + rotateBaseTileIndex(0, dir.opposite()) + " location " + candidate_location);
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        break;
                    case 6:
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(1, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(0, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        break;
                    case 8:
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(1, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(2, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        break;
                    default: 
                        break;
                }

            }
            for (Direction dir : exit_priority){
                System.out.println("Direction " + dir);
                int design_school_rotated_base_index = rotateBaseTileIndex(design_school_base_index, dir);
                System.out.println("Base index " + design_school_base_index + " rotated " + design_school_rotated_base_index);
                switch(design_school_rotated_base_index){
                    case 0:
                    case 3:
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(5, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        break;
                    case 2:
                    case 5:
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(3, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        break;
                    case 6:
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(2, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(5, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        break;
                    case 7:
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(1, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(0, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(2, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(3, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(5, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        break;                    
                    case 8:
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(0, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        candidate_location = getLocationFromBaseTileIndex(rotateBaseTileIndex(3, mirrorDirectionAboutNorth(dir)));
                        if (canBuildFulfillmentCenterAtLocation(candidate_location)) return candidate_location;
                        break;
                    default: 
                        break;
                }
            }    
        }

        // 0 1 2
        // 3 4 5
        // 6 7 8

        // If centered
            // in order of preferred direction 
                // re-orient
                // if DS at 0
                    // try 5, 2
                // if DS at 1
                    // try 8
                // if DS at 2
                    // try 8
                // if DS at 6
                    // try 5, 8
                // if DS at 7
                    // try 2
                // if DS at 8
                    // try 2
            // in order of preferred direction
                // re-orient
                // if DS at 0
                    // try 8, 7
                // if DS at 1
                    // try 7
                // if DS at 2
                    // try 7
                // if DS at 6
                    // try 2, 1
                // if DS at 7
                    // try 1
                // if DS at 8
                    // try 1



        // MapLocation[] base_bounds = getBaseBounds();
        // double best_score = 0;
        // MapLocation best_location = null;
        // // System.out.println("Start choose fulfillment center");

        // for (int x = base_bounds[0].x; x <= base_bounds[1].x; x++){
        //     for (int y = base_bounds[0].y; y <= base_bounds[1].y; y++){
        //         MapLocation loc = new MapLocation(x,y);
        //         if (!loc.equals(HQ_loc) && !loc.equals(design_school_location) && rc.canSenseLocation(loc) &&
        //             (rc.senseRobotAtLocation(loc) == null || rc.senseRobotAtLocation(loc).getTeam() == rc.getTeam().opponent())){
        //             double score = baseLayoutScore(design_school_location, loc);
        //             if (score > best_score){
        //                 best_score = score;
        //                 best_location = loc;
        //             }                
        //         } 
        //     }
        // }

        // // System.out.println("End choose fulfillment center");

        return null;
    }

    static boolean isInsideBase(MapLocation[] base_bounds, MapLocation loc){
        return (loc.x >= base_bounds[0].x && loc.x <= base_bounds[1].x && loc.y >= base_bounds[0].y && loc.y <= base_bounds[1].y);
    }

    static double baseLayoutScore(MapLocation design_school_location, MapLocation fulfillment_center_location) throws GameActionException{
        MapLocation[] base_bounds = getBaseBounds();
        double best_score = 0;
        // System.out.println("Start base layout score "+ Clock.getBytecodeNum());
        for (Direction dir1 : directions){
            MapLocation landscaper_dropoff = design_school_location.add(dir1);
            if (landscaper_dropoff.equals(HQ_loc) || landscaper_dropoff.equals(fulfillment_center_location) || !isInsideBase(base_bounds, landscaper_dropoff)){
                continue;
            }
            for (Direction dir2 : directions){
                MapLocation drone_dropoff = fulfillment_center_location.add(dir2);
                if (drone_dropoff.equals(HQ_loc) || drone_dropoff.equals(fulfillment_center_location) ||
                    drone_dropoff.equals(landscaper_dropoff) || !isInsideBase(base_bounds, drone_dropoff)){
                    continue;
                }
                double score = baseLayoutScoreFullInput(design_school_location, fulfillment_center_location, landscaper_dropoff, drone_dropoff);
                if (score > best_score){
                    best_score = score;
                }
            }
        }
        // System.out.println("End base layout score " + Clock.getBytecodeNum());

        return best_score;

    }

    static double baseLayoutScoreFullInput(MapLocation design_school_location, MapLocation fulfillment_center_location, MapLocation landscaper_dropoff, MapLocation drone_dropoff) throws GameActionException{
        MapLocation[] base_bounds = getBaseBounds();
        int largest_distance_from_HQ = 0;
        // System.out.println("Start base layout score full"+ Clock.getBytecodeNum());

        for (int i = 0; i != 4; i++){
            int dist = 0;
            switch(i){
                case 0: // Right
                    dist = rc.getMapWidth() - 1 - base_bounds[1].x;   
                    break;
                case 1: // Up
                    dist = rc.getMapHeight() - 1 - base_bounds[1].y;   
                    break;
                case 2: // Left
                    dist = base_bounds[0].x;   
                    break;
                case 3: // Down
                    dist = base_bounds[0].y;   
                    break;
            }
            if (dist > largest_distance_from_HQ){
                largest_distance_from_HQ = dist;
            }
        }
        double score = 0;
        if (landscaper_dropoff.isAdjacentTo(HQ_loc) || drone_dropoff.isAdjacentTo(HQ_loc)){
            score += 5000;
        }
        if (landscaper_dropoff.isAdjacentTo(drone_dropoff)){
            score += 1000;
        }
        double exit_score = 0;
        for (int i = 0; i != 4; i++){
            // Check if drone dropoff near exit. Calculate exit score
            // Bonus if landscaper dropoff is near same exit and FC
            double exit_score_i = 0;
            switch(i){
                case 0: // Right
                    if (drone_dropoff.x == base_bounds[1].x){
                        exit_score_i = (rc.getMapWidth() - 1 - base_bounds[1].x)*500.0/largest_distance_from_HQ;
                        if (fulfillment_center_location.isAdjacentTo(landscaper_dropoff) && landscaper_dropoff.x == base_bounds[1].x){
                            exit_score_i += 100;
                        }
                    }
                    break;
                case 1: // Up
                    if (drone_dropoff.y == base_bounds[1].y){
                        exit_score_i = (rc.getMapHeight() - 1 - base_bounds[1].y)*500.0/largest_distance_from_HQ;
                        if (fulfillment_center_location.isAdjacentTo(landscaper_dropoff) && landscaper_dropoff.y == base_bounds[1].y){
                            exit_score_i += 100;
                        }
                    }
                    break;
                case 2: // Left
                    if (drone_dropoff.x == base_bounds[0].x){
                        exit_score_i = base_bounds[0].x*500.0/largest_distance_from_HQ;
                        if (fulfillment_center_location.isAdjacentTo(landscaper_dropoff) && landscaper_dropoff.x == base_bounds[0].x){
                            exit_score_i += 100;
                        }
                    }
                    break;
                case 3: // Down
                    if (drone_dropoff.y == base_bounds[0].y){
                        exit_score_i = base_bounds[0].y*500.0/largest_distance_from_HQ;
                        if (fulfillment_center_location.isAdjacentTo(landscaper_dropoff) && landscaper_dropoff.y == base_bounds[0].y){
                            exit_score_i += 100;
                        }
                    }
                    break;
            }
            if (exit_score_i > exit_score){
                exit_score = exit_score_i;
            }
        }

        // Could add score for vaporator continuity

        score += exit_score;
        // System.out.println("End base layout score full " + Clock.getBytecodeNum());

        return score;
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
        updateMapRobots();
        if ((rc.getTeamSoup() >= 350 || rc.getRoundNum() > 800) && HQ_loc != null){
            RobotInfo[] nearby_robots = rc.senseNearbyRobots();

            int count = 0;
            for (RobotInfo nr : nearby_robots){
                if (nr.getTeam() == rc.getTeam() && nr.getType() == RobotType.LANDSCAPER && nr.getLocation().isWithinDistanceSquared(HQ_loc, 2)){
                    count++;
                }
            }
            if (count >= 2){
                return;
            }
            for(Direction dir : directions)
                if(tryBuild(RobotType.LANDSCAPER, dir));
        }
    }

    static void runFulfillmentCenter() throws GameActionException {
        updateMapRobots();

        if (rc.getTeamSoup() >= 350  || rc.getRoundNum() > 800){
            RobotInfo[] nearby_robots = rc.senseNearbyRobots();

            int count = 0;
            int count_LS = 0;
            for (RobotInfo nr : nearby_robots){
                if (nr.getTeam() == rc.getTeam() && nr.getType() == RobotType.DELIVERY_DRONE && nr.getLocation().isWithinDistanceSquared(HQ_loc, 2)){
                    count++;
                }
                if (nr.getTeam() == rc.getTeam() && nr.getType() == RobotType.LANDSCAPER && nr.getLocation().isWithinDistanceSquared(HQ_loc, 8)){
                    count_LS++;
                }
            }
            if (count >= 2){
                return;
            }
            if (count >= 1 && count_LS < 12){
                return;
            }
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
            if(rc.getLocation().equals(buildLocation) || rc.getLocation().isAdjacentTo(buildLocation)){
                if(rc.canDepositDirt(rc.getLocation().directionTo(buildLocation)) && rc.isReady()){
                    rc.depositDirt(rc.getLocation().directionTo(buildLocation));
                } 
                else {
                    int dx = HQ_loc.x - rc.getLocation().x;
                    int dy = HQ_loc.y - rc.getLocation().y;
                    int min_x = 100;
                    int max_x = 0;
                    int min_y = 100;
                    int max_y = 0;
                    for (MapLocation wl : wallLocation){
                        if (wl.x < min_x)
                            min_x = wl.x;
                        if (wl.x > max_x)
                            max_x = wl.x;                        
                        if (wl.y < min_y)
                            min_y = wl.y;                        
                        if (wl.y > max_y)
                            max_y = wl.y;
                    }
                    
                    for (Direction dir : directions){
                        MapLocation dig_location = rc.getLocation().add(dir);
                        if (dig_location.x >= min_x && dig_location.x <= max_x && dig_location.y >= min_y && dig_location.y <= max_y){
                            continue;
                        }
                        if(rc.canDigDirt(dir) && rc.isReady()) {
                            rc.digDirt(dir);
                            break;
                        }
                    }
                }
            }
            else {
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
        MapLocation current_location = rc.getLocation();
        int value = 10000;
        for(int i=0; i<16; i++){
            if(rc.canSenseLocation(wallLocation[i])){
                // if(rc.senseElevation(returnLoc) > rc.senseElevation(wallLocation[i]) + 3*(int)Math.sqrt(rc.getLocation().distanceSquaredTo(wallLocation[i]))){
                int num_steps = Math.max(0, Math.abs(current_location.x - wallLocation[i].x)-1)+Math.max(0, Math.abs(current_location.y - wallLocation[i].y)-1)+1;
                int value_i = rc.senseElevation(wallLocation[i]) + 3*num_steps;
                if(value_i < value){
                    returnLoc = wallLocation[i];
                    value = value_i;
                }
            }
        }
        
        return returnLoc;
    }

    static void tryDefaultDroneMission() throws GameActionException{
        if(HQ_loc != null && wallLocSet) {
            setWallLocations();
            wallLocSet = false;
        }
        if(droneSpawnLocation == null) droneSpawnLocation = rc.getLocation();
        Team ourTeam = rc.getTeam();
        
        if(rc.isCurrentlyHoldingUnit() && enemyUnitInDrone){
            for(Direction dir : directions){
                if(rc.canSenseLocation(rc.getLocation().add(dir)) && rc.senseFlooding(rc.getLocation().add(dir)) && rc.canDropUnit(dir)) {
                    rc.dropUnit(dir);
                    enemyUnitInDrone = false;
                }
                else;
            }
            MapLocation drop_location = closest_water_to_HQ;
            if (HQ_loc != null && enemy_HQ_loc != null && 
                rc.getLocation().distanceSquaredTo(closest_water_to_enemy_HQ) < rc.getLocation().distanceSquaredTo(closest_water_to_HQ)){
                drop_location = closest_water_to_enemy_HQ;
            }
            if (drop_location != null)
                moveToLocationUsingBugPathing(drop_location);
            else
                tryMove(randomDirection());
            return;
        }
        
        if(rc.isCurrentlyHoldingUnit() && allyLandscaperUnitInDrone){
            MapLocation closest_empty_wall = null;
            int dist_to_empty = 10000;
            for(int i = 0; i < 16; i++){
                if(rc.getLocation().isAdjacentTo(wallLocation[i]) && rc.isReady()  && rc.canDropUnit(rc.getLocation().directionTo(wallLocation[i]))){
                    rc.dropUnit(rc.getLocation().directionTo(wallLocation[i]));
                    allyLandscaperUnitInDrone = false;
                    helpUnit = false;
                    helpUnitID = 0;
                    break;
                }
                int dist_i = rc.getLocation().distanceSquaredTo(wallLocation[i]);
                if (dist_i < dist_to_empty){
                    closest_empty_wall = wallLocation[i];
                    dist_to_empty = dist_i;
                }
            }


            // Head more intelligently to empty spot
            if (closest_empty_wall != null){
                moveToLocationUsingBugPathing(closest_empty_wall);
            }
        }
        
        RobotInfo[] nearby_robots = rc.senseNearbyRobots();
        for(int i=0; i < nearby_robots.length; i++){
            if(nearby_robots[i].getTeam() != ourTeam && nearby_robots[i].getType().canBePickedUp()){
                if(rc.getLocation().isAdjacentTo(nearby_robots[i].getLocation()) && rc.isReady() && rc.canPickUpUnit(nearby_robots[i].getID())){
                    rc.pickUpUnit(nearby_robots[i].getID());
                    enemyUnitInDrone = true;
                }
                else moveToLocationUsingBugPathing(nearby_robots[i].getLocation());
            }
        }
        boolean wall_full = true;
        for(int j = 0; j < 16; j++){
            if (rc.canSenseLocation(wallLocation[j]) && rc.senseRobotAtLocation(wallLocation[j]) == null){
                wall_full = false;
                break;
            }
        }
        RobotInfo landscaper_to_help = null;
        if (!wall_full){
            int dist_to_landscaper = 10000;
            for(int i=0; i < nearby_robots.length; i++){
                if(nearby_robots[i].getTeam() == ourTeam && nearby_robots[i].getType() == RobotType.LANDSCAPER){
                    boolean on_wall = false;
                    for(int j = 0; j < 16; j++){
                        if(nearby_robots[i].getLocation().equals(wallLocation[j])){
                            on_wall = true;
                            break;
                        }

                    }
                    if (!on_wall){
                        int dist_i = rc.getLocation().distanceSquaredTo(nearby_robots[i].getLocation());
                        if (dist_i < dist_to_landscaper){
                            landscaper_to_help = nearby_robots[i];
                            dist_to_landscaper = dist_i;
                        }                    
                    }
                }
            }    
        }

        if (landscaper_to_help != null){
            if(rc.isReady() && rc.canPickUpUnit(landscaper_to_help.getID())){
                rc.pickUpUnit(landscaper_to_help.getID());
                allyLandscaperUnitInDrone = true;
            }
            else moveToLocationUsingBugPathing(landscaper_to_help.getLocation());
        }
        
        if(rc.canSenseLocation(HQ_loc)) tryMove(randomDirection());
        else moveToLocationUsingBugPathing(HQ_loc);        
    }

    static void runDeliveryDrone() throws GameActionException {
        readBlockChain();
        Mission current_mission = new Mission();
        if (active_missions.size() > 0){
            current_mission = active_missions.get(0);
        }
        else{
            current_mission.mission_type = MissionType.DRONE_DEFAULT;
        }

        updateMapDiscovered();
        updateMapGoalLocation();
        // updateMapSoupDeposits();
        updateMapRobots();

        switch(current_mission.mission_type){
            case SCOUT:
                tryScoutMission(current_mission);
                break;
            case DRONE_DEFAULT:
            default:
                tryDefaultDroneMission();
                break;
        }
        tryBlockchain();
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
            if (map[x][y].flooded){
                if (HQ_loc != null && (closest_water_to_HQ == null || HQ_loc.distanceSquaredTo(ml) < HQ_loc.distanceSquaredTo(closest_water_to_HQ))){
                    closest_water_to_HQ = ml;
                    Report report = new Report();
                    report.report_type = ReportType.FLOOD;
                    report.location = ml;
                    report_queue.add(report);
                }
                if (enemy_HQ_loc != null && (closest_water_to_enemy_HQ == null || HQ_loc.distanceSquaredTo(ml) < HQ_loc.distanceSquaredTo(closest_water_to_enemy_HQ))){
                    closest_water_to_enemy_HQ = ml;
                }            
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

    static void addRobotToList(ArrayList<RobotStatus> robots, int id, MapLocation location){
        if (location == null){
            addRobotToList(robots, id);
            return;
        }
        if (!robotListContainsID(robots, id)){
            RobotStatus rs = new RobotStatus(id);
            rs.location = location;
            System.out.println("Adding robot with ID: " + id + " at location: " + location);
            robots.add(rs);
        }
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
        enemy_drones.clear();
        for (RobotInfo nr : nearby_robots){
            if (robot_type == RobotType.HQ && rc.getTeam() == nr.getTeam() ){
                System.out.println("Found robot with ID: " + nr.getID());
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
                        addRobotToList(refineries, id, nr.getLocation());
                        break;
                    case VAPORATOR:
                        addRobotToList(vaporators, id, nr.getLocation());
                        break;
                    case DESIGN_SCHOOL:
                        addRobotToList(design_schools, id, nr.getLocation());
                        break;
                    case FULFILLMENT_CENTER:
                        addRobotToList(fulfillment_centers, id, nr.getLocation());
                        break;
                    case NET_GUN:
                        addRobotToList(net_guns, id, nr.getLocation());
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
            if (rc.getType() == RobotType.LANDSCAPER && nr.getType() == RobotType.FULFILLMENT_CENTER && nr.getTeam() == rc.getTeam()){
                addRobotToList(fulfillment_centers, nr.getID());
                if (!robotListContainsID(fulfillment_centers, nr.getID())){
                    RobotStatus rs = new RobotStatus(nr.getID());
                    rs.location = nr.getLocation();
                    fulfillment_centers.add(rs);
                }
            }
            if (nr.getType() == RobotType.DELIVERY_DRONE && rc.getTeam() != nr.getTeam() && rc.getLocation().isWithinDistanceSquared(nr.getLocation(), GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED)){
                RobotStatus rs = new RobotStatus(nr.getID());
                rs.location = nr.getLocation();
                enemy_drones.add(rs);
            }
        }
    }

    static void moveToLocationUsingBugPathing(MapLocation location) throws GameActionException{
        moveToLocationUsingBugPathing(location, rc.getType().canFly());
    }

    static void moveToLocationUsingBugPathing(MapLocation location, boolean avoid_net_guns) throws GameActionException{
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
        return bugPathPlan(goal, turn_left, rc.getType().canFly());
    }

    static boolean outOfEnemyNetGunRange(MapLocation location){
        RobotInfo[] enemy_robots_in_range = rc.senseNearbyRobots(location, GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED, rc.getTeam().opponent());
        for (RobotInfo ri : enemy_robots_in_range){
            if (ri.getType() == RobotType.HQ || ri.getType() == RobotType.NET_GUN){
                return false;
            }
        }
        return true;
    }

    static PathResult bugPathPlan(MapLocation goal, boolean turn_left, boolean avoid_net_guns) throws GameActionException {
        MapLocation current_location = rc.getLocation();
        Direction dir = current_location.directionTo(goal);
        HashSet<MapLocation> visited_plan = new HashSet<MapLocation>();

        boolean ignoreElevation = rc.getType().canFly();

        visited_plan.add(current_location);

        Direction first_dir = Direction.CENTER;

        int num_steps = 0;

        while(true){

            for (int i = 0; i != directions.length; i++){
                MapLocation destination = current_location.add(dir);
                if (onTheMap(destination) && (!rc.canSenseLocation(destination) || (!rc.isLocationOccupied(destination) &&
                    (ignoreElevation || (!rc.senseFlooding(destination) && Math.abs(rc.senseElevation(destination)-rc.senseElevation(current_location)) <= 3)) &&
                    !visited.contains(destination) && !visited_plan.contains(destination))) && (!avoid_net_guns || (outOfEnemyNetGunRange(destination) && (dir.getDeltaX()==0 || dir.getDeltaY() == 0)))){
                    current_location = destination;
                    visited_plan.add(current_location);
                    // rc.setIndicatorDot(current_location,255,0,0);
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
        if (rc.isReady() && rc.canMove(dir) && ((rc.canSenseLocation(rc.getLocation().add(dir)) && !rc.senseFlooding(rc.getLocation().add(dir))) || rc.getType().canFly())){
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

        message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH -1 - (remaining_bits-1) / 32] += (data_label >>> Math.max(0, -shift)) << Math.max(0, shift);
        remaining_bits -= data_label_size - Math.max(0, -shift);

        if (shift < 0) {
            message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH -1 - (remaining_bits-1) / 32] += (data_label << (32 - Math.max(0, -shift)) >>> (32 - Math.max(0,-shift))) << (32 - Math.max(0, -shift));
            remaining_bits -= Math.max(0, -shift);
        }

        shift = ((remaining_bits - 1) % 32 + 1 - data_size);
        message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH -1 - (remaining_bits-1) / 32] += (data >>> Math.max(0, -shift)) << Math.max(0, shift);

        remaining_bits -= data_size - Math.max(0, -shift);
        if (shift < 0){
            message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH -1 - (remaining_bits-1) / 32] += (data << (32 - Math.max(0, -shift)) >>> (32 - Math.max(0,-shift))) << (32 - Math.max(0, -shift));
            remaining_bits -= Math.max(0, -shift);            
        }

        return remaining_bits;
    }

    static void addPasswordAndHashToMessage(int[] message){
        message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH - 1] += blockchain_password << 22;
        String message_serialized = "";
        for (int i = 0; i != GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH; i++){
            message_serialized += Integer.toString(message[i]) + "_"; 
        }
        int hash = message_serialized.hashCode();
        message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH - 1] += hash >>> 10;
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
                                closest_water_to_enemy_HQ = closest_water_to_HQ;
                            }
                            return;
                        }
                        else{
                            possible_symmetries.remove(Symmetry.values()[i]);
                            if (possible_symmetries.size() == 1 && enemy_HQ_loc == null){
                                enemy_HQ_loc = symmetric_HQ_locs[possible_symmetries.get(0).ordinal()];
                                closest_water_to_enemy_HQ = closest_water_to_HQ;
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
                    addRobotToList(refineries, report.robot_id, report.location);
                    break;
                case VAPORATOR:
                    addRobotToList(vaporators, report.robot_id, report.location);
                    break;
                case DESIGN_SCHOOL:
                    addRobotToList(design_schools, report.robot_id, report.location);
                    break;
                case FULFILLMENT_CENTER:
                    addRobotToList(fulfillment_centers, report.robot_id, report.location);
                    break;
                case LANDSCAPER:  
                    addRobotToList(landscapers, report.robot_id);
                    break;
                case DELIVERY_DRONE: 
                    addRobotToList(drones, report.robot_id);
                    break;
                case NET_GUN:
                    addRobotToList(net_guns, report.robot_id, report.location);
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
            case FLOOD:
                if (HQ_loc != null && (closest_water_to_HQ == null ||
                    HQ_loc.distanceSquaredTo(report.location) < HQ_loc.distanceSquaredTo(closest_water_to_HQ))){
                    closest_water_to_HQ = report.location;
                }
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
        if (message.length < GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH)
            return false;
        if (blockchain_password_hashes.contains(message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH - 1]))
            return false;
        int pass = message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH - 1] >>> 22;
        if (pass != blockchain_password) 
            return false;
        int hash = message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH - 1] << 10 >>> 10;
        message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH - 1] = message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH - 1] >>> 22 << 22;
        String message_serialized = "";
        for (int i = 0; i != GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH; i++){
            message_serialized += Integer.toString(message[i]) + "_"; 
        }
        if (hash != message_serialized.hashCode()>>>10)
            return false;
        message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH - 1] += hash;
        return true; 
    }

    static void readMessage(int[] message) throws GameActionException {
        if (!checkPasswordAndHash(message)){
            return;
        }
        blockchain_password_hashes.add(message[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH - 1]);
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
            Transaction[] block = rc.getBlock(blockchain_read_index);
            for (Transaction msg : block){
                readMessage(msg.getMessage());
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

    static boolean addMissionInList(Mission mission, ArrayList<RobotStatus> robots){
        boolean found = false;
        for (RobotStatus rs : robots){
            for (int id : mission.robot_ids){
                if (rs.robot_id == id){
                    rs.missions.clear();
                    rs.missions.add(mission);     
                    found = true;                       
                }                                        
            }  
        } 
        return found;
    }

    static void addAllToMission(ArrayList<Mission> missions) {
        for (Mission mission : missions){
            if (addMissionInList(mission, miners)) continue;
            if (addMissionInList(mission, drones)) continue;
            if (addMissionInList(mission, landscapers)) continue;
            if (addMissionInList(mission, refineries)) continue;
            if (addMissionInList(mission, vaporators)) continue;
            if (addMissionInList(mission, design_schools)) continue;
            if (addMissionInList(mission, fulfillment_centers)) continue;
            if (addMissionInList(mission, net_guns)) continue;
        }
    }

    static void tryBlockchain() throws GameActionException {
        if (rc.getRoundNum() == 0)
            return;
        if (rc.getType() == RobotType.HQ){
            if (mission_queue.size() == 0){
                return;
            }

            int[] message = new int[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH];
            int remaining_bits = 32*GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH;
            int i = 0;

            Mission mission = new Mission();

            ArrayList<Mission> mission_queue_message = new ArrayList<Mission>();

            while(i < mission_queue.size()){
                mission = mission_queue.get(i);

                int mission_bits = getMissionBits(mission);

                if (mission_bits > remaining_bits){
                    addToMessage(message, remaining_bits, 0, Label.END_MESSAGE.ordinal(), 0);
                    addPasswordAndHashToMessage(message);

                    if (rc.canSubmitTransaction(message, 1)){
                        rc.submitTransaction(message, 1);
                        addAllToMission(mission_queue_message);
                        mission_queue_message.clear();
                    }
                    remaining_bits = 32*GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH;
                    message = new int[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH];
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
                mission_queue_message.add(mission);
            }

            addToMessage(message, remaining_bits, 0, Label.END_MESSAGE.ordinal(), 0);
            addPasswordAndHashToMessage(message);

            if (rc.canSubmitTransaction(message, 1)){
                rc.submitTransaction(message, 1);
                addAllToMission(mission_queue_message);
                mission_queue_message.clear();
            }
        }
        else{
            if (report_queue.size() == 0){
                return;
            }

            int[] message = new int[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH];
            int remaining_bits = 32*GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH;
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

                    remaining_bits = 32*GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH;
                    message = new int[GameConstants.BLOCKCHAIN_TRANSACTION_LENGTH];
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
                else if (report.report_type == ReportType.FLOOD){
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
