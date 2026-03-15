package alternativeBots1;

import battlecode.common.*;

import java.util.Random;


/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;

    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    static final Random rng = new Random(6147);

    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    static int spawnCounter = 0;

    static MapLocation[] visitedTiles = new MapLocation[30];
    static int visitedIndex = 0;

    static MapLocation[] knownTowers = new MapLocation[30];
    static int knownTowerCount = 0;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        System.out.println("I'm alive");

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            turnCount += 1;  // We have now been alive for one more turn!

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                // The same run() function is called for every robot on your team, even if they are
                // different types. Here, we separate the control depending on the UnitType, so we can
                // use different strategies on different robots. If you wish, you are free to rewrite
                // this into a different control structure!
                rememberTowers(rc);

                switch (rc.getType()){
                    case SOLDIER: runSoldier(rc); break;
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc); break; // Consider upgrading examplefuncsplayer to use splashers!
                    default: runTower(rc); break;
                    }
                }
             catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You should
                // handle GameActionExceptions judiciously, in case unexpected events occur in the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }

    /**
     * Run a single turn for towers.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runTower(RobotController rc) throws GameActionException{
        if (rc.canUpgradeTower(rc.getLocation())) {
            rc.upgradeTower(rc.getLocation());
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemies) {
            if (rc.canAttack(enemy.location)) {
                rc.attack(enemy.location);
                break;
            }
        }

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type == UnitType.SOLDIER || ally.type == UnitType.MOPPER || ally.type == UnitType.SPLASHER) {
                if (ally.paintAmount < ally.type.paintCapacity / 3) {
                    int give = Math.min(rc.getPaint() / 4, ally.type.paintCapacity - ally.paintAmount);
                    if (give > 5 && rc.canTransferPaint(ally.location, give)) {
                        rc.transferPaint(ally.location, give);
                        break;
                    }
                }
            }
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        int enemyPaintCount = 0;
        for (MapInfo tile : nearbyTiles) {
            if (tile.getPaint().isEnemy()) {
                enemyPaintCount++;
            }
        }

        if (rc.getPaint() < 200) {
            // Read incoming messages
            Message[] messages = rc.readMessages(-1);
            for (Message m : messages) {
                System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());
            }
            // TODO: can we attack other bots?
            return;
        }

        UnitType toSpawn;
        if (enemyPaintCount > 8) {
            toSpawn = UnitType.MOPPER;
        } else if (rc.getRoundNum() < 100) {
            toSpawn = UnitType.SOLDIER;
        } else if (rc.getRoundNum() < 300) {
            int r = spawnCounter % 10;
            if (r < 4) toSpawn = UnitType.SPLASHER;
            else if (r < 9) toSpawn = UnitType.SOLDIER;
            else toSpawn = UnitType.MOPPER;
        } else {
            int r = spawnCounter % 10;
            if (r < 6) toSpawn = UnitType.SPLASHER;
            else if (r < 8) toSpawn = UnitType.SOLDIER;
            else toSpawn = UnitType.MOPPER;
        }

        Direction bestSpawnDir = null;
        int bestSpawnScore = Integer.MIN_VALUE;
        for (Direction d : directions) {
            MapLocation spawnLoc = rc.getLocation().add(d);
            if (!rc.canBuildRobot(toSpawn, spawnLoc)) continue;

            int spawnScore = 0;
            for (MapInfo t : nearbyTiles) {
                if (spawnLoc.distanceSquaredTo(t.getMapLocation()) <= 4) {
                    if (t.getPaint().isEnemy()) spawnScore++;
                }
            }
            RobotInfo[] allyNearSpawn = rc.senseNearbyRobots(spawnLoc, 4, rc.getTeam());
            spawnScore -= allyNearSpawn.length;

            if (spawnScore > bestSpawnScore) {
                bestSpawnScore = spawnScore;
                bestSpawnDir = d;
            }
        }
        if (bestSpawnDir != null) {
            rc.buildRobot(toSpawn, rc.getLocation().add(bestSpawnDir));
            spawnCounter++;
        }

        // Read incoming messages
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());
        }

        // TODO: can we attack other bots?
    }


    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runSoldier(RobotController rc) throws GameActionException{
        MapLocation myLoc = rc.getLocation();
        int curPaint = rc.getPaint();
        int maxPaint = rc.getType().paintCapacity;

        // Sense information about all visible nearby tiles.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        // Search for a nearby ruin to complete.
        MapLocation nearestRuin = null;
        int nearestRuinDist = Integer.MAX_VALUE;
        for (MapInfo tile : nearbyTiles){
            if (tile.hasRuin()){
                MapLocation ruinLoc = tile.getMapLocation();
                RobotInfo robotAtRuin = rc.senseRobotAtLocation(ruinLoc);
                if (robotAtRuin == null) {
                    int dist = myLoc.distanceSquaredTo(ruinLoc);
                    if (dist <= 4 && dist < nearestRuinDist) {
                        nearestRuinDist = dist;
                        nearestRuin = ruinLoc;
                    }
                }
            }
        }

        MapLocation nearestTower = findNearestKnownTower(rc);
        double paintRatio = (double) curPaint / maxPaint;
        if (paintRatio < 0.30 && nearestTower != null) {
            if (myLoc.distanceSquaredTo(nearestTower) <= 2) {
                int need = maxPaint - curPaint;
                if (rc.canTransferPaint(nearestTower, -need)) {
                    rc.transferPaint(nearestTower, -need);
                    curPaint = rc.getPaint();
                    paintRatio = (double) curPaint / maxPaint;
                }
            } else if (rc.isMovementReady()) {
                navigateToward(rc, nearestTower);
                paintCurrentTile(rc);
                return;
            }
        }

        if (nearestRuin != null) {
            UnitType tt = chooseTowerType(rc);

            if (nearestRuinDist <= 2) {
                if (rc.canMarkTowerPattern(tt, nearestRuin)) {
                    rc.markTowerPattern(tt, nearestRuin);
                }
                if (rc.canCompleteTowerPattern(tt, nearestRuin)) {
                    rc.completeTowerPattern(tt, nearestRuin);
                    nearestRuin = null;
                } else if (rc.isActionReady()) {
                    // Fill in any spots in the pattern with the appropriate paint.
                    for (MapInfo pt : rc.senseNearbyMapInfos(nearestRuin, 8)) {
                        if (pt.getMark() != pt.getPaint() && pt.getMark() != PaintType.EMPTY) {
                            boolean sec = pt.getMark() == PaintType.ALLY_SECONDARY;
                            if (rc.canAttack(pt.getMapLocation())) {
                                rc.attack(pt.getMapLocation(), sec);
                                break;
                            }
                        }
                    }
                    // Complete the ruin if we can.
                    if (rc.canCompleteTowerPattern(tt, nearestRuin)) {
                        rc.completeTowerPattern(tt, nearestRuin);
                        nearestRuin = null;
                    }
                    return;
                }
            } else {
                if (rc.isMovementReady()) {
                    navigateToward(rc, nearestRuin);
                    paintCurrentTile(rc);
                    return;
                }
            }
        }

        // Try to paint beneath us as we walk to avoid paint penalties.
        // Avoiding wasting paint by re-painting our own tiles.
        if (rc.isActionReady()) {
            MapInfo curTile = rc.senseMapInfo(rc.getLocation());
            if (!curTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
                rc.attack(rc.getLocation());
            } else {
                MapLocation bestPaint = null;
                int bestPaintScore = 0;
                for (MapInfo tile : nearbyTiles) {
                    if (!rc.canAttack(tile.getMapLocation())) continue;
                    MapLocation tileLoc = tile.getMapLocation();
                    PaintType tp = tile.getPaint();

                    int s = 0;
                    if (tp.isEnemy()) {
                        boolean nearAlly = false;
                        for (Direction d : directions) {
                            MapLocation adj = tileLoc.add(d);
                            if (rc.canSenseLocation(adj) && rc.senseMapInfo(adj).getPaint().isAlly()) {
                                nearAlly = true;
                                break;
                            }
                        }
                        s = nearAlly ? 5 : 2;
                    } else if (tp == PaintType.EMPTY && tile.isPassable() && !tile.hasRuin()) {
                        s = 1;
                    }

                    if (s > bestPaintScore) {
                        bestPaintScore = s;
                        bestPaint = tileLoc;
                    }
                }
                if (bestPaint != null) rc.attack(bestPaint);
            }
        }

        // Move and attack randomly if no objective.
        if (rc.isMovementReady()) {
            Direction bestDir = null;
            int bestScore = Integer.MIN_VALUE;

            for (Direction d : directions) {
                if (!rc.canMove(d)) continue;
                MapLocation dest = myLoc.add(d);
                MapInfo destInfo = rc.senseMapInfo(dest);
                PaintType destPaint = destInfo.getPaint();

                int traversalCost = paintCostOf(destPaint);
                int newTiles = countUnpaintedAround(rc, dest);

                int score = newTiles * 12;
                score -= traversalCost * 15;

                if (paintRatio < 0.4) score -= traversalCost * 10;
                if (paintRatio < 0.2 && traversalCost > 0) score -= 30;

                RobotInfo[] allyNear = rc.senseNearbyRobots(dest, 2, rc.getTeam());
                score -= 8 * allyNear.length;

                score -= visitedPenalty(dest);
                score += rng.nextInt(3);

                if (score > bestScore) {
                    bestScore = score;
                    bestDir = d;
                }
            }

            if (bestDir != null) {
                rc.move(bestDir);
                recordVisit(rc.getLocation());
            } else {
                Direction randomDir = directions[rng.nextInt(directions.length)];
                if (rc.canMove(randomDir)) {
                    rc.move(randomDir);
                    recordVisit(rc.getLocation());
                }
            }
        }
    }


    /**
     * Run a single turn for a Mopper.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runMopper(RobotController rc) throws GameActionException{
        MapLocation myLoc = rc.getLocation();
        int curPaint = rc.getPaint();
        int maxPaint = 100;

        if (curPaint < 20) {
            MapLocation tower = findNearestKnownTower(rc);
            if (tower != null) {
                if (myLoc.distanceSquaredTo(tower) <= 2) {
                    int need = maxPaint - curPaint;
                    if (rc.canTransferPaint(tower, -need)) {
                        rc.transferPaint(tower, -need);
                    }
                } else {
                    navigateToward(rc, tower);
                    return;
                }
            }
        }

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type == UnitType.SOLDIER && ally.paintAmount < 100) {
                if (myLoc.distanceSquaredTo(ally.location) <= 2) {
                    int transfer = Math.min(curPaint - 10, 200 - ally.paintAmount);
                    if (transfer > 0 && rc.canTransferPaint(ally.location, transfer)) {
                        rc.transferPaint(ally.location, transfer);
                        curPaint -= transfer;
                        break;
                    }
                }
            }
        }

        // Move and attack randomly.
        RobotInfo[] enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        boolean didSwing = false;
        for (Direction d : directions) {
            if (rc.canMopSwing(d)) {
                int enemiesInSwing = countEnemiesInSwingDir(rc, d, enemies);
                if (enemiesInSwing >= 2) {
                    rc.mopSwing(d);
                    System.out.println("Mop Swing! Booyah!");
                    didSwing = true;
                    break;
                }
            }
        }

        if (rc.isMovementReady()) {
            Direction bestDir = null;
            int bestScore = Integer.MIN_VALUE;

            for (Direction d : directions) {
                if (!rc.canMove(d)) continue;
                MapLocation dest = myLoc.add(d);
                MapInfo destInfo = rc.senseMapInfo(dest);
                PaintType destPaint = destInfo.getPaint();

                int score = 0;
                int traversalCost = paintCostOf(destPaint);

                score -= traversalCost * 8;
                if (destPaint.isEnemy()) score += 20;
                if (destPaint.isAlly()) score -= 15;

                for (RobotInfo enemy : enemies) {
                    if (dest.distanceSquaredTo(enemy.location) <= 4) {
                        score += 25;
                    }
                }

                MapLocation further = dest.add(d);
                if (rc.canSenseLocation(further)) {
                    if (rc.senseMapInfo(further).getPaint().isEnemy()) score += 10;
                }

                score -= visitedPenalty(dest);
                score += rng.nextInt(3);

                if (score > bestScore) {
                    bestScore = score;
                    bestDir = d;
                }
            }

            if (bestDir != null) {
                rc.move(bestDir);
                recordVisit(rc.getLocation());
            } else {
                Direction randomDir = directions[rng.nextInt(directions.length)];
                if (rc.canMove(randomDir)) {
                    rc.move(randomDir);
                    recordVisit(rc.getLocation());
                }
            }
        }

        myLoc = rc.getLocation();

        if (!didSwing) {
            enemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
            for (Direction d : directions) {
                if (rc.canMopSwing(d)) {
                    int cnt = countEnemiesInSwingDir(rc, d, enemies);
                    if (cnt >= 1) {
                        rc.mopSwing(d);
                        System.out.println("Mop Swing! Booyah!");
                        didSwing = true;
                        break;
                    }
                }
            }
        }

        if (!didSwing) {
            for (Direction d : directions) {
                MapLocation target = myLoc.add(d);
                if (rc.canSenseLocation(target)) {
                    MapInfo targetInfo = rc.senseMapInfo(target);
                    if (targetInfo.getPaint().isEnemy() && rc.canAttack(target)) {
                        rc.attack(target);
                        break;
                    }
                }
            }
        }

        // We can also move our code into different methods or classes to better organize it!
        updateEnemyRobots(rc);
    }

    public static void runSplasher(RobotController rc) throws GameActionException{
        MapLocation myLoc = rc.getLocation();
        int curPaint = rc.getPaint();
        int maxPaint = rc.getType().paintCapacity;

        double paintRatio = (double) curPaint / maxPaint;
        if (paintRatio < 0.25) {
            MapLocation tower = findNearestKnownTower(rc);
            if (tower != null) {
                if (myLoc.distanceSquaredTo(tower) <= 2) {
                    int need = maxPaint - curPaint;
                    if (rc.canTransferPaint(tower, -need)) {
                        rc.transferPaint(tower, -need);
                        curPaint = rc.getPaint();
                        paintRatio = (double) curPaint / maxPaint;
                    }
                } else if (rc.isMovementReady()) {
                    navigateToward(rc, tower);
                    return;
                }
            }
        }

        if (rc.isMovementReady()) {
            Direction bestDir = null;
            int bestMoveScore = Integer.MIN_VALUE;

            for (Direction d : directions) {
                if (!rc.canMove(d)) continue;
                MapLocation dest = myLoc.add(d);
                MapInfo destInfo = rc.senseMapInfo(dest);
                PaintType destPaint = destInfo.getPaint();

                int traversalCost = paintCostOf(destPaint);
                int score = 0;
                score -= traversalCost * 12;

                int enemyPaintAround = countEnemyPaintAround(rc, dest);
                score += enemyPaintAround * 15;

                if (destPaint.isEnemy()) score += 10;
                if (destPaint == PaintType.EMPTY) score += 5;

                if (paintRatio < 0.3) score -= traversalCost * 10;

                score -= visitedPenalty(dest);
                score += rng.nextInt(3);

                if (score > bestMoveScore) {
                    bestMoveScore = score;
                    bestDir = d;
                }
            }

            if (bestDir != null) {
                rc.move(bestDir);
                recordVisit(rc.getLocation());
            } else {
                Direction randomDir = directions[rng.nextInt(directions.length)];
                if (rc.canMove(randomDir)) {
                    rc.move(randomDir);
                    recordVisit(rc.getLocation());
                }
            }
        }

        if (rc.isActionReady()) {
            myLoc = rc.getLocation();
            MapInfo[] allNearby = rc.senseNearbyMapInfos();
            MapLocation bestSplash = null;
            int bestSplashScore = 0;

            MapInfo[] attackCandidates = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
            for (MapInfo candidate : attackCandidates) {
                MapLocation loc = candidate.getMapLocation();
                if (!rc.canAttack(loc)) continue;

                int enemyPaint = 0;
                int empty = 0;
                int allyPaint = 0;

                for (MapInfo t : allNearby) {
                    if (loc.distanceSquaredTo(t.getMapLocation()) <= 4) {
                        PaintType p = t.getPaint();
                        if (p.isEnemy()) enemyPaint++;
                        else if (p == PaintType.EMPTY) empty++;
                        else if (p.isAlly()) allyPaint++;
                    }
                }

                int score = 4 * enemyPaint + 2 * empty - 2 * allyPaint;
                if (score > bestSplashScore) {
                    bestSplashScore = score;
                    bestSplash = loc;
                }
            }

            if (bestSplash != null) {
                rc.attack(bestSplash);
            }
        }
    }

    public static void updateEnemyRobots(RobotController rc) throws GameActionException{
        // Sensing methods can be passed in a radius of -1 to automatically use the largest possible value.
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0){
            rc.setIndicatorString("There are nearby enemy robots! Scary!");
            // Save an array of locations with enemy robots in them for possible future use.
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++){
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
            // Occasionally try to tell nearby allies how many enemy robots we see.
            if (rc.getRoundNum() % 20 == 0){
                for (RobotInfo ally : allyRobots){
                    if (rc.canSendMessage(ally.location, enemyRobots.length)){
                        rc.sendMessage(ally.location, enemyRobots.length);
                    }
                }
            }
        }
    }

    static UnitType chooseTowerType(RobotController rc) {
        int n = rc.getNumberTowers();
        if (n < 3) return UnitType.LEVEL_ONE_PAINT_TOWER;
        if (n % 3 == 0) return UnitType.LEVEL_ONE_MONEY_TOWER;
        return UnitType.LEVEL_ONE_PAINT_TOWER;
    }

    static int paintCostOf(PaintType p) {
        if (p.isAlly()) return 0;
        if (p.isEnemy()) return 2;
        return 1;
    }

    static int countEnemyPaintAround(RobotController rc, MapLocation loc) throws GameActionException {
        int count = 0;
        for (Direction d : directions) {
            MapLocation adj = loc.add(d);
            if (rc.canSenseLocation(adj)) {
                MapInfo info = rc.senseMapInfo(adj);
                if (info.getPaint().isEnemy()) {
                    count++;
                }
            }
        }
        return count;
    }

    static int countUnpaintedAround(RobotController rc, MapLocation loc) throws GameActionException {
        int count = 0;
        for (Direction d : directions) {
            MapLocation adj = loc.add(d);
            if (rc.canSenseLocation(adj)) {
                MapInfo info = rc.senseMapInfo(adj);
                if (info.isPassable() && !info.getPaint().isAlly()) {
                    count++;
                }
            }
        }
        return count;
    }

    static void recordVisit(MapLocation loc) {
        visitedTiles[visitedIndex] = loc;
        visitedIndex = (visitedIndex + 1) % visitedTiles.length;
    }

    static int visitedPenalty(MapLocation loc) {
        for (int i = 0; i < visitedTiles.length; i++) {
            if (visitedTiles[i] != null && visitedTiles[i].equals(loc)) {
                return 100;
            }
        }
        return 0;
    }

    static void rememberTowers(RobotController rc) throws GameActionException {
        for (int i = 0; i < knownTowerCount; i++) {
            if (rc.canSenseLocation(knownTowers[i])) {
                RobotInfo robotThere = rc.senseRobotAtLocation(knownTowers[i]);
                if (robotThere == null || robotThere.team != rc.getTeam()
                        || robotThere.type == UnitType.SOLDIER
                        || robotThere.type == UnitType.MOPPER
                        || robotThere.type == UnitType.SPLASHER) {
                    knownTowers[i] = knownTowers[knownTowerCount - 1];
                    knownTowerCount--;
                    i--;
                }
            }
        }

        RobotInfo[] allies = rc.senseNearbyRobots(-1, rc.getTeam());
        for (RobotInfo ally : allies) {
            if (ally.type != UnitType.SOLDIER && ally.type != UnitType.MOPPER && ally.type != UnitType.SPLASHER) {
                boolean known = false;
                for (int i = 0; i < knownTowerCount; i++) {
                    if (knownTowers[i].equals(ally.location)) {
                        known = true;
                        break;
                    }
                }
                if (!known && knownTowerCount < knownTowers.length) {
                    knownTowers[knownTowerCount] = ally.location;
                    knownTowerCount++;
                }
            }
        }
    }

    static MapLocation findNearestKnownTower(RobotController rc) {
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (int i = 0; i < knownTowerCount; i++) {
            int dist = rc.getLocation().distanceSquaredTo(knownTowers[i]);
            if (dist < minDist) {
                minDist = dist;
                nearest = knownTowers[i];
            }
        }
        return nearest;
    }

    static void navigateToward(RobotController rc, MapLocation target) throws GameActionException {
        Direction bestDir = null;
        int bestScore = Integer.MAX_VALUE; // minimize

        for (Direction d : directions) {
            if (!rc.canMove(d)) continue;
            MapLocation next = rc.getLocation().add(d);
            int dist = next.distanceSquaredTo(target);
            int cost = 0;
            if (rc.canSenseLocation(next)) {
                cost = paintCostOf(rc.senseMapInfo(next).getPaint());
            }
            int score = dist + cost * 15 + visitedPenalty(next);
            if (score < bestScore) {
                bestScore = score;
                bestDir = d;
            }
        }

        if (bestDir != null) {
            rc.move(bestDir);
            recordVisit(rc.getLocation());
        }
    }

    // Try to paint beneath us as we walk to avoid paint penalties.
    // Avoiding wasting paint by re-painting our own tiles.
    static void paintCurrentTile(RobotController rc) throws GameActionException {
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())) {
            rc.attack(rc.getLocation());
        }
    }

    static int countEnemiesInSwingDir(RobotController rc, Direction dir, RobotInfo[] enemies) {
        MapLocation myLoc = rc.getLocation();
        MapLocation front = myLoc.add(dir);
        MapLocation frontLeft = myLoc.add(dir.rotateLeft());
        MapLocation frontRight = myLoc.add(dir.rotateRight());

        int count = 0;
        for (RobotInfo enemy : enemies) {
            MapLocation eLoc = enemy.location;
            if (eLoc.equals(front) || eLoc.equals(frontLeft) || eLoc.equals(frontRight)) {
                count++;
            }
        }
        return count;
    }
}
