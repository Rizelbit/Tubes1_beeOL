package alternativeBots1;  // Punya Manu

import java.util.Arrays;
import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;

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
    public static void debug(Object... args) {
        System.out.println(Arrays.deepToString(args));
    }

    static int turnCount = 0;
    static int curSpawn = 0; // utk sementara pake curSpawn
    static Direction biasDir = null;        // arah sebar unik per robot (dari ID % 8)
    static MapLocation exploreTarget = null; // target eksplorasi saat ini
    static int exploreStuckCount = 0;       // counter stuck saat bergerak ke target

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
                switch (rc.getType()){
                    case SOLDIER: runSoldier(rc); break;
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc); break;
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
        // Tentukan tipe robot target berdasarkan giliran saat ini (belum di-increment).
        // curSpawn hanya naik kalau build benar-benar berhasil, supaya tiap tipe
        // tidak pernah terlewat karena tile kebetulan terhalang.
        int robotType = (curSpawn + 1) % 3; // 0=SOLDIER, 1=MOPPER, 2=SPLASHER
        UnitType typeToBuild = robotType == 0 ? UnitType.SOLDIER
                : robotType == 1 ? UnitType.MOPPER
                : UnitType.SPLASHER;

        debug("[Tower] round=", rc.getRoundNum(), "trying to build", typeToBuild,
                "paint=", rc.getPaint());

        // Coba semua 8 arah; berhenti di arah pertama yang berhasil.
        boolean built = false;
        for (Direction dir : directions) {
            MapLocation nextLoc = rc.getLocation().add(dir);
            if (rc.canBuildRobot(typeToBuild, nextLoc)) {
                rc.buildRobot(typeToBuild, nextLoc);
                curSpawn++; // increment hanya kalau build sukses
                debug("[Tower] BUILT", typeToBuild, "at", nextLoc,
                        "=> total spawned=", curSpawn);
                built = true;
                break;
            }
        }
        if (!built) {
            debug("[Tower] FAILED to build", typeToBuild,
                    "(all 8 dirs blocked) round=", rc.getRoundNum());
        }

        // Read incoming messages
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());
        }

        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        for (RobotInfo enemy : enemyRobots) {
            MapLocation enemyLoc = enemy.getLocation();
            if (rc.canAttack(enemyLoc)) {
                rc.attack(enemyLoc);
                debug("[Tower] attacked enemy at", enemyLoc);
                break;
            }
        }
    }


    /**
     * Run a single turn for a Soldier.
     * Spread-first: tiap robot menginisialisasi arah sebar dari ID-nya (anti-clumping),
     * mencari target dengan kepadatan petak kosong tertinggi, bergerak ke sana,
     * lalu mengecat petak yang diinjak. Ruin tetap prioritas tertinggi.
     */
    public static void runSoldier(RobotController rc) throws GameActionException {
        // Init arah sebar sekali pakai menggunakan ID unik robot,
        // lalu set exploreTarget ke tepi peta searah biasDir.
        MapLocation myLoc = rc.getLocation();
        if (biasDir == null) {
            biasDir = directions[rc.getID() % 8];
            exploreTarget = mapEdgeTarget(rc, myLoc, biasDir);
            exploreStuckCount = 0;
        }

        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();

        // --- Prioritas 1: Tangani Reruntuhan (Ruin) ---
        // MapInfo curRuin = null;
        // for (MapInfo tile : nearbyTiles) {
        //     if (tile.hasRuin()) curRuin = tile;
        // }
        // if (curRuin != null) {
        //     MapLocation targetLoc = curRuin.getMapLocation();
        //     Direction dir = rc.getLocation().directionTo(targetLoc);
        //     if (rc.canMove(dir))
        //         rc.move(dir);
        //     MapLocation shouldBeMarked = curRuin.getMapLocation().subtract(dir);
        //     if (rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY
        //             && rc.canMarkTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)) {
        //         rc.markTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
        //         System.out.println("Trying to build a tower at " + targetLoc);
        //     }
        //     for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)) {
        //         if (patternTile.getMark() != patternTile.getPaint()
        //                 && patternTile.getMark() != PaintType.EMPTY) {
        //             boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
        //             if (rc.canAttack(patternTile.getMapLocation()))
        //                 rc.attack(patternTile.getMapLocation(), useSecondaryColor);
        //         }
        //     }
        //     if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)) {
        //         rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
        //         rc.setTimelineMarker("Tower built", 0, 255, 0);
        //         System.out.println("Built a tower at " + targetLoc + "!");
        //     }
        //     // Cat petak saat ini jika aksi masih tersedia
        //     myLoc = rc.getLocation();
        //     if (rc.getActionCooldownTurns() < 10 && rc.getPaint() > 0) {
        //         MapInfo cur = rc.senseMapInfo(myLoc);
        //         if (!cur.getPaint().isAlly() && rc.canAttack(myLoc))
        //             rc.attack(myLoc);
        //     }
        //     rc.setIndicatorString("Ruin mode target=" + targetLoc);
        //     return;
        // }

        // --- Prioritas 2: Fase Sebar (Spread-First) ---
        // Target adalah titik tepi peta searah biasDir.
        // Refresh hanya kalau null atau sudah dekat target (≤ dist² 8).
        if (exploreTarget == null || myLoc.distanceSquaredTo(exploreTarget) <= 8) {
            biasDir = biasDir.rotateLeft(); // geser sektor sedikit supaya coverage lebih luas
            exploreTarget = mapEdgeTarget(rc, myLoc, biasDir);
            exploreStuckCount = 0;
        }

        // Gerak menuju target eksplorasi
        if (rc.getMovementCooldownTurns() < 10) {
            Direction moveDir = null;
            if (exploreTarget != null) {
                Direction preferred = myLoc.directionTo(exploreTarget);
                if (rc.canMove(preferred)) {
                    moveDir = preferred;
                    exploreStuckCount = 0;
                } else {
                    Direction left = preferred.rotateLeft();
                    Direction right = preferred.rotateRight();
                    if (rc.canMove(left)) { moveDir = left; exploreStuckCount = 0; }
                    else if (rc.canMove(right)) { moveDir = right; exploreStuckCount = 0; }
                    else {
                        exploreStuckCount++;
                        if (exploreStuckCount >= 3) {
                            exploreTarget = null;
                            exploreStuckCount = 0;
                        }
                    }
                }
            }
            // Fallback: coba arah bias dulu, baru acak
            if (moveDir == null) {
                if (rc.canMove(biasDir)) {
                    moveDir = biasDir;
                } else {
                    int start = rng.nextInt(directions.length);
                    for (int i = 0; i < directions.length; i++) {
                        Direction d = directions[(start + i) % directions.length];
                        if (rc.canMove(d)) { moveDir = d; break; }
                    }
                }
            }
            if (moveDir != null) rc.move(moveDir);
        }

        // Cat: utamakan petak yang baru diinjak, lalu non-ally terdekat
        myLoc = rc.getLocation();
        if (rc.getActionCooldownTurns() < 10 && rc.getPaint() > 0) {
            MapInfo cur = rc.senseMapInfo(myLoc);
            if (!cur.getPaint().isAlly() && rc.canAttack(myLoc)) {
                rc.attack(myLoc);
            } else {
                MapLocation attackTarget = findNearestNonAllyTile(rc);
                if (attackTarget != null && rc.canAttack(attackTarget))
                    rc.attack(attackTarget);
            }
        }

        rc.setIndicatorString("Spread bias=" + biasDir + " target=" + exploreTarget);
    }


    /**
     * Menghitung titik di tepi peta searah dir dari posisi from.
     * Digunakan sebagai target spread jarak jauh supaya soldier tidak loiter.
     */
    public static MapLocation mapEdgeTarget(RobotController rc, MapLocation from, Direction dir) {
        int far = Math.max(rc.getMapWidth(), rc.getMapHeight());
        int tx = Math.max(0, Math.min(rc.getMapWidth()  - 1, from.x + dir.dx * far));
        int ty = Math.max(0, Math.min(rc.getMapHeight() - 1, from.y + dir.dy * far));
        return new MapLocation(tx, ty);
    }

    /**
     * Mencari target eksplorasi terbaik untuk fase sebar.
     * Kandidat: petak non-ally dalam radius √20 (~4.47 petak).
     * Skor: kepadatan petak non-ally di sekitarnya + bonus jika searah biasDir.
     */
    public static MapLocation findSpreadTarget(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation bestTarget = null;
        int bestScore = Integer.MIN_VALUE;

        for (MapInfo tile : rc.senseNearbyMapInfos(myLoc, 20)) {
            if (tile.getPaint().isAlly()) continue;
            MapLocation loc = tile.getMapLocation();
            int score = 0;

            // Hitung kepadatan petak non-ally dalam radius² 4 di sekitar kandidat
            for (MapInfo neighbor : rc.senseNearbyMapInfos(loc, 4)) {
                if (!neighbor.getPaint().isAlly()) score += 1;
            }

            // Bonus jika kandidat searah dengan arah sebar robot ini
            Direction toTile = myLoc.directionTo(loc);
            if (toTile == biasDir) score += 8;
            else if (toTile == biasDir.rotateLeft() || toTile == biasDir.rotateRight()) score += 4;

            // Penalti jarak agar tidak terlalu jauh
            score -= myLoc.distanceSquaredTo(loc) / 4;

            if (score > bestScore) {
                bestScore = score;
                bestTarget = loc;
            }
        }
        return bestTarget;
    }

    /**
     * Returns the location of the nearest non-ally (empty or enemy-painted) tile within sensor range.
     */
    public static MapLocation findNearestNonAllyTile(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (MapInfo tile : rc.senseNearbyMapInfos()) {
            if (!tile.getPaint().isAlly()) {
                int dist = myLoc.distanceSquaredTo(tile.getMapLocation());
                if (dist < minDist) {
                    minDist = dist;// update nilai min
                    nearest = tile.getMapLocation();
                }
            }
        }
        return nearest;
    }

    /**
     * Finds the nearest ally building (paint tower) or Mopper to refill paint.
     */
    public static MapLocation findRefillTarget(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();
        MapLocation nearest = null;
        int minDist = Integer.MAX_VALUE;
        for (RobotInfo ally : rc.senseNearbyRobots(-1, rc.getTeam())) {
            UnitType type = ally.getType();
            if (type != UnitType.SOLDIER && type != UnitType.MOPPER && type != UnitType.SPLASHER) {
                int dist = myLoc.distanceSquaredTo(ally.getLocation());
                if (dist < minDist) {
                    minDist = dist;
                    nearest = ally.getLocation();
                }
            }
        }
        return nearest;
    }

    /**
     * Run a single turn for a Splasher.
     * Greedy Maximum Coverage & Siege: untuk setiap kandidat titik pusat serangan
     * dalam jarak 2 petak, hitung skor berdasarkan petak kosong (+1), petak musuh (+2),
     * dan menara musuh di area splash (+50). Serang titik dengan skor tertinggi.
     */
    public static void runSplasher(RobotController rc) throws GameActionException {
        MapLocation myLoc = rc.getLocation();

        // --- Fase Serangan ---
        // Syarat kelayakan: action cooldown < 10 dan cat >= 50.
        if (rc.getActionCooldownTurns() < 10 && rc.getPaint() >= 50) {
            MapLocation bestTarget = null;
            int maxScore = 0;
            // Kandidat: semua petak dalam jarak² ≤ 4 (maksimal 2 petak) dari posisi Splasher.
            for (MapInfo tile : rc.senseNearbyMapInfos(myLoc, 4)) {
                MapLocation loc = tile.getMapLocation();
                if (!rc.canAttack(loc)) continue;
                int score = 0;
                // Evaluasi setiap petak dalam radius √2 (dist² ≤ 2) dari titik pusat.
                for (MapInfo splash : rc.senseNearbyMapInfos(loc, 2)) {
                    PaintType paint = splash.getPaint();
                    if (paint == PaintType.EMPTY) {
                        score += 1;   // Petak kosong: +1
                    } else if (paint.isEnemy()) {
                        score += 2;   // Petak musuh: +2
                    }
                }
                // Menara musuh dalam area splash: +50
                for (RobotInfo robot : rc.senseNearbyRobots(loc, 2, rc.getTeam().opponent())) {
                    UnitType rType = robot.getType();
                    if (rType != UnitType.SOLDIER && rType != UnitType.MOPPER && rType != UnitType.SPLASHER) {
                        score += 50;
                    }
                }
                if (score > maxScore) {
                    maxScore = score;
                    bestTarget = loc;
                }
            }
            if (bestTarget != null) {
                rc.attack(bestTarget);
                rc.setIndicatorString("Splasher attacking, score=" + maxScore);
            }
        }

        // --- Fase Pergerakan ---
        // Bergerak independen dari action cooldown (movement cooldown terpisah).
        if (rc.getMovementCooldownTurns() < 10) {
            if (rc.getPaint() < 50) {
                // Cat kurang: dekati ally tower atau Mopper terdekat untuk isi ulang.
                MapLocation refill = findRefillTarget(rc);
                if (refill != null) {
                    Direction dir = myLoc.directionTo(refill);
                    if (rc.canMove(dir)) {
                        rc.move(dir);
                        rc.setIndicatorString("Splasher heading to refill");
                        return;
                    }
                }
            } else {
                // Cat cukup: maju ke klaster petak non-ally terdekat.
                MapLocation target = findNearestNonAllyTile(rc);
                if (target != null) {
                    Direction dir = myLoc.directionTo(target);
                    if (rc.canMove(dir)) {
                        rc.move(dir);
                        return;
                    }
                }
            }
            // Fallback: gerak acak.
            Direction dir = directions[rng.nextInt(directions.length)];
            if (rc.canMove(dir)) rc.move(dir);
        }
    }

    /**
     * Run a single turn for a Mopper.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runMopper(RobotController rc) throws GameActionException{
        debug("[Mopper] round=", rc.getRoundNum(), "loc=", rc.getLocation(),
                "paint=", rc.getPaint());
        // Move and attack randomly.
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);
        if (rc.canMove(dir)){
            rc.move(dir);
        }
        if (rc.canMopSwing(dir)){
            rc.mopSwing(dir);
            debug("[Mopper] MopSwing dir=", dir);
        }
        else if (rc.canAttack(nextLoc)){
            rc.attack(nextLoc);
            debug("[Mopper] attacked", nextLoc);
        }
        // We can also move our code into different methods or classes to better organize it!
        updateEnemyRobots(rc);
    }

    public static void updateEnemyRobots(RobotController rc) throws GameActionException{
        // Sensing methods can be passed in a radius of -1 to automatically
        // use the largest possible value.
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
}